package com.polestar4.drivefilemanager

import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.UnknownHostException
import java.net.URL

class DeviceCodeAuth {
    data class Challenge(val deviceCode:String,val userCode:String,val verificationUrl:String,val expiresIn:Long,val interval:Long)
    data class Tokens(val access:String,val refresh:String,val expiresIn:Long,val scope:String)

    fun configured()=BuildConfig.TV_OAUTH_CLIENT_ID.isNotBlank() && BuildConfig.TV_OAUTH_CLIENT_SECRET.isNotBlank()

    fun begin():Challenge {
        if(!configured())throw AuthFailure("CONFIG_MISSING","차량용 TVs and Limited Input devices OAuth Client 설정이 없습니다.")
        AuthDiagnostics.event("AUTH_START","START","VEHICLE_DEVICE_CODE")
        val j=postWithDnsFallback(listOf("https://oauth2.googleapis.com/device/code","https://accounts.google.com/o/oauth2/device/code"),linkedMapOf(
            "client_id" to BuildConfig.TV_OAUTH_CLIENT_ID,
            "scope" to "openid email ${DriveApi.VEHICLE_SCOPE}"
        ))
        if(j.has("error"))throw oauthFailure(j)
        return Challenge(j.getString("device_code"),j.getString("user_code"),j.optString("verification_url",j.optString("verification_uri","https://www.google.com/device")),j.optLong("expires_in",1800),j.optLong("interval",5))
    }

    fun poll(challenge:Challenge,isCancelled:()->Boolean):Tokens {
        val deadline=System.currentTimeMillis()+challenge.expiresIn*1000
        var interval=challenge.interval.coerceAtLeast(5)
        while(System.currentTimeMillis()<deadline && !isCancelled()){
            Thread.sleep(interval*1000)
            val j=postTokenForm(linkedMapOf(
                "client_id" to BuildConfig.TV_OAUTH_CLIENT_ID,
                "client_secret" to BuildConfig.TV_OAUTH_CLIENT_SECRET,
                "device_code" to challenge.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            ))
            val error=j.optString("error")
            when(error){
                "authorization_pending"->continue
                "slow_down"->{interval+=5;continue}
                "access_denied"->throw AuthFailure("OAUTH_ACCESS_DENIED","Google 계정에서 승인이 거부되었습니다.")
                "expired_token"->throw AuthFailure("TOKEN_EXPIRED","인증 코드가 만료되었습니다. 다시 시도하세요.")
                ""->{
                    val access=j.optString("access_token")
                    if(access.isBlank())throw AuthFailure("TOKEN_ERROR","Google 서버가 액세스 토큰을 반환하지 않았습니다.")
                    return Tokens(access,j.optString("refresh_token"),j.optLong("expires_in",3600),j.optString("scope",DriveApi.VEHICLE_SCOPE))
                }
                else->throw oauthFailure(j)
            }
        }
        if(isCancelled())throw AuthFailure("OAUTH_CANCELED","차량 인증이 취소되었습니다.")
        throw AuthFailure("TOKEN_EXPIRED","인증 시간이 만료되었습니다. 다시 시도하세요.")
    }

    companion object {
        private fun encode(form:Map<String,String>)=form.entries.joinToString("&"){"${Uri.encode(it.key)}=${Uri.encode(it.value)}"}
        private fun post(url:String,form:Map<String,String>):JSONObject {
            val c=(URL(url).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=20000;readTimeout=30000;doOutput=true;setRequestProperty("Content-Type","application/x-www-form-urlencoded")}
            try{
                c.outputStream.use{it.write(encode(form).toByteArray())}
                val body=(if(c.responseCode in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
                return try{JSONObject(body)}catch(_:Exception){throw IOException("Google OAuth 응답을 읽을 수 없습니다 (HTTP ${c.responseCode}).")}
            }finally{c.disconnect()}
        }
        private fun postWithDnsFallback(urls:List<String>,form:Map<String,String>):JSONObject {
            var last:UnknownHostException?=null
            for(url in urls)try{return post(url,form)}catch(e:UnknownHostException){last=e;AuthDiagnostics.event("TOKEN_EXCHANGE","RETRY","DNS_FALLBACK","host=${URL(url).host}")}
            throw last?:UnknownHostException("Google OAuth 서버 주소를 찾을 수 없습니다.")
        }
        fun postTokenForm(form:Map<String,String>):JSONObject = postWithDnsFallback(listOf("https://oauth2.googleapis.com/token","https://www.googleapis.com/oauth2/v4/token"),form)
        private fun oauthFailure(j:JSONObject):AuthFailure {
            val error=j.optString("error","oauth_error")
            val code=when(error){"invalid_client"->"OAUTH_CLIENT_NOT_FOUND";"access_denied"->"OAUTH_ACCESS_DENIED";"invalid_scope"->"OAUTH_SCOPE_INVALID";else->"TOKEN_ERROR"}
            return AuthFailure(code,j.optString("error_description",error))
        }
    }
}
