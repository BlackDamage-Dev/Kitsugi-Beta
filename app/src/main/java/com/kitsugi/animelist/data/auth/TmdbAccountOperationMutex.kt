package com.kitsugi.animelist.data.auth

import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide ordering between complete account snapshots and native account writes.
 * Without this shared lock, a snapshot started before a user write could finish afterwards
 * and merge stale remote state over the newer local intent.
 */
internal object TmdbAccountOperationMutex {
    val lock = Mutex()
}
