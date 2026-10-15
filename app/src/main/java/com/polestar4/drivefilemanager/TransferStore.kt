package com.polestar4.drivefilemanager

import android.content.Context
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class TransferTask(
    val id:String,
    val workId:String,
    val kind:String,
    val name:String,
    val source:String,
    val mime:String,
    val parent:String,
    val remoteId:String,
    val size:Long,
    val progress:Long,
    val status:String,
    val error:String,
    val created:Long,
    val session:String=""
)

object TransferStore {
    private const val PREFS="p4_transfer_queue"
    private const val KEY="tasks"
    private fun prefs(ctx:Context)=ctx.getSharedPreferences(PREFS,0)
    @Synchronized fun list(ctx:Context):List<TransferTask>{
        val array=try{JSONArray(prefs(ctx).getString(KEY,"[]"))}catch(_:Exception){JSONArray()}
        return (0 until array.length()).mapNotNull{i->try{val j=array.getJSONObject(i);TransferTask(
            j.getString("id"),j.optString("workId"),j.getString("kind"),j.getString("name"),j.optString("source"),j.optString("mime","application/octet-stream"),j.optString("parent","root"),j.optString("remoteId"),j.optLong("size"),j.optLong("progress"),j.optString("status","QUEUED"),j.optString("error"),j.optLong("created"),j.optString("session")
        )}catch(_:Exception){null}}.sortedByDescending{it.created}
    }
    @Synchronized private fun save(ctx:Context,tasks:List<TransferTask>){
        val array=JSONArray();tasks.take(100).forEach{t->array.put(JSONObject().put("id",t.id).put("workId",t.workId).put("kind",t.kind).put("name",t.name).put("source",t.source).put("mime",t.mime).put("parent",t.parent).put("remoteId",t.remoteId).put("size",t.size).put("progress",t.progress).put("status",t.status).put("error",t.error).put("created",t.created).put("session",t.session))}
        prefs(ctx).edit().putString(KEY,array.toString()).commit()
    }
    fun get(ctx:Context,id:String)=list(ctx).firstOrNull{it.id==id}
    @Synchronized fun put(ctx:Context,task:TransferTask){save(ctx,list(ctx).filterNot{it.id==task.id}+task)}
    @Synchronized fun update(ctx:Context,id:String,change:(TransferTask)->TransferTask){val all=list(ctx);save(ctx,all.map{if(it.id==id)change(it) else it})}
    @Synchronized fun clearFinished(ctx:Context){save(ctx,list(ctx).filter{it.status in setOf("QUEUED","RUNNING","PAUSED")})}
}

object TransferManager {
    const val TAG="P4_TRANSFER"
    private fun request(taskId:String)=OneTimeWorkRequestBuilder<P4TransferWorker>()
        .setInputData(workDataOf("taskId" to taskId)).addTag(TAG)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).build()
    fun enqueueUpload(ctx:Context,source:String,name:String,mime:String,parent:String,size:Long):String{
        val id=UUID.randomUUID().toString();val req=request(id)
        TransferStore.put(ctx,TransferTask(id,req.id.toString(),"UPLOAD",name,source,mime,parent,"",size,0,"QUEUED","",System.currentTimeMillis()))
        WorkManager.getInstance(ctx).enqueue(req);return id
    }
    fun enqueueDownload(ctx:Context,remoteId:String,name:String,mime:String,size:Long):String{
        val id=UUID.randomUUID().toString();val req=request(id)
        TransferStore.put(ctx,TransferTask(id,req.id.toString(),"DOWNLOAD",name,"",mime,"root",remoteId,size,0,"QUEUED","",System.currentTimeMillis()))
        WorkManager.getInstance(ctx).enqueue(req);return id
    }
    fun pause(ctx:Context,id:String){TransferStore.get(ctx,id)?.let{if(it.workId.isNotBlank())WorkManager.getInstance(ctx).cancelWorkById(UUID.fromString(it.workId))};TransferStore.update(ctx,id){it.copy(status="PAUSED")}}
    fun cancel(ctx:Context,id:String){TransferStore.get(ctx,id)?.let{if(it.workId.isNotBlank())WorkManager.getInstance(ctx).cancelWorkById(UUID.fromString(it.workId))};TransferStore.update(ctx,id){it.copy(status="CANCELED")}}
    fun resume(ctx:Context,id:String){val task=TransferStore.get(ctx,id)?:return;val req=request(id);TransferStore.put(ctx,task.copy(workId=req.id.toString(),status="QUEUED",error=""));WorkManager.getInstance(ctx).enqueue(req)}
}
