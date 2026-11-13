package com.polestar4.drivefilemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException

class DriveUploadProtocolTest {
    @Test fun transientIoRetriesButUserCodeFailuresDoNot() {
        assertTrue(shouldRetryTransfer(InterruptedIOException("timeout"),0))
        assertTrue(shouldRetryTransfer(IOException("unexpected end of stream"),7))
        assertFalse(shouldRetryTransfer(IOException("still broken"),8))
        assertFalse(shouldRetryTransfer(IllegalStateException("bad state"),0))
    }

    @Test fun knownLengthIsUsedForEveryChunkRange() {
        assertEquals("bytes 0-4194303/293601280",driveUploadContentRange(0,4_194_304,293_601_280,false))
        assertEquals("bytes 289406976-293601279/293601280",driveUploadContentRange(289_406_976,293_601_280,293_601_280,true))
    }

    @Test fun unknownLengthUsesWildcardUntilFinalChunk() {
        assertEquals("bytes 0-4194303/*",driveUploadContentRange(0,4_194_304,0,false))
        assertEquals("bytes 4194304-4194310/4194311",driveUploadContentRange(4_194_304,4_194_311,0,true))
    }

    @Test fun uploading280MiBUsesSeventy4MiBChunksWithoutGaps() {
        val total=280L*1024*1024;val chunk=4L*1024*1024;var start=0L;var count=0
        while(start<total){val end=minOf(start+chunk,total);val range=driveUploadContentRange(start,end,total,end==total);assertEquals("bytes $start-${end-1}/$total",range);start=end;count++}
        assertEquals(70,count)
    }

    @Test fun missingRangeMeansNoBytesAcceptedAndCanRetrySameChunk() {
        assertEquals(0L,driveAcknowledgedOffset(null))
        assertEquals(ChunkProgress.NONE,uploadChunkProgress(0,0,4_194_304))
        assertEquals(ChunkProgress.ADVANCED,uploadChunkProgress(1_048_576,0,4_194_304))
        assertThrows(IOException::class.java){uploadChunkProgress(4_194_305,0,4_194_304)}
    }

    @Test fun onlyMissingOrInaccessibleWakeStatusFilesAreRecreated() {
        assertTrue(isStaleWakeStatusFailure("notFound"))
        assertTrue(isStaleWakeStatusFailure("insufficientFilePermissions"))
        assertTrue(isStaleWakeStatusFailure("appNotAuthorizedToFile"))
        assertFalse(isStaleWakeStatusFailure("storageQuotaExceeded"))
        assertFalse(isStaleWakeStatusFailure("rateLimitExceeded"))
    }
}
