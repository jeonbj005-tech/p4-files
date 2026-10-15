package com.polestar4.drivefilemanager

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class DriveApi(private val ctx: Context) {
    companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        const val VEHICLE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val SCOPE_VERSION = 3
    }
    data class DriveFile(val id:String,val name:String,val mime:String,val size:Long,val modified:Long=0L) {
        val folder get() = mime == "application/vnd.google-apps.folder"
    }
    private val prefs=ctx.getSharedPreferences("drive_v2",0)
    val connected get() = prefs.getInt("scope_version",0)==SCOPE_VERSION &&
        ((prefs.contains("access") && prefs.getLong("expiry",0)>System.currentTimeMillis()+60000) || prefs.contains("refresh"))
    val vehicleMode get() = prefs.getString("auth_mode","")=="VEHICLE_DEVICE_CODE"
    fun acceptAccessToken(value:String){prefs.edit().putString("access",value).putLong("expiry",System.currentTimeMillis()+50*60*1000).putInt("scope_version",SCOPE_VERSION).putString("granted_scope",DRIVE_SCOPE).putString("auth_mode","PLAY_SERVICES").remove("refresh").commit()}
    fun acceptVehicleTokens(access:String,refresh:String,expiresIn:Long,scope:String){prefs.edit().putString("access",access).putString("refresh",refresh).putLong("expiry",System.currentTimeMillis()+expiresIn*1000).putInt("scope_version",SCOPE_VERSION).putString("granted_scope",scope).putString("auth_mode","VEHICLE_DEVICE_CODE").commit()}
    private fun connection(url:String, method:String="GET")=(URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod=method;connectTimeout=20000;readTimeout=60000
    }
    private fun response(c:HttpURLConnection):JSONObject {
        val code=c.responseCode
        val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        val j=try{JSONObject(body)}catch(e:Exception){JSONObject()}
        if(code !in 200..299 && !((c.url.toString().contains("oauth2.googleapis.com/token") || c.url.toString().contains("www.googleapis.com/oauth2/v4/token")) && j.has("error"))){
            val error=j.optString("error");val detail=j.optString("error_description",j.optString("message","HTTP $code"));val failure=when{code==401->"TOKEN_ERROR";code==403->"DRIVE_PERMISSION_DENIED";error=="access_denied"->"OAUTH_ACCESS_DENIED";else->"HTTP_ERROR"};AuthDiagnostics.event("DRIVE_API_REQUEST","FAILED",failure,"http=$code");throw AuthFailure(failure,detail)
        }
        return j
    }
    private fun check(j:JSONObject):JSONObject {
        if(j.has("error")){val error=j.optString("error");val e=j.optJSONObject("error");val message=e?.optString("message") ?: j.optString("error_description",j.optString("error"));throw AuthFailure(if(error=="invalid_client")"OAUTH_CLIENT_NOT_FOUND" else "TOKEN_ERROR",message)}
        return j
    }
    @Synchronized private fun token():String {
        if(System.currentTimeMillis()>prefs.getLong("expiry",0)-60000){
            if(vehicleMode && prefs.contains("refresh")) refreshVehicleToken()
            else throw AuthFailure("TOKEN_MISSING","Google Drive에 다시 로그인하세요.")
        }
        return prefs.getString("access",null)?:throw AuthFailure("TOKEN_MISSING","Google Drive에 로그인하세요.")
    }
    private fun refreshVehicleToken(){
        val refresh=prefs.getString("refresh",null)?:throw AuthFailure("TOKEN_MISSING","차량 인증을 다시 진행하세요.")
        val form=linkedMapOf("client_id" to BuildConfig.TV_OAUTH_CLIENT_ID,"client_secret" to BuildConfig.TV_OAUTH_CLIENT_SECRET,"refresh_token" to refresh,"grant_type" to "refresh_token")
        AuthDiagnostics.event("TOKEN_EXCHANGE","START","VEHICLE_REFRESH")
        val j=DeviceCodeAuth.postTokenForm(form)
        val access=j.optString("access_token")
        if(access.isBlank())throw AuthFailure("TOKEN_ERROR",j.optString("error_description",j.optString("error","토큰 갱신 실패")))
        prefs.edit().putString("access",access).putLong("expiry",System.currentTimeMillis()+j.optLong("expires_in",3600)*1000).commit()
        AuthDiagnostics.event("TOKEN_EXCHANGE","SUCCESS","VEHICLE_REFRESH")
    }
    fun logout(){prefs.edit().clear().commit()}
    private fun request(url:String,method:String="GET")=connection(url,method).apply{setRequestProperty("Authorization","Bearer ${token()}");AuthDiagnostics.event("DRIVE_API_REQUEST","START",method)}
    private fun driveFile(x:JSONObject):DriveFile{val modified=try{Instant.parse(x.optString("modifiedTime")).toEpochMilli()}catch(_:Exception){0L};return DriveFile(x.getString("id"),x.getString("name"),x.getString("mimeType"),x.optLong("size"),modified)}
    fun listFolder(parent:String):List<DriveFile>{
        val result=mutableListOf<DriveFile>();var page=""
        do {val q="'${parent.replace("'","\\'")}' in parents and trashed=false"
            val c=request("https://www.googleapis.com/drive/v3/files?q=${Uri.encode(q)}&pageSize=100&orderBy=folder,name&fields=nextPageToken,files(id,name,mimeType,size,modifiedTime)&pageToken=${Uri.encode(page)}")
            val j=try{check(response(c))}finally{c.disconnect()};val a=j.optJSONArray("files")?:JSONArray()
            for(i in 0 until a.length())result.add(driveFile(a.getJSONObject(i)))
            page=j.optString("nextPageToken")
        }while(page.isNotEmpty());return result
    }
    fun createFolder(name:String,parent:String):String {
        val c=request("https://www.googleapis.com/drive/v3/files","POST")
        try{c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("name",name).put("mimeType","application/vnd.google-apps.folder").put("parents",JSONArray().put(parent)).toString().toByteArray())};return check(response(c)).getString("id")}finally{c.disconnect()}
    }
    fun search(query:String,mimePrefix:String?=null):List<DriveFile>{
        val escaped=query.replace("'","\\'");var q="name contains '$escaped' and trashed=false";if(!mimePrefix.isNullOrBlank())q+=" and mimeType contains '${mimePrefix.replace("'","\\'")}'"
        val c=request("https://www.googleapis.com/drive/v3/files?q=${Uri.encode(q)}&pageSize=100&orderBy=modifiedTime%20desc&fields=files(id,name,mimeType,size,modifiedTime)")
        val j=try{check(response(c))}finally{c.disconnect()};val a=j.optJSONArray("files")?:JSONArray();return (0 until a.length()).map{driveFile(a.getJSONObject(it))}
    }
    fun getFile(id:String):DriveFile{val c=request("https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}?fields=id,name,mimeType,size,modifiedTime");return try{driveFile(check(response(c)))}finally{c.disconnect()}}
    fun rename(id:String,name:String){patch(id,JSONObject().put("name",name))}
    fun trash(id:String){patch(id,JSONObject().put("trashed",true))}
    fun move(id:String,fromParent:String,toParent:String){patch(id,JSONObject(),"?addParents=${Uri.encode(toParent)}&removeParents=${Uri.encode(fromParent)}")}
    fun copyFile(id:String,name:String,parent:String):String{
        val c=request("https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}/copy","POST")
        try{c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("name",name).put("parents",JSONArray().put(parent)).toString().toByteArray())};return check(response(c)).getString("id")}finally{c.disconnect()}
    }
    private fun patch(id:String,body:JSONObject,suffix:String=""){
        val c=request("https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}$suffix","PATCH")
        try{c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())};check(response(c))}finally{c.disconnect()}
    }
    fun upload(uri:Uri,name:String,mime:String,parent:String,progress:(Long)->Unit):String {
        var spool:File?=null
        var length=ctx.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.SIZE),null,null,null)?.use{if(it.moveToFirst()&&!it.isNull(0))it.getLong(0) else -1L}?:-1L
        if(length<0){
            val temp=File.createTempFile("upload-",".part",ctx.cacheDir)
            try{(ctx.contentResolver.openInputStream(uri)?:throw IOException("파일을 열 수 없습니다.")).use{input->temp.outputStream().use{input.copyTo(it)}}}catch(e:Exception){temp.delete();throw e}
            spool=temp;length=temp.length()
        }
        try {
        val init=request("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable","POST")
        val location=try{init.doOutput=true;init.setRequestProperty("Content-Type","application/json");init.setRequestProperty("X-Upload-Content-Type",mime)
            init.setRequestProperty("X-Upload-Content-Length",length.toString())
            init.outputStream.use{it.write(JSONObject().put("name",name).put("parents",JSONArray().put(parent)).toString().toByteArray())}
            if(init.responseCode !in 200..299)check(response(init));init.getHeaderField("Location")?:throw IOException("업로드 세션 생성 실패")
        }finally{init.disconnect()}
        val c=request(location,"PUT")
        try{c.doOutput=true;c.setFixedLengthStreamingMode(length);c.setRequestProperty("Content-Type",mime)
            (spool?.inputStream()?:ctx.contentResolver.openInputStream(uri))?.use{input->c.outputStream.use{output->copy(input,output,progress)}}?:throw IOException("파일을 열 수 없습니다.")
            return check(response(c)).getString("id")
        }finally{c.disconnect()}
        }finally{spool?.delete()}
    }
    fun uploadResumable(uri:Uri,name:String,mime:String,parent:String,length:Long,savedSession:String,onSession:(String)->Unit,progress:(Long)->Unit){
        var session=savedSession;var offset=0L
        if(session.isNotBlank()){
            val probe=request(session,"PUT")
            try{probe.doOutput=true;probe.setFixedLengthStreamingMode(0);probe.setRequestProperty("Content-Range","bytes */$length");val code=probe.responseCode
                if(code==308)offset=probe.getHeaderField("Range")?.substringAfterLast('-')?.toLongOrNull()?.plus(1)?:0L
                else if(code in 200..299){progress(length);return}
                else if(code==404){session="";offset=0}else check(response(probe))
            }finally{probe.disconnect()}
        }
        if(session.isBlank()){
            val init=request("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable","POST")
            session=try{init.doOutput=true;init.setRequestProperty("Content-Type","application/json");init.setRequestProperty("X-Upload-Content-Type",mime);init.setRequestProperty("X-Upload-Content-Length",length.toString());init.outputStream.use{it.write(JSONObject().put("name",name).put("parents",JSONArray().put(parent)).toString().toByteArray())};if(init.responseCode !in 200..299)check(response(init));init.getHeaderField("Location")?:throw IOException("업로드 세션 생성 실패")}finally{init.disconnect()}
            onSession(session)
        }
        val input=ctx.contentResolver.openInputStream(uri)?:throw IOException("파일을 열 수 없습니다.")
        input.use{stream->var skipped=0L;while(skipped<offset){val n=stream.skip(offset-skipped);if(n<=0){if(stream.read()<0)throw EOFException("업로드 재개 위치를 찾을 수 없습니다");skipped++}else skipped+=n};progress(offset)
            val chunk=ByteArray(1024*1024);while(offset<length){val wanted=minOf(chunk.size.toLong(),length-offset).toInt();var count=0;while(count<wanted){val n=stream.read(chunk,count,wanted-count);if(n<0)throw EOFException("예상보다 파일이 짧습니다");count+=n}
                val end=offset+count-1;val c=request(session,"PUT")
                try{c.doOutput=true;c.setFixedLengthStreamingMode(count);c.setRequestProperty("Content-Type",mime);c.setRequestProperty("Content-Range","bytes $offset-$end/$length");c.outputStream.use{it.write(chunk,0,count)};val code=c.responseCode
                    if(code==308){offset=c.getHeaderField("Range")?.substringAfterLast('-')?.toLongOrNull()?.plus(1)?:end+1;progress(offset)}
                    else if(code in 200..299){progress(length);return}
                    else check(response(c))
                }finally{c.disconnect()}
            }
        }
    }
    fun download(id:String,output:OutputStream,progress:(Long)->Unit){val c=request("https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}?alt=media");try{if(c.responseCode !in 200..299){check(response(c));throw IOException("다운로드 실패")};c.inputStream.use{copy(it,output,progress)}}finally{c.disconnect()}}
    fun downloadResumable(id:String,file:File,progress:(Long)->Unit){var offset=if(file.exists())file.length() else 0L;val c=request("https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}?alt=media");if(offset>0)c.setRequestProperty("Range","bytes=$offset-");try{val code=c.responseCode;if(code !in 200..299){check(response(c));throw IOException("다운로드 실패")};val append=code==206&&offset>0;if(!append)offset=0;FileOutputStream(file,append).use{out->c.inputStream.use{input->val buffer=ByteArray(256*1024);var total=offset;while(true){val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);total+=n;progress(total)}}}}finally{c.disconnect()}}
    private fun copy(input:InputStream,output:OutputStream,progress:(Long)->Unit){val buffer=ByteArray(256*1024);var total=0L;var last=0L;while(true){val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n);total+=n;if(System.currentTimeMillis()-last>300){progress(total);last=System.currentTimeMillis()}};progress(total)}
}
