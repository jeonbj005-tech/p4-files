package com.polestar4.drivefilemanager

import java.io.IOException
import java.util.Locale

internal enum class ChunkProgress { NONE, ADVANCED }

internal fun driveUploadContentRange(start:Long,endExclusive:Long,totalLength:Long,last:Boolean):String {
    require(start>=0&&endExclusive>start)
    val total=if(totalLength>0)totalLength.toString() else if(last)endExclusive.toString() else "*"
    require(total=="*"||endExclusive<=total.toLong())
    return "bytes $start-${endExclusive-1}/$total"
}

internal fun driveAcknowledgedOffset(range:String?):Long =
    range?.substringAfterLast('-')?.toLongOrNull()?.plus(1)?:0L

internal fun uploadChunkProgress(next:Long,start:Long,endExclusive:Long):ChunkProgress {
    if(next<start||next>endExclusive)throw IOException("Drive 업로드 응답 위치가 청크 범위를 벗어났습니다.")
    return if(next==start)ChunkProgress.NONE else ChunkProgress.ADVANCED
}

internal fun isStaleWakeStatusFailure(reason:String?):Boolean =
    when(reason?.filter{it.isLetterOrDigit()}?.lowercase(Locale.ROOT)){
        "notfound","insufficientfilepermissions","appnotauthorizedtofile"->true
        else->false
    }
