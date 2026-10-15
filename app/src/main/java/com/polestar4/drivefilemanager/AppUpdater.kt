package com.polestar4.drivefilemanager

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(val versionCode:Int,val versionName:String,val apkUrl:String,val sha256:String,val notes:String)

class AppUpdater(private val context:Context){
    fun check(manifestUrl:String):UpdateInfo{
        require(manifestUrl.startsWith("https://")){"업데이트 주소는 HTTPS여야 합니다."}
        val json=JSONObject(request(manifestUrl).decodeToString())
        val info=UpdateInfo(json.getInt("versionCode"),json.getString("versionName"),json.getString("apkUrl"),json.getString("sha256").replace(":","").uppercase(),json.optString("notes"))
        require(info.apkUrl.startsWith("https://")){"APK 주소는 HTTPS여야 합니다."}
        require(info.sha256.matches(Regex("[0-9A-F]{64}"))){"SHA-256 형식이 올바르지 않습니다."}
        return info
    }
    fun download(info:UpdateInfo,progress:(Long,Long)->Unit):File{
        val dir=File(context.getExternalFilesDir(null),"Downloads").apply{mkdirs()}
        val target=File(dir,"P4-Files-v${info.versionName}.apk");val temp=File(context.cacheDir,"p4-update-${info.versionCode}.part")
        val c=(URL(info.apkUrl).openConnection() as HttpURLConnection).apply{connectTimeout=15_000;readTimeout=60_000;instanceFollowRedirects=true;setRequestProperty("Accept-Encoding","identity")}
        try{
            if(c.responseCode !in 200..299)throw java.io.IOException("업데이트 다운로드 HTTP ${c.responseCode}")
            val total=c.contentLengthLong;var done=0L
            c.inputStream.use{input->temp.outputStream().use{out->val buffer=ByteArray(256*1024);while(true){val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);done+=n;progress(done,total)}}}
        }finally{c.disconnect()}
        val actual=sha256(temp);if(actual!=info.sha256){temp.delete();throw java.io.IOException("APK SHA-256 불일치\n예상: ${info.sha256}\n실제: $actual")}
        if(target.exists())target.delete();if(!temp.renameTo(target)){temp.copyTo(target,true);temp.delete()};return target
    }
    private fun request(url:String):ByteArray{val c=(URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=10_000;readTimeout=15_000;instanceFollowRedirects=true;setRequestProperty("Accept","application/json")};return try{if(c.responseCode !in 200..299)throw java.io.IOException("업데이트 확인 HTTP ${c.responseCode}");c.inputStream.use{it.readBytes()}}finally{c.disconnect()}}
    private fun sha256(file:File):String{val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val b=ByteArray(256*1024);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)}};return digest.digest().joinToString(""){"%02X".format(it)}}
}
