package com.polestar4.drivefilemanager

/** Snapshot replacement is only applied for a category whose scan completed. */
object DvrSnapshotReconciler {
    data class CategorySnapshot(
        val categoryKey: String,
        val items: List<Pair<DashcamApi.Category, DashcamApi.Media>>,
        val complete: Boolean
    )

    fun reconcile(
        cached: List<Pair<DashcamApi.Category, DashcamApi.Media>>,
        snapshots: List<CategorySnapshot>
    ): List<Pair<DashcamApi.Category, DashcamApi.Media>> {
        val byCategory = cached.groupBy { DashcamIdentity.categoryKey(it.first) }.toMutableMap()
        snapshots.forEach { snapshot ->
            val incoming = unique(snapshot.items)
            byCategory[snapshot.categoryKey] = if (snapshot.complete) {
                incoming
            } else {
                unique((byCategory[snapshot.categoryKey].orEmpty() + incoming))
            }
        }
        return unique(byCategory.values.flatten())
    }

    private fun unique(items: List<Pair<DashcamApi.Category, DashcamApi.Media>>) =
        items.distinctBy { DashcamIdentity.key(it.second) }
}
