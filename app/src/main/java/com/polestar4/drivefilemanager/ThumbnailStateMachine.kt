package com.polestar4.drivefilemanager

import java.util.concurrent.ConcurrentHashMap

enum class ThumbnailState { MISSING, LOADING, READY, FAILED }

class ThumbnailStateMachine {
    private val states = ConcurrentHashMap<String, ThumbnailState>()

    fun state(key: String): ThumbnailState = states[key] ?: ThumbnailState.MISSING

    fun markMissing(key: String) { states.putIfAbsent(key, ThumbnailState.MISSING) }

    fun begin(keys: Collection<String>, retryFailed: Boolean): Set<String> = keys.filter { key ->
        val current = state(key)
        (current == ThumbnailState.MISSING || (retryFailed && current == ThumbnailState.FAILED)) &&
            states.replace(key, current, ThumbnailState.LOADING).let { it || states.putIfAbsent(key, ThumbnailState.LOADING) == null }
    }.toSet()

    fun ready(key: String) { states[key] = ThumbnailState.READY }

    fun failedIfLoading(key: String) { if (states[key] == ThumbnailState.LOADING) states[key] = ThumbnailState.FAILED }

    fun clear() { states.clear() }
}
