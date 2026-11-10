package com.kitsugi.animelist.audit

import android.content.Context
import com.kitsugi.animelist.model.*
import com.kitsugi.animelist.data.local.*
import com.kitsugi.animelist.data.auth.ExternalListSyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

private class MemoryMediaDao(entries: List<MediaEntry> = emptyList()) : MediaEntryDao {
    val rows = entries.map { it.toEntity() }.toMutableList()
    var nextId = (rows.maxOfOrNull { it.id } ?: 0) + 1
    var replaceInvocations = 0
    override fun observeAll(): Flow<List<MediaEntryEntity>> = flowOf(rows.toList())
    override suspend fun getAll() = rows.toList()
    override suspend fun getById(id: Int) = rows.firstOrNull { it.id == id }
    override suspend fun getByMalId(malId: Int) = rows.firstOrNull { it.malId == malId }
    override suspend fun insert(entry: MediaEntryEntity): Long {
        val saved = entry.copy(id = if (entry.id == 0) nextId++ else entry.id)
        rows.removeAll { it.id == saved.id }; rows.add(saved); return saved.id.toLong()
    }
    override suspend fun insertAll(entities: List<MediaEntryEntity>) = entities.map { insert(it) }
    override suspend fun upsertAll(entities: List<MediaEntryEntity>) = insertAll(entities)
    override suspend fun update(entry: MediaEntryEntity) { rows.replaceAll { if (it.id == entry.id) entry else it } }
    override suspend fun updateAll(entities: List<MediaEntryEntity>) { entities.forEach { update(it) } }
    override suspend fun deleteById(id: Int) { rows.removeAll { it.id == id } }
    override suspend fun deleteByIds(ids: List<Int>) { rows.removeAll { it.id in ids } }
    override suspend fun deleteBySource(source: String) { rows.removeAll { it.source == source } }
    override suspend fun deleteAll() { rows.clear() }
    override suspend fun deletePendingSyncJobs(ids: List<Int>) { /* queue is owned by MemoryQueue in these checks */ }
    override suspend fun replaceAllTransaction(entities: List<MediaEntryEntity>) {
        replaceInvocations++; super.replaceAllTransaction(entities)
    }
}
private class MemoryQueue : PendingSyncDao {
    val rows = mutableListOf<PendingSyncEntity>()
    private var nextId = 1
    override suspend fun getAll() = rows.toList()
    override suspend fun insert(entity: PendingSyncEntity) { rows.add(entity.copy(id=nextId++)) }
    override suspend fun deleteById(id: Int) { rows.removeAll { it.id==id } }
    override suspend fun deleteStale(maxRetries: Int) { error("Jobs must not be purged") }
    override suspend fun incrementRetry(id: Int) { rows.replaceAll { if(it.id==id) it.copy(retryCount=it.retryCount+1) else it } }
    override suspend fun count() = rows.size
}

fun main() = runBlocking {
    var passed=0
    suspend fun test(name: String, block: suspend () -> Unit) { block(); passed++; println("PASS $name") }
    val anime=MediaEntry(1,"Title","Anime",MediaType.Anime,WatchStatus.Watching,8,3,12,source="mal",malId=1)
    val ctx=Context()
    test("smartImport retains anime and manga sharing MAL ID") {
        val dao=MemoryMediaDao(listOf(anime)); val repo=MediaEntryRepository(dao)
        repo.smartImport("mal",listOf(anime.copy(type=MediaType.Manga)),allowDelete=false)
        check(dao.rows.size==2)
    }
    test("allowDelete false does not erase same-title different-ID records") {
        val dao=MemoryMediaDao(listOf(anime,anime.copy(id=2,malId=2)))
        MediaEntryRepository(dao).smartImport("mal",listOf(anime.copy(progress=4)),allowDelete=false)
        check(dao.rows.size==2 && dao.getById(1)?.progress==4 && dao.getById(2)?.progress==3)
    }
    test("ambiguous existing duplicates fail without deleting records") {
        val dao=MemoryMediaDao(listOf(anime,anime.copy(id=2)))
        check(runCatching { MediaEntryRepository(dao).smartImport("mal",listOf(anime),false) }.isFailure)
        check(dao.rows.size==2)
    }
    test("duplicate remote IDs within a page insert once") {
        val dao=MemoryMediaDao(); MediaEntryRepository(dao).smartImport("mal",listOf(anime,anime.copy(progress=5)),false)
        check(dao.rows.size==1 && dao.rows.single().progress==5)
    }
    test("import does not mutate a different platform library") {
        val dao=MemoryMediaDao(listOf(anime.copy(source="anilist")))
        MediaEntryRepository(dao).smartImport("mal",listOf(anime),false)
        check(dao.rows.size==2 && dao.rows.any { it.source=="anilist" })
    }
    test("replace restore routes through the Room transaction method") {
        val dao=MemoryMediaDao(listOf(anime)); MediaEntryRepository(dao).replaceAll(listOf(anime.copy(malId=2)))
        check(dao.replaceInvocations==1 && dao.rows.single().malId==2)
    }
    test("latest queued intent supersedes earlier update including a delete") {
        val dao=MemoryQueue()
        PendingSyncDrainer.enqueue(dao,"UPDATE",anime)
        PendingSyncDrainer.enqueue(dao,"DELETE",anime)
        check(dao.rows.size==1 && dao.rows.single().operation=="DELETE")
    }
    test("queue does not coalesce anime and manga by MAL ID") {
        val dao=MemoryQueue()
        PendingSyncDrainer.enqueue(dao,"UPDATE",anime)
        PendingSyncDrainer.enqueue(dao,"UPDATE",anime.copy(type=MediaType.Manga))
        check(dao.rows.size==2)
    }
    test("exhausted jobs are retained") {
        val dao=MemoryQueue(); dao.insert(PendingSyncEntity(operation="UPDATE",entryJson="{}",retryCount=5))
        check(PendingSyncDrainer.drain(ctx,dao)==0 && dao.rows.size==1)
    }
    test("no-target no-op does not acknowledge pending work") {
        val dao=MemoryQueue(); PendingSyncDrainer.enqueue(dao,"UPDATE",anime)
        ExternalListSyncManager.handler={ ExternalListSyncManager.SyncResult(emptyList()) }
        check(PendingSyncDrainer.drain(ctx,dao)==0 && dao.rows.size==1)
    }
    test("queued payload roundtrips notes timestamps and provider IDs") {
        val entry=anime.copy(notes="offline",updatedAt=123,malListId=9999999999L,simklId=42,titleJapanese="日本語")
        val dao=MemoryQueue(); PendingSyncDrainer.enqueue(dao,"UPDATE",entry)
        ExternalListSyncManager.handler={ check(it==entry); ExternalListSyncManager.SyncResult(listOf("confirmed")) }
        check(PendingSyncDrainer.drain(ctx,dao)==1 && dao.rows.isEmpty())
    }
    test("failed remote write remains queued") {
        val dao=MemoryQueue(); PendingSyncDrainer.enqueue(dao,"DELETE",anime)
        ExternalListSyncManager.handler={ ExternalListSyncManager.SyncResult(emptyList(),listOf("HTTP 500")) }
        check(PendingSyncDrainer.drain(ctx,dao)==0 && dao.rows.single().retryCount==1)
    }
    test("cancellation preserves job and releases drain lock") {
        val dao=MemoryQueue(); PendingSyncDrainer.enqueue(dao,"UPDATE",anime)
        ExternalListSyncManager.handler={ throw CancellationException("test") }
        check(runCatching { PendingSyncDrainer.drain(ctx,dao) }.exceptionOrNull() is CancellationException)
        check(dao.rows.single().retryCount==0)
        ExternalListSyncManager.handler={ ExternalListSyncManager.SyncResult(listOf("confirmed")) }
        check(PendingSyncDrainer.drain(ctx,dao)==1)
    }
    test("concurrent drains do not duplicate the same write") {
        val dao=MemoryQueue(); PendingSyncDrainer.enqueue(dao,"UPDATE",anime)
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); var calls=0
        ExternalListSyncManager.handler={ calls++; entered.complete(Unit); release.await(); ExternalListSyncManager.SyncResult(listOf("confirmed")) }
        val first=async { PendingSyncDrainer.drain(ctx,dao) }; entered.await()
        check(PendingSyncDrainer.drain(ctx,dao)==0)
        release.complete(Unit); check(first.await()==1 && calls==1)
    }
    println("$passed repository/queue checks passed (in-memory DAO + fake network; not SQLite/device tests)")
}
