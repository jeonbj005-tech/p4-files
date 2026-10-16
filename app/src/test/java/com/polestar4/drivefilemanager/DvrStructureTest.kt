package com.polestar4.drivefilemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class DvrStructureTest {
    private val category = DashcamApi.Category("normal", "상시", "normal", -1)

    private fun media(name: String, id: String, path: String, type: String = "normal") =
        DashcamApi.Media(type, id, name, 100, 1, 1, "http://198.18.37.20/$path/$name")

    private fun snapshot(items: List<DashcamApi.Media>, complete: Boolean) =
        DvrSnapshotReconciler.CategorySnapshot(DashcamIdentity.categoryKey(category), items.map { category to it }, complete)

    @Test fun samePathIdChangeDoesNotDuplicate() {
        val old = media("NOR_001.MP4", "old-id", "normal")
        val new = media("NOR_001.MP4", "new-id", "normal")
        val result = DvrSnapshotReconciler.reconcile(listOf(category to old), listOf(snapshot(listOf(new), true)))
        assertEquals(1, result.size)
        assertEquals("new-id", result.single().second.id)
    }

    @Test fun sameNameDifferentPathIsPreserved() {
        val front = media("NOR_001.MP4", "front", "front")
        val rear = media("NOR_001.MP4", "rear", "rear")
        val result = DvrSnapshotReconciler.reconcile(emptyList(), listOf(snapshot(listOf(front, rear), true)))
        assertEquals(2, result.size)
    }

    @Test fun completeSnapshotRemovesDeletedAndAddsNew() {
        val deleted = media("NOR_deleted.MP4", "deleted", "normal")
        val added = media("NOR_added.MP4", "added", "normal")
        val result = DvrSnapshotReconciler.reconcile(listOf(category to deleted), listOf(snapshot(listOf(added), true)))
        assertEquals(listOf("NOR_added.MP4"), result.map { it.second.name })
    }

    @Test fun incompleteSnapshotDoesNotDeleteCachedFiles() {
        val cached = media("NOR_cached.MP4", "cached", "normal")
        val newItem = media("NOR_new.MP4", "new", "normal")
        val result = DvrSnapshotReconciler.reconcile(listOf(category to cached), listOf(snapshot(listOf(newItem), false)))
        assertEquals(setOf("NOR_cached.MP4", "NOR_new.MP4"), result.map { it.second.name }.toSet())
    }

    @Test fun thousandItemsAcrossPagesRemainUnique() {
        val pages = (0 until 5).map { page ->
            (0 until 200).map { index ->
                val n = page * 200 + index
                media("NOR_${n}.MP4", "id-$n", "normal")
            }
        }
        val duplicatePage = pages[2].take(20).map { media(it.name, "changed-${it.id}", "normal") }
        val result = DvrSnapshotReconciler.reconcile(emptyList(), listOf(snapshot(pages.flatten() + duplicatePage, true)))
        assertEquals(1000, result.size)
    }

    @Test fun sessionManagerSerializesAndRecordsExplicitReason() {
        var inFileList = false
        val transitions = mutableListOf<DvrSessionManager.Transition>()
        val manager = DvrSessionManager(
            isInFileList = { inFileList },
            enterFileList = { inFileList = true },
            leaveFileList = { inFileList = false },
            now = { 123L },
            log = transitions::add
        )
        assertEquals("ok", manager.withFileList("mock-list") { "ok" })
        assertTrue(transitions.any { it.reason == "mock-list" && it.result == "entered" })
        assertTrue(transitions.any { it.reason == "mock-list" && it.result == "left" })
        assertTrue(!inFileList)
    }

    @Test fun thumbnailStatesRetryOnlyFailedFiles() {
        val states = ThumbnailStateMachine()
        states.markMissing("ok")
        states.markMissing("failed")
        val first = states.begin(listOf("ok", "failed"), retryFailed = false)
        assertEquals(setOf("ok", "failed"), first)
        states.ready("ok")
        states.failedIfLoading("failed")
        assertEquals(setOf("failed"), states.begin(listOf("ok", "failed"), retryFailed = true))
    }

    @Test fun timeoutLikeIncompletePageKeepsPreviousSnapshot() {
        val cached = media("NOR_before_timeout.MP4", "before", "normal")
        val partial = media("NOR_partial.MP4", "partial", "normal")
        val result = DvrSnapshotReconciler.reconcile(listOf(category to cached), listOf(snapshot(listOf(partial), false)))
        assertEquals(2, result.size)
    }
}
