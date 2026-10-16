package com.polestar4.drivefilemanager

object DashcamIdentity {
    fun categoryKey(category: DashcamApi.Category): String = "${category.type}\u0000${category.path.trim('/')}"

    /** Prefer the vehicle path so same-name clips from different cameras survive reconciliation. */
    fun key(media: DashcamApi.Media): String {
        val path = media.url.substringBefore('?').trim().trimEnd('/')
        return if (path.isNotBlank()) "${media.type}\u0000$path" else "${media.type}\u0000${media.name}"
    }
}
