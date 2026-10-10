@file:OptIn(ExperimentalCoroutinesApi::class)

package com.gaabaariaa.music.data.playlists

import com.gaabaariaa.music.core.util.evaluateSmartRule
import com.gaabaariaa.music.data.local.PersonalDao
import com.gaabaariaa.music.data.local.PlaylistDao
import com.gaabaariaa.music.data.local.PlaylistEntity
import com.gaabaariaa.music.data.local.PlaylistSongEntity
import com.gaabaariaa.music.data.local.SongDao
import com.gaabaariaa.music.data.toDomain
import com.gaabaariaa.music.domain.model.Playlist
import com.gaabaariaa.music.domain.model.SmartRule
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.model.decodeSmartRule
import com.gaabaariaa.music.domain.repository.PlaylistRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val dao: PlaylistDao,
    private val songs: SongDao,
    private val personal: PersonalDao
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        combine(dao.observePlaylists(), dao.observeStats()) { list, stats ->
            val byId = stats.associateBy { it.playlistId }
            list.map { entity ->
                val rule = decodeSmartRule(entity.smartRule)
                val stat = byId[entity.id]
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    smartRule = rule,
                    songCount = if (rule != null) -1 else stat?.songCount ?: 0,
                    coverSongId = if (rule != null) 0L else stat?.coverSongId ?: 0L
                )
            }
        }

    override fun observePlaylist(id: Long): Flow<Playlist?> = dao.observePlaylist(id).map { entity ->
        entity?.let { Playlist(it.id, it.name, decodeSmartRule(it.smartRule), -1, 0L) }
    }

    override fun observeSongs(playlistId: Long): Flow<List<Song>> =
        dao.observePlaylist(playlistId).flatMapLatest { entity ->
            val rule = decodeSmartRule(entity?.smartRule)
            when {
                entity == null -> flowOf(emptyList())
                rule == null -> dao.observeSongs(playlistId).map { list -> list.map { it.toDomain() } }
                else -> smartSongs(rule)
            }
        }

    private fun smartSongs(rule: SmartRule): Flow<List<Song>> =
        combine(songs.observeSongs(), personal.observePlayCounts(), personal.observeFavoriteIds()) { all, plays, favorites ->
            evaluateSmartRule(
                rule = rule,
                songs = all.map { it.toDomain() },
                plays = plays.associate { it.songId to it.plays },
                favorites = favorites.toSet(),
                nowSeconds = System.currentTimeMillis() / 1000
            )
        }.flowOn(Dispatchers.Default)

    override suspend fun createManual(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim(), smartRule = "", createdAt = System.currentTimeMillis()))

    override suspend fun createSmart(name: String, rule: SmartRule): Long =
        dao.insertPlaylist(
            PlaylistEntity(name = name.trim(), smartRule = rule.encode(), createdAt = System.currentTimeMillis())
        )

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    override suspend fun delete(id: Long) {
        dao.deleteAllSongs(id)
        dao.deletePlaylist(id)
    }

    override suspend fun addSongs(playlistId: Long, songIds: List<Long>): Int {
        val existing = dao.songIds(playlistId).toSet()
        val fresh = songIds.distinct().filter { it !in existing }
        if (fresh.isEmpty()) return 0
        val start = dao.maxPosition(playlistId) + 1
        val now = System.currentTimeMillis()
        dao.insertSongs(fresh.mapIndexed { i, id -> PlaylistSongEntity(playlistId, id, start + i, now) })
        return fresh.size
    }

    override suspend fun removeSong(playlistId: Long, songId: Long) = dao.removeSong(playlistId, songId)

    override suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>) {
        orderedSongIds.forEachIndexed { index, songId -> dao.setPosition(playlistId, songId, index) }
    }
}
