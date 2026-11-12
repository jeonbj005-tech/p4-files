package com.polestar4.drivefilemanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DvrWakeProbeWorker(context:Context,params:WorkerParameters):Worker(context,params){
    companion object{
        const val UNIQUE_WORK="dvr_wake_probe"
        const val PREFS="dvr_wake_probe"
        const val ACTIVE="active"
        const val STARTED_AT="started_at"
        const val LOG="log"
        private const val CHANNEL="p4_dvr_probe"
        private const val NOTIFICATION_ID=4701
        private const val DURATION_MS=5*60*1000L
        private const val INTERVAL_MS=15*1000L

        @Synchronized fun appendLog(context:Context,message:String){
            val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            val stamp=SimpleDateFormat("HH:mm:ss",Locale.KOREA).format(Date())
            val lines=(prefs.getString(LOG,"").orEmpty()+"\n$stamp · $message").split('\n').filter{it.isNotBlank()}.takeLast(60)
            prefs.edit().putString(LOG,lines.joinToString("\n")).apply()
        }
    }

    override fun doWork():Result{
        val prefs=applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val startedAt=prefs.getLong(STARTED_AT,System.currentTimeMillis())
        val endsAt=startedAt+DURATION_MS
        return try{
            prefs.edit().putBoolean(ACTIVE,true).apply()
            setForegroundAsync(foregroundInfo()).get()
            appendLog(applicationContext,"백그라운드 DVR 상태 관찰 시작 · 파일 목록/녹화 모드 변경 없음")
            while(!isStopped&&System.currentTimeMillis()<endsAt){
                val result=try{
                    "DVR 응답 · ${DashcamApi.shared.probeRecordingState()}"
                }catch(error:Exception){
                    "DVR 응답 없음 · ${error.javaClass.simpleName}"
                }
                appendLog(applicationContext,result)
                val wait=(endsAt-System.currentTimeMillis()).coerceAtMost(INTERVAL_MS)
                if(wait>0)Thread.sleep(wait)
            }
            appendLog(applicationContext,if(isStopped)"관찰 중지됨" else "5분 관찰 종료")
            prefs.edit().putBoolean(ACTIVE,false).apply()
            Result.success()
        }catch(_:InterruptedException){
            Thread.currentThread().interrupt()
            appendLog(applicationContext,"관찰 작업 중단")
            prefs.edit().putBoolean(ACTIVE,false).apply()
            Result.success()
        }catch(error:Exception){
            appendLog(applicationContext,"테스트 실행 실패 · ${error.javaClass.simpleName}")
            prefs.edit().putBoolean(ACTIVE,false).apply()
            Result.failure()
        }
    }

    private fun foregroundInfo():ForegroundInfo{
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(CHANNEL,"차량 연결 테스트",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel=WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification=NotificationCompat.Builder(applicationContext,CHANNEL)
            .setSmallIcon(R.drawable.p4_files_icon)
            .setContentTitle("P4 Files 차량 연결 테스트")
            .setContentText("DVR 상태만 확인 중 · 5분 후 종료")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(0,"테스트 중지",cancel).build()
        return if(Build.VERSION.SDK_INT>=29)ForegroundInfo(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID,notification)
    }
}
