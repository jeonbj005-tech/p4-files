package com.polestar4.drivefilemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicBoolean

class DvrStructureTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val category = DashcamApi.Category("normal", "상시", "normal", -1)

    @Test fun thumbnailDiskCacheSurvivesRecreationAndReplacement() {
        val directory = temporaryFolder.newFolder("thumbnails")
        val first = byteArrayOf(1, 2, 3)
        ThumbnailDiskCache(directory).write("clip", first)
        assertArrayEquals(first, ThumbnailDiskCache(directory).read("clip"))

        val replacement = byteArrayOf(4, 5, 6)
        ThumbnailDiskCache(directory).write("clip", replacement)
        assertArrayEquals(replacement, ThumbnailDiskCache(directory).read("clip"))
        assertEquals(1, directory.listFiles()?.count { it.extension == "jpg" })
    }

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

    @Test fun monthScanStopsAfterVerifiedOlderPage() {
        val requestedOffsets = mutableListOf<Int>()
        val currentMonth = (0 until 200).map { index ->
            val time = if (index == 0) 190L else if (index == 1) 180L else 90L
            DashcamApi.Media("normal", "id-$index", "clip-$index.mp4", 1, time, 0, "http://dvr/clip-$index.mp4")
        }
        val olderMonth = (0 until 200).map { index ->
            DashcamApi.Media("normal", "old-$index", "old-$index.mp4", 1, 50, 0, "http://dvr/old-$index.mp4")
        }
        val result = DvrMonthScanner.scan(100, 200, { it.dateTime }, { offset, _ ->
            requestedOffsets += offset
            if (offset == 0) currentMonth else olderMonth
        })
        assertTrue(result.complete)
        assertEquals(listOf(0, 200), requestedOffsets)
        assertEquals(listOf("clip-0.mp4", "clip-1.mp4"), result.items.map { it.name })
    }

    @Test fun completedMonthRefreshPreservesOtherMonths() {
        val oldMonth = media("NOR_20261031_235900_0000.MP4", "old", "normal")
        val stale = media("NOR_20261101_080000_0000.MP4", "stale", "normal")
        val fresh = media("NOR_20261102_090000_0000.MP4", "fresh", "normal")
        val result = DvrSnapshotReconciler.reconcileMonth(
            listOf(category to oldMonth, category to stale),
            listOf(DvrSnapshotReconciler.CategorySnapshot(DashcamIdentity.categoryKey(category), listOf(category to fresh), true)),
            100,
            200
        ) { if (it.name == stale.name || it.name == fresh.name) 150 else 50 }
        assertEquals(setOf(oldMonth.name, fresh.name), result.map { it.second.name }.toSet())
    }
}
