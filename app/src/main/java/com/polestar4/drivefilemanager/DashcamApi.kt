package com.polestar4.drivefilemanager

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Polestar 4 factory DVR gallery protocol used by Polaris 3.2. */
class DashcamApi {
    data class Category(val type:String,val label:String,val path:String,val count:Int)
    data class Media(val type:String,val id:String,val name:String,val size:Long,val dateTime:Long,val duration:Int,val url:String)

    companion object {
        private const val BASE="http://198.18.37.20"
        private const val STATUS="/status?app=gallery"
        private const val MEDIA_DIRS="/mediaDirList?app=gallery"
        private const val MODE="/status"
        private const val ENTER_BODY="{\"app\":\"gallery\",\"recording\":\"enter-file-list\"}"
        private const val NORMAL_BODY="{\"app\":\"gallery\",\"recording\":\"normal\"}"
    }

    private fun connection(url:String)=((URL(url).openConnection()) as HttpURLConnection).apply {
        connectTimeout=8_000
        readTimeout=25_000
        instanceFollowRedirects=true
        setRequestProperty("Accept-Encoding","identity")
    }

    private fun response(c:HttpURLConnection):String {
        val code=c.responseCode
        val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        if(code !in 200..299)throw IOException("DVR HTTP $code: ${body.take(200)}")
        return body
    }

    private fun get(path:String):String { val c=connection(BASE+path);return try{response(c)}finally{c.disconnect()} }

    private fun postMode(body:String){
        val c=connection(BASE+MODE).apply {
            requestMethod="POST"
            doOutput=true
            setRequestProperty("Content-Type","application/json; charset=utf-8")
        }
        try {
            c.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
            val result=JSONObject(response(c)).optString("result")
            if(result!="ok")throw IOException("DVR mode change rejected: $result")
        } finally { c.disconnect() }
    }

    private fun array(json:JSONObject):JSONArray? =
        listOf("list","files","data","items").firstNotNullOfOrNull{json.optJSONArray(it)}
            ?: json.keys().asSequence().mapNotNull{json.optJSONArray(it)}.firstOrNull()

    fun enterFileList(){
        postMode(ENTER_BODY)
        repeat(6){
            if(JSONObject(get(STATUS)).optString("recording")=="in-file-list")return
            Thread.sleep(250)
        }
        throw IOException("DVR did not enter file-list mode")
    }

    fun leaveFileList(){try{postMode(NORMAL_BODY)}catch(_:Exception){}}

    fun categories():List<Category>{
        val a=array(JSONObject(get(MEDIA_DIRS)))?:return emptyList()
        return (0 until a.length()).mapNotNull{i->a.optJSONObject(i)}.mapNotNull{j->
            val type=j.optString("mediaType",j.optString("type"))
            if(type.isBlank())return@mapNotNull null
            val path=j.optString("mediaPath",j.optString("path",j.optString("dir",type)))
            val label=j.optString("label").ifBlank { when(type){
                "normal","continuous"->"주행(일반)"
                "emergency"->"긴급"
                "parking"->"주차"
                "avm"->"서라운드"
                "avm_emergency"->"서라운드 긴급"
                "photo"->"사진"
                else->type
            } }
            Category(type,label,path,j.optInt("fileCount",j.optInt("count",-1)))
        }
    }

    fun list(category:Category,start:Int=0,count:Int=200):List<Media>{
        val query="/filelist?app=gallery&type=${enc(category.type)}&startIndex=$start&count=$count&sort=newest-first&infoLevel=2"
        val a=array(JSONObject(get(query)))?:return emptyList()
        return (0 until a.length()).mapNotNull{i->a.optJSONObject(i)}.mapNotNull{j->
            val name=j.optString("name")
            if(name.isBlank()||name=="."||name==".."||name.contains('/')||name.contains('\\'))return@mapNotNull null
            val type=j.optString("mediaType",category.type)
            val id=j.optString("id",name)
            val relative=category.path.trim('/').split('/').filter{it.isNotBlank()}.joinToString("/"){enc(it)}
            val mediaUrl="$BASE/${if(relative.isBlank())"" else "$relative/"}${enc(name)}"
            Media(type,id,name,j.optLong("size").coerceAtLeast(0),j.optLong("dateTime"),j.optInt("duration"),mediaUrl)
        }
    }

    /** The factory gallery only returns at most 200 records per request. */
    fun listAll(category:Category,progress:(Int)->Unit={}):List<Media>{
        val result=ArrayList<Media>()
        val seen=HashSet<String>()
        var start=0
        repeat(100){
            val page=list(category,start,200)
            val unique=page.filter{seen.add("${it.type}/${it.id}/${it.name}")}
            result.addAll(unique)
            progress(result.size)
            if(page.size<200||unique.isEmpty())return result
            start+=200
            Thread.sleep(60)
        }
        return result
    }

    fun download(media:Media,out:OutputStream,progress:(Long)->Unit={}){
        val c=connection(media.url)
        try {
            if(c.responseCode !in 200..299)throw IOException("DVR HTTP ${c.responseCode}")
            c.inputStream.use{input->
                val buffer=ByteArray(256*1024)
                var total=0L
                while(true){val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);total+=n;progress(total)}
            }
        } finally { c.disconnect() }
    }

    @Synchronized fun thumbnail(media:Media):ByteArray?{
        val url="$BASE/thumbnail?app=gallery&mediaType=${enc(media.type)}&id=${enc(media.id)}&time=${media.dateTime}"
        var entered=false
        try{
            repeat(3){attempt->
                val c=connection(url).apply{connectTimeout=6_000;readTimeout=10_000}
                try{
                    val code=c.responseCode
                    if(code==200){val bytes=c.inputStream.use{it.readBytes()};if(bytes.isNotEmpty())return bytes}
                    if((code==403||code==409)&&!entered){c.disconnect();enterFileList();entered=true}
                }catch(_:Exception){if(!entered)try{enterFileList();entered=true}catch(_:Exception){}}
                finally{c.disconnect()}
                if(attempt<2)Thread.sleep(250)
            }
        }finally{
            if(entered)leaveFileList()
        }
        return null
    }

    private fun enc(value:String)=URLEncoder.encode(value,"UTF-8").replace("+","%20")
    fun mime(media:Media):String=when(media.name.substringAfterLast('.').lowercase()){
        "jpg","jpeg"->"image/jpeg"
        "png"->"image/png"
        "mp4"->"video/mp4"
        "ts"->"video/mp2t"
        else->if(media.type.contains("photo",true))"image/jpeg" else "video/*"
    }
}
