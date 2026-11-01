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
    private var activeLeases = 0
    private var ownsFileListMode = false

    fun <T> withFileList(reason: String, block: () -> T): T = synchronized(lock) {
        val alreadyInFileList = runCatching { isInFileList() }.getOrDefault(false)
        val changed = if (alreadyInFileList) {
            record("file-list", "file-list", reason, "already-in-state")
            false
        } else {
            record("normal", "file-list", reason, "request")
            try {
                enterFileList()
                ownsFileListMode = true
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
                if (activeLeases == 0) leaveMode(reason)
                else record("file-list", "file-list", reason, "leave-deferred-active-playback")
            }
        }
    }

    fun acquireFileList(reason: String): AutoCloseable {
        synchronized(lock) {
            if (activeLeases == 0 && !runCatching { isInFileList() }.getOrDefault(false)) {
                record("normal", "file-list", reason, "lease-request")
                try {
                    enterFileList()
                    ownsFileListMode = true
                    record("normal", "file-list", reason, "lease-entered")
                } catch (error: Throwable) {
                    record("normal", "file-list", reason, "lease-enter-failed")
                    throw error
                }
            }
            activeLeases++
            record("file-list", "file-list", reason, "lease-opened")
        }
        var closed = false
        return AutoCloseable {
            synchronized(lock) {
                if (!closed) {
                    closed = true
                    activeLeases--
                    if (activeLeases == 0 && ownsFileListMode) leaveMode(reason)
                    else record("file-list", "file-list", reason, "lease-closed")
                }
            }
        }
    }

    private fun leaveMode(reason: String) {
        runCatching { leaveFileList() }
            .onSuccess { record("file-list", "normal", reason, "left"); ownsFileListMode = false }
            .onFailure { record("file-list", "normal", reason, "leave-failed") }
    }

    private fun record(previous: String, target: String, reason: String, result: String) {
        log(Transition(now(), previous, target, reason, result))
    }
}
