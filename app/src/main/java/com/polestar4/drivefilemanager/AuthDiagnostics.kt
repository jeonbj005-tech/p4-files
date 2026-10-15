package com.polestar4.drivefilemanager

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

object AuthDiagnostics {
    const val TAG = "P4DriveAuth"
    fun event(stage:String,result:String,code:String?=null,detail:String?=null){
        val safe=detail?.replace(Regex("(?i)(token|secret|device.?code|authorization)\\s*[:=]\\s*\\S+"),"$1=<redacted>")
        Log.i(TAG,"$stage result=$result${code?.let{" code=$it"}.orEmpty()}${safe?.let{" detail=$it"}.orEmpty()}")
    }
    fun fingerprint(context:Context):String {
        val packageInfo=context.packageManager.getPackageInfo(context.packageName,if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)
        val signature=if(Build.VERSION.SDK_INT>=28) packageInfo.signingInfo?.apkContentsSigners?.firstOrNull() else @Suppress("DEPRECATION") packageInfo.signatures?.firstOrNull()
        return if(signature==null)"UNAVAILABLE" else MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()).joinToString(":"){String.format("%02X",it)}
    }
    fun report(context:Context):String {
        val gmsStatus=try{val info=context.packageManager.getPackageInfo("com.google.android.gms",0);"AVAILABLE ${info.versionName}"}catch(_:Exception){"NOT_AVAILABLE"}
        val authClient=try{Class.forName("com.google.android.gms.auth.api.identity.Identity");"AVAILABLE"}catch(_:Exception){"NOT_BUNDLED"}
        val api=DriveApi(context)
        return "package=${context.packageName}\napplicationId=${BuildConfig.APPLICATION_ID}\nversion=${BuildConfig.VERSION_NAME}\nsha1=${fingerprint(context)}\nandroid=${Build.VERSION.RELEASE}\nsdk=${Build.VERSION.SDK_INT}\ngooglePlayServices=$gmsStatus\nauthorizationClient=$authClient\nauthMode=HYBRID_PLAY_SERVICES_OR_DEVICE_CODE\nactiveSession=${if(api.vehicleMode)"VEHICLE_DEVICE_CODE" else if(api.connected)"PLAY_SERVICES" else "NONE"}\nphoneScope=${DriveApi.DRIVE_SCOPE}\nvehicleScope=${DriveApi.VEHICLE_SCOPE}\nvehicleClientConfigured=${BuildConfig.TV_OAUTH_CLIENT_ID.isNotBlank()}\nvehicleClientSecretConfigured=${BuildConfig.TV_OAUTH_CLIENT_SECRET.isNotBlank()}\nandroidOAuthRegistrationRequired=true\nvehicleOAuthType=TVS_AND_LIMITED_INPUT_DEVICES\n"
    }
}

class AuthFailure(val code:String,message:String,cause:Throwable?=null):java.io.IOException(message,cause)
