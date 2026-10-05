package com.gaabaariaa.music.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName="songs")
data class SongEntity(
 @PrimaryKey val mediaStoreId:Long,val title:String,val artist:String,val album:String,val albumArtist:String,val genre:String,val year:Int,val track:Int,val durationMs:Long,val sizeBytes:Long,val mimeType:String,val path:String
)