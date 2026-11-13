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
        private const val UPLOAD_CHUNK_SIZE = 8 * 1024 * 1024
    }
    data class DriveFile(val id:String,val name:String,val mime:String,val size:Long,val modified:Long=0L) {
        val folder get() = mime == "application/vnd.google-apps.folder"
    }
    private val prefs=ctx.getSharedPreferences("drive_v2",0)
    val connected get() = prefs.getInt("scope_version",0)==SCOPE_VERSION &&
        if(vehicleMode) !prefs.getString("refresh",null).isNullOrBlank()
        else prefs.contains("access") && prefs.getLong("expiry",0)>System.currentTimeMillis()+60000
    val hasCachedAccessToken get() = !prefs.getString("access",null).isNullOrBlank()
    val vehicleMode get() = prefs.getString("auth_mode","")=="VEHICLE_DEVICE_CODE"
    fun acceptAccessToken(value:String,scope:String=DRIVE_SCOPE){prefs.edit().putString("access",value).putLong("expiry",System.currentTimeMillis()+50*60*1000).putInt("scope_version",SCOPE_VERSION).putString("granted_scope",scope).putString("auth_mode","PLAY_SERVICES").remove("refresh").commit()}
    fun acceptVehicleTokens(access:String,refresh:String,expiresIn:Long,scope:String){
        if(access.isBlank()||refresh.isBlank())throw AuthFailure("TOKEN_REFRESH_UNAVAILABLE","Google이 장기 연결 토큰을 주지 않았습니다. Drive를 다시 연결하세요.")
        prefs.edit().putString("access",access).putString("refresh",refresh).putLong("expiry",System.currentTimeMillis()+expiresIn*1000).putInt("scope_version",SCOPE_VERSION).putString("granted_scope",scope).putString("auth_mode","VEHICLE_DEVICE_CODE").commit()
    }
    private fun connection(url:String, method:String="GET")=(URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod=method;connectTimeout=20000;readTimeout=60000
    }
    private fun response(c:HttpURLConnection):JSONObject {
        val code=c.responseCode
        val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        val j=try{JSONObject(body)}catch(e:Exception){JSONObject()}
        if(code !in 200..299 && !((c.url.toString().contains("oauth2.googleapis.com/token") || c.url.toString().contains("www.googleapis.com/oauth2/v4/token")) && j.has("error"))){
            val error=j.optJSONObject("error")
            val reason=error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
                .ifBlank{error?.optString("status").orEmpty().ifBlank{j.optString("error")}}
            val detail=error?.optString("message").orEmpty().ifBlank{j.optString("error_description",j.optString("message","HTTP $code"))}.take(300)
            val retryable=code==408||code==429||code in 500..599||reason in setOf("rateLimitExceeded","userRateLimitExceeded","backendError","internalError")
            if(retryable){
                AuthDiagnostics.event("DRIVE_API_REQUEST","RETRY","HTTP_$code","reason=$reason")
                throw IOException("Google Drive 일시 오류 (HTTP $code${if(reason.isNotBlank())", $reason" else ""}). 재시도합니다.")
            }
            val failure=when{code==401->"TOKEN_ERROR";reason=="storageQuotaExceeded"->"DRIVE_STORAGE_FULL";code==403->"DRIVE_PERMISSION_DENIED";reason=="access_denied"->"OAUTH_ACCESS_DENIED";else->"HTTP_ERROR"}
            val message=when(failure){"DRIVE_STORAGE_FULL"->"Google Drive 저장공간이 부족합니다. ($detail)";"DRIVE_PERMISSION_DENIED"->"Google Drive 권한을 확인하세요. ($detail)";else->"$detail (HTTP $code)"}
            AuthDiagnostics.event("DRIVE_API_REQUEST","FAILED",failure,"http=$code reason=$reason")
            throw AuthFailure(failure,message,reason=reason)
        }
        return j
    }
    private fun check(j:JSONObject):JSONObject {
        if(j.has("error")){val error=j.optString("error");val e=j.optJSONObject("error");val message=e?.optString("message") ?: j.optString("error_description",j.optString("error"));throw AuthFailure(if(error=="invalid_client")"OAUTH_CLIENT_NOT_FOUND" else "TOKEN_ERROR",message)}
        return j
    }
    @Synchronized private fun token():String {
        if(System.currentTimeMillis()>prefs.getLong("expiry",0)-60000){
            if(vehicleMode && !prefs.getString("refresh",null).isNullOrBlank()) refreshVehicleToken()
            else throw AuthFailure("TOKEN_MISSING","Google Drive에 다시 로그인하세요.")
        }
        return prefs.getString("access",null)?:throw AuthFailure("TOKEN_MISSING","Google Drive에 로그인하세요.")
    }
    private fun clearVehicleSession(){prefs.edit().clear().commit()}
    private fun refreshVehicleToken(){
        val refresh=prefs.getString("refresh",null)?.takeIf{it.isNotBlank()}?:throw AuthFailure("TOKEN_REAUTH_REQUIRED","Google Drive를 다시 연결하세요.")
        val form=linkedMapOf("client_id" to BuildConfig.TV_OAUTH_CLIENT_ID,"client_secret" to BuildConfig.TV_OAUTH_CLIENT_SECRET,"refresh_token" to refresh,"grant_type" to "refresh_token")
        AuthDiagnostics.event("TOKEN_EXCHANGE","START","VEHICLE_REFRESH")
        val j=DeviceCodeAuth.postTokenForm(form)
        if(j.has("error")){
            val error=j.optString("error","oauth_error")
            val failure=DeviceCodeAuth.tokenFailure(error,j.optString("error_description",error))
            AuthDiagnostics.event("TOKEN_EXCHANGE","FAILED",(failure as? AuthFailure)?.code?:"OAUTH_TEMPORARY_FAILURE","oauthError=$error")
            if((failure as? AuthFailure)?.code=="TOKEN_REAUTH_REQUIRED")clearVehicleSession()
            throw failure
        }
        val access=j.optString("access_token")
        if(access.isBlank())throw AuthFailure("TOKEN_ERROR","Google 서버가 새 액세스 토큰을 반환하지 않았습니다.")
        prefs.edit().putString("access",access).putLong("expiry",System.currentTimeMillis()+j.optLong("expires_in",3600)*1000).commit()
        AuthDiagnostics.event("TOKEN_EXCHANGE","SUCCESS","VEHICLE_REFRESH")
    }
    fun logout(){prefs.edit().clear().commit()}
    fun markAccessTokenExpired(){prefs.edit().putLong("expiry",0).commit()}
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
    fun findWakeStatusFile():DriveFile?=latestWakeStatusFile(search(WAKE_STATUS_FILE_NAME))
    fun readTextFile(id:String):String{
        val c=request("https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}?alt=media")
        return try{if(c.responseCode !in 200..299){check(response(c));throw IOException("파일 읽기 실패")};c.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText()}}finally{c.disconnect()}
    }
    fun publishWakeStatus(content:String,savedId:String?):String{
        val existingId=savedId?.takeIf{it.isNotBlank()}?:findWakeStatusFile()?.id
        if(existingId!=null)try{return writeSmallJson(existingId,content)}catch(e:AuthFailure){
            if(!isStaleWakeStatusFailure(e.reason))throw e
            val replacement=findWakeStatusFile()?.id
            if(replacement!=null&&replacement!=existingId)try{return writeSmallJson(replacement,content)}catch(retry:AuthFailure){if(!isStaleWakeStatusFailure(retry.reason))throw retry}
        }
        return writeSmallJson(null,content)
    }
    private fun writeSmallJson(id:String?,content:String):String{
        val bytes=content.toByteArray(Charsets.UTF_8)
        if(id!=null){
            val c=request("https://www.googleapis.com/upload/drive/v3/files/${Uri.encode(id)}?uploadType=media&fields=id","PATCH")
            try{c.doOutput=true;c.setFixedLengthStreamingMode(bytes.size);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write(bytes)};return check(response(c)).getString("id")}finally{c.disconnect()}
        }
        val boundary="p4files_${System.currentTimeMillis()}"
        val metadata=JSONObject().put("name",WAKE_STATUS_FILE_NAME).put("mimeType","application/json").put("parents",JSONArray().put("root"))
        val body=buildString{
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString()).append("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content).append("\r\n--$boundary--")
        }.toByteArray(Charsets.UTF_8)
        val c=request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id","POST")
        try{c.doOutput=true;c.setFixedLengthStreamingMode(body.size);c.setRequestProperty("Content-Type","multipart/related; boundary=$boundary");c.outputStream.use{it.write(body)};return check(response(c)).getString("id")}finally{c.disconnect()}
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
    fun uploadEmpty(uri:Uri,name:String,mime:String,parent:String):String{
        (ctx.contentResolver.openInputStream(uri)?:throw IOException("파일을 열 수 없습니다.")).use{if(it.read()!=-1)throw IOException("0바이트 파일로 확인되지 않아 업로드를 중단했습니다.")}
        val init=request("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable","POST")
        val location=try{init.doOutput=true;init.setRequestProperty("Content-Type","application/json");init.setRequestProperty("X-Upload-Content-Type",mime);init.setRequestProperty("X-Upload-Content-Length","0");init.outputStream.use{it.write(JSONObject().put("name",name).put("parents",JSONArray().put(parent)).toString().toByteArray())};if(init.responseCode !in 200..299)check(response(init));init.getHeaderField("Location")?:throw IOException("업로드 세션 생성 실패")}finally{init.disconnect()}
        val c=request(location,"PUT")
        return try{c.doOutput=true;c.setFixedLengthStreamingMode(0);c.setRequestProperty("Content-Type",mime);c.outputStream.use{};check(response(c)).getString("id")}finally{c.disconnect()}
    }
    fun uploadResumable(uri:Uri,name:String,mime:String,parent:String,length:Long,savedSession:String,onSession:(String)->Unit,progress:(Long)->Unit){
        uploadResumableFrom({offset->ctx.contentResolver.openInputStream(uri)?.apply{skipFully(this,offset)}},name,mime,parent,length,savedSession,onSession,progress)
    }
    private fun uploadFailure(stage:String,error:IOException):IOException =
        if(error is AuthFailure)error else IOException("$stage: ${error.message?:error.javaClass.simpleName}",error)
    fun uploadResumableFrom(openSource:(Long)->InputStream?,name:String,mime:String,parent:String,length:Long,savedSession:String,onSession:(String)->Unit,progress:(Long)->Unit){
        val knownLength=length>0
        var session=savedSession;var offset=0L
        if(session.isNotBlank()){
            val probe=request(session,"PUT")
            try{probe.doOutput=true;probe.setFixedLengthStreamingMode(0);probe.setRequestProperty("Content-Range","bytes */${if(knownLength)length else "*"}");val code=try{probe.responseCode}catch(e:IOException){throw uploadFailure("Drive 업로드 재개 확인",e)}
                if(code==308)offset=uploadedOffset(probe)
                else if(code in 200..299){progress(if(knownLength)length else uploadedOffset(probe));return}
                else if(code==404){session="";offset=0}else try{check(response(probe))}catch(e:IOException){throw uploadFailure("Google Drive 재개 응답 읽기",e)}
            }finally{probe.disconnect()}
        }
        if(session.isBlank()){
            val init=request("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable","POST")
            session=try{try{init.doOutput=true;init.setRequestProperty("Content-Type","application/json");init.setRequestProperty("X-Upload-Content-Type",mime);if(knownLength)init.setRequestProperty("X-Upload-Content-Length",length.toString());init.outputStream.use{it.write(JSONObject().put("name",name).put("parents",JSONArray().put(parent)).toString().toByteArray())};if(init.responseCode !in 200..299)check(response(init));init.getHeaderField("Location")?:throw IOException("업로드 세션 생성 실패")}catch(e:IOException){throw uploadFailure("Google Drive 업로드 세션 생성",e)}}finally{init.disconnect()}
            onSession(session)
        }
        if(knownLength&&offset>length)throw IOException("Drive 업로드 위치가 파일 크기를 초과했습니다.")
        val input=try{openSource(offset)}catch(e:IOException){throw uploadFailure("차량 DVR 원본 연결",e)}?:throw IOException("파일을 열 수 없습니다.")
        input.use{raw->val stream=PushbackInputStream(raw,1);progress(offset);val chunk=ByteArray(UPLOAD_CHUNK_SIZE)
            while(true){
                val wanted=if(knownLength)minOf(chunk.size.toLong(),length-offset).toInt() else chunk.size
                if(wanted==0)throw IOException("Drive 업로드가 완료되지 않았습니다.")
                var count=0
                while(count<wanted){val n=try{stream.read(chunk,count,wanted-count)}catch(e:IOException){throw uploadFailure("차량 DVR 원본 읽기",e)};if(n<0)break;count+=n}
                if(count==0)throw EOFException(if(knownLength)"예상보다 파일이 짧습니다." else "빈 파일은 업로드할 수 없습니다.")
                if(knownLength&&count<wanted)throw EOFException("예상보다 파일이 짧습니다.")
                val last=if(knownLength)offset+count==length else if(count<wanted)true else {val next=try{stream.read()}catch(e:IOException){throw uploadFailure("차량 DVR 원본 읽기",e)};if(next<0)true else{stream.unread(next);false}}
                var accepted=0;var noProgress=0
                while(accepted<count){
                    val start=offset+accepted;val end=offset+count-1;val c=request(session,"PUT")
                    try{
                        c.readTimeout=180_000
                        c.doOutput=true;c.setFixedLengthStreamingMode(count-accepted);c.setRequestProperty("Content-Type",mime);c.setRequestProperty("Content-Range",driveUploadContentRange(start,end+1,length,last))
                        try{c.outputStream.use{it.write(chunk,accepted,count-accepted)}}catch(e:IOException){throw uploadFailure("Google Drive 청크 전송",e)}
                        val code=try{c.responseCode}catch(e:IOException){throw uploadFailure("Google Drive 응답 수신",e)}
                        if(code==308){val next=uploadedOffset(c);when(uploadChunkProgress(next,start,offset+count)){
                            ChunkProgress.NONE->{if(++noProgress>2)throw IOException("Drive 업로드 위치가 진행되지 않았습니다.")}
                            ChunkProgress.ADVANCED->{accepted=(next-offset).toInt();noProgress=0;progress(next)}
                        }}
                        else if(code in 200..299){if(!last)throw IOException("Drive가 파일 끝보다 먼저 업로드를 완료했습니다.");progress(if(knownLength)length else offset+count);return}
                        else try{check(response(c))}catch(e:IOException){throw uploadFailure("Google Drive 응답 읽기",e)}
                    }finally{c.disconnect()}
                }
                offset+=count
                if(last)throw IOException("Drive가 최종 업로드 청크를 완료하지 않았습니다.")
            }
        }
    }
    private fun uploadedOffset(c:HttpURLConnection)=driveAcknowledgedOffset(c.getHeaderField("Range"))
    private fun skipFully(input:InputStream,count:Long){var skipped=0L;while(skipped<count){val n=input.skip(count-skipped);if(n>0)skipped+=n else if(input.read()<0)throw EOFException("업로드 재개 위치를 찾을 수 없습니다.") else skipped++}}
    fun download(id:String,output:OutputStream,progress:(Long)->Unit){val c=request("https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}?alt=media");try{if(c.responseCode !in 200..299){check(response(c));throw IOException("다운로드 실패")};c.inputStream.use{copy(it,output,progress)}}finally{c.disconnect()}}
    fun downloadResumable(id:String,file:File,progress:(Long)->Unit){var offset=if(file.exists())file.length() else 0L;val c=request("https://www.googleapis.com/drive/v3/files/${Uri.encode(id)}?alt=media");if(offset>0)c.setRequestProperty("Range","bytes=$offset-");try{val code=c.responseCode;if(code !in 200..299){check(response(c));throw IOException("다운로드 실패")};val append=code==206&&offset>0;if(!append)offset=0;FileOutputStream(file,append).use{out->c.inputStream.use{input->val buffer=ByteArray(256*1024);var total=offset;while(true){val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);total+=n;progress(total)}}}}finally{c.disconnect()}}
    private fun copy(input:InputStream,output:OutputStream,progress:(Long)->Unit){val buffer=ByteArray(256*1024);var total=0L;var last=0L;while(true){val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n);total+=n;if(System.currentTimeMillis()-last>300){progress(total);last=System.currentTimeMillis()}};progress(total)}
}
