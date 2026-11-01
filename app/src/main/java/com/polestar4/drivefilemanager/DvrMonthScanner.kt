package com.polestar4.drivefilemanager

object DvrMonthScanner {
    data class Result(val items: List<DashcamApi.Media>, val complete: Boolean, val scannedCount: Int)

    fun scan(
        startMillis: Long,
        endMillis: Long,
        timeOf: (DashcamApi.Media) -> Long,
        listPage: (offset: Int, count: Int) -> List<DashcamApi.Media>,
        onPage: (scannedCount: Int) -> Unit = {}
    ): Result {
        require(startMillis < endMillis)
        val items = linkedMapOf<String, DashcamApi.Media>()
        val seen = hashSetOf<String>()
        var offset = 0
        var scannedCount = 0
        var previousOldest: Long? = null
        var newestFirst = true
        var repeatedPages = 0

        while (offset <= 100_000) {
            val page = listPage(offset, 200)
            if (page.isEmpty()) return Result(items.values.toList(), true, scannedCount)
            scannedCount += page.size
            onPage(scannedCount)

            val times = page.map(timeOf)
            val pageIsNewestFirst = times.all { it > 0 } && times.zipWithNext().all { (a, b) -> a >= b }
            newestFirst = newestFirst && pageIsNewestFirst && (previousOldest == null || previousOldest!! >= times.first())
            previousOldest = times.lastOrNull()

            var newFiles = 0
            page.forEach { media ->
                if (seen.add(DashcamIdentity.key(media))) {
                    newFiles++
                    val time = timeOf(media)
                    if (time in startMillis until endMillis) items[DashcamIdentity.key(media)] = media
                }
            }
            repeatedPages = if (newFiles == 0) repeatedPages + 1 else 0
            if (repeatedPages >= 3) return Result(items.values.toList(), false, scannedCount)
            if (newestFirst && times.maxOrNull()!! < startMillis) return Result(items.values.toList(), true, scannedCount)
            if (page.size < 200) return Result(items.values.toList(), true, scannedCount)

            offset += page.size
            Thread.sleep(60)
        }
        return Result(items.values.toList(), false, scannedCount)
    }
}
