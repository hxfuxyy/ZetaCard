package io.github.hxfuxyy.zetacard.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LsposedConnection {
    private val connectedState = MutableStateFlow(false)
    private val syncState = MutableStateFlow(0)

    val connected: StateFlow<Boolean> = connectedState.asStateFlow()
    val syncRevision: StateFlow<Int> = syncState.asStateFlow()

    @JvmStatic
    fun setConnected(value: Boolean) {
        connectedState.value = value
    }

    @JvmStatic
    fun markSynced() {
        syncState.value = syncState.value + 1
    }
}
