package com.gaabaariaa.music.data
import android.content.ContentResolver
import android.provider.MediaStore
class MusicRepository(private val resolver:ContentResolver,private val dao:SongDao){
 suspend fun scan():Int{
  val songs=mutableListOf<SongEntity>()
  val p=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.ALBUM_ARTIST,MediaStore.Audio.Media.YEAR,MediaStore.Audio.Media.TRACK,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.SIZE,MediaStore.Audio.Media.MIME_TYPE,MediaStore.Audio.Media.DATA)
  resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,p,MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC")?.use{c->
   val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val title=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);val artist=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);val album=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);val aa=c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST);val year=c.getColumnIndex(MediaStore.Audio.Media.YEAR);val track=c.getColumnIndex(MediaStore.Audio.Media.TRACK);val dur=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);val size=c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);val mime=c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);val data=c.getColumnIndex(MediaStore.Audio.Media.DATA)
   while(c.moveToNext())songs+=SongEntity(c.getLong(id),c.getString(title).orEmpty().ifBlank{"Unknown title"},c.getString(artist).orEmpty().ifBlank{"Unknown artist"},c.getString(album).orEmpty().ifBlank{"Unknown album"},if(aa>=0)c.getString(aa).orEmpty()else"","",if(year>=0&&!c.isNull(year))c.getInt(year)else 0,if(track>=0&&!c.isNull(track))c.getInt(track)else 0,c.getLong(dur),c.getLong(size),c.getString(mime).orEmpty(),if(data>=0)c.getString(data).orEmpty()else"")}
  }
  if(songs.isNotEmpty()){dao.upsertAll(songs);dao.deleteMissing(songs.map{it.mediaStoreId})}
  return songs.size
 }
}