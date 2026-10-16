package com.polestar4.drivefilemanager

/**
 * Serializes every DVR mode transition.  It deliberately knows nothing about
 * files or thumbnails, so a caller must state why it is opening a session.
 */
class DvrSessionManager(
    private val isInFileList: () -> Boolean,
    private val enterFileList: () -> Unit,
    private val leaveFileList: () -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val log: (Transition) -> Unit = {}
) {
    data class Transition(
        val timestamp: Long,
        val previous: String,
        val target: String,
        val reason: String,
        val result: String
    )

    private val lock = Any()

    fun <T> withFileList(reason: String, block: () -> T): T = synchronized(lock) {
        val alreadyInFileList = runCatching { isInFileList() }.getOrDefault(false)
        val changed = if (alreadyInFileList) {
            record("file-list", "file-list", reason, "already-in-state")
            false
        } else {
            record("normal", "file-list", reason, "request")
            try {
                enterFileList()
                record("normal", "file-list", reason, "entered")
            } catch (error: Throwable) {
                record("normal", "file-list", reason, "enter-failed")
                throw error
            }
            true
        }
        try {
            block()
        } finally {
            if (changed) {
                runCatching { leaveFileList() }
                    .onSuccess { record("file-list", "normal", reason, "left") }
                    .onFailure { record("file-list", "normal", reason, "leave-failed") }
            }
        }
    }

    private fun record(previous: String, target: String, reason: String, result: String) {
        log(Transition(now(), previous, target, reason, result))
    }
}
