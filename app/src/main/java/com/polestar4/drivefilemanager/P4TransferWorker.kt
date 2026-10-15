package com.polestar4.drivefilemanager

import android.app.*
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.io.File
import java.io.InterruptedIOException
import java.util.UUID

class P4TransferWorker(ctx:Context,params:WorkerParameters):Worker(ctx,params){
    companion object { private const val CHANNEL="p4_transfers" }
    override fun doWork():Result{
        val id=inputData.getString("taskId")?:return Result.failure();val task=TransferStore.get(applicationContext,id)?:return Result.failure()
        if(task.status=="CANCELED"||task.status=="PAUSED")return Result.success()
        TransferStore.update(applicationContext,id){it.copy(status="RUNNING",error="")};setForegroundAsync(foreground(task,task.progress)).get()
        return try{
            val api=DriveApi(applicationContext)
            if(task.kind=="UPLOAD"){
                if(task.size>0)api.uploadResumable(Uri.parse(task.source),task.name,task.mime,task.parent,task.size,task.session,{session->TransferStore.update(applicationContext,id){it.copy(session=session)}},{n->checkpoint(id,task,n)})
                else api.upload(Uri.parse(task.source),task.name,task.mime,task.parent){n->checkpoint(id,task,n)}
            }else{
                val dir=File(applicationContext.getExternalFilesDir(null),"Downloads").apply{mkdirs()};val safe=task.name.replace(Regex("[\\\\/:*?\"<>|]"),"_").take(150).ifBlank{"file"};var out=File(dir,safe);var n=1;while(out.exists())out=File(dir,"${n++}_$safe")
                val temp=File(applicationContext.cacheDir,"transfer-$id.part")
                api.downloadResumable(task.remoteId,temp){done->checkpoint(id,task,done)}
                if(!temp.renameTo(out)){temp.inputStream().use{input->out.outputStream().use{input.copyTo(it)}};temp.delete()}
            }
            TransferStore.update(applicationContext,id){it.copy(status="SUCCESS",progress=if(it.size>0)it.size else it.progress)};Result.success()
        }catch(e:InterruptedIOException){if(TransferStore.get(applicationContext,id)?.status=="RUNNING")TransferStore.update(applicationContext,id){it.copy(status="PAUSED")};Result.success()
        }catch(e:AuthFailure){TransferStore.update(applicationContext,id){it.copy(status="FAILED",error="${e.code}: ${e.message}")};Result.failure()
        }catch(e:Exception){TransferStore.update(applicationContext,id){it.copy(status="FAILED",error=e.message?:e.javaClass.simpleName)};if(runAttemptCount<4)Result.retry() else Result.failure()}
    }
    private fun checkpoint(id:String,task:TransferTask,done:Long){if(isStopped)throw InterruptedIOException("transfer paused");TransferStore.update(applicationContext,id){it.copy(progress=done,status="RUNNING")};setProgressAsync(workDataOf("bytes" to done,"total" to task.size));setForegroundAsync(foreground(task,done))}
    private fun foreground(task:TransferTask,done:Long):ForegroundInfo{
        if(Build.VERSION.SDK_INT>=26)(applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"P4 파일 전송",NotificationManager.IMPORTANCE_LOW))
        val percent=if(task.size>0)((done*100/task.size).coerceIn(0,100)).toInt() else 0
        val cancelIntent=try{WorkManager.getInstance(applicationContext).createCancelPendingIntent(UUID.fromString(task.workId))}catch(_:Exception){null}
        val builder=NotificationCompat.Builder(applicationContext,CHANNEL).setSmallIcon(R.drawable.p4_files_icon).setContentTitle(if(task.kind=="UPLOAD")"Drive 업로드" else "Drive 다운로드").setContentText(task.name).setOnlyAlertOnce(true).setOngoing(true).setProgress(100,percent,task.size<=0)
        if(cancelIntent!=null)builder.addAction(0,"취소",cancelIntent)
        val notification=builder.build()
        return if(Build.VERSION.SDK_INT>=29)ForegroundInfo(task.id.hashCode(),notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(task.id.hashCode(),notification)
    }
}
