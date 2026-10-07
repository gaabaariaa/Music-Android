package com.gaabaariaa.music.data.personal

import com.gaabaariaa.music.data.local.FavoriteEntity
import com.gaabaariaa.music.data.local.PersonalDao
import com.gaabaariaa.music.data.local.PlayEntity
import com.gaabaariaa.music.data.toDomain
import com.gaabaariaa.music.domain.model.Song
import com.gaabaariaa.music.domain.repository.PersonalRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PersonalRepositoryImpl @Inject constructor(private val dao: PersonalDao) : PersonalRepository {

    override fun observeFavoriteIds(): Flow<Set<Long>> = dao.observeFavoriteIds().map { it.toSet() }

    override suspend fun toggleFavorite(songId: Long) {
        if (dao.favoriteCount(songId) > 0) dao.removeFavorite(songId)
        else dao.addFavorite(FavoriteEntity(songId, System.currentTimeMillis()))
    }

    override suspend fun recordPlay(songId: Long) {
        dao.addPlay(PlayEntity(songId = songId, playedAt = System.currentTimeMillis()))
    }

    override fun observeRecentlyAdded(limit: Int): Flow<List<Song>> = dao.observeRecentlyAdded(limit).songs()
    override fun observeRecentlyPlayed(limit: Int): Flow<List<Song>> = dao.observeRecentlyPlayed(limit).songs()
    override fun observeMostPlayed(limit: Int): Flow<List<Song>> = dao.observeMostPlayed(limit).songs()
    override fun observeFavorites(limit: Int): Flow<List<Song>> = dao.observeFavorites(limit).songs()
    override fun observeRecentlyDownloaded(limit: Int): Flow<List<Song>> = dao.observeRecentlyDownloaded(limit).songs()

    private fun Flow<List<com.gaabaariaa.music.data.local.SongEntity>>.songs(): Flow<List<Song>> =
        map { list -> list.map { it.toDomain() } }
}
