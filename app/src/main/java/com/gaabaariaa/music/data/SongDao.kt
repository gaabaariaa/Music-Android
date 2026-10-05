package com.gaabaariaa.music.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface SongDao{
 @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE") fun observeSongs():Flow<List<SongEntity>>
 @Query("SELECT * FROM songs WHERE artist != '' GROUP BY artist ORDER BY artist COLLATE NOCASE") fun observeArtists():Flow<List<SongEntity>>
 @Query("SELECT * FROM songs WHERE album != '' GROUP BY album ORDER BY album COLLATE NOCASE") fun observeAlbums():Flow<List<SongEntity>>
 @Query("SELECT COUNT(*) FROM songs") fun observeCount():Flow<Int>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertAll(songs:List<SongEntity>)
 @Query("DELETE FROM songs WHERE mediaStoreId NOT IN (:ids)") suspend fun deleteMissing(ids:List<Long>)
}