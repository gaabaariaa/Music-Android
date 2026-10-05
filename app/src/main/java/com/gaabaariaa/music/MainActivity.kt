package com.gaabaariaa.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.gaabaariaa.music.data.MusicDatabase
import com.gaabaariaa.music.data.MusicRepository
import com.gaabaariaa.music.data.SongEntity
import com.gaabaariaa.music.player.MusicPlayerManager
import kotlinx.coroutines.*

class MainActivity:ComponentActivity(){
 private lateinit var db:MusicDatabase
 private lateinit var player:MusicPlayerManager
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var permissionGranted by mutableStateOf(false)
 private var scanning by mutableStateOf(false)
 private var scanResult by mutableStateOf("")
 private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
  permissionGranted=granted
  if(granted)scan()
 }
 override fun onCreate(state:Bundle?){
  super.onCreate(state)
  db=Room.databaseBuilder(applicationContext,MusicDatabase::class.java,"music.db").build()
  player=MusicPlayerManager(this)
  permissionGranted=hasAudioPermission()
  setContent{MusicApp(db,player,permissionGranted,scanning,scanResult){requestAudioPermission()}}
  if(permissionGranted)scan()
 }
 private fun hasAudioPermission()=ContextCompat.checkSelfPermission(this,audioPermission())==PackageManager.PERMISSION_GRANTED
 private fun audioPermission()=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
 private fun requestAudioPermission(){permissionLauncher.launch(audioPermission())}
 private fun scan(){
  if(scanning)return
  scanning=true
  scope.launch(Dispatchers.IO){
   val count=MusicRepository(contentResolver,db.songDao()).scan()
   withContext(Dispatchers.Main){scanResult="Library scanned: $count songs";scanning=false}
  }
 }
 override fun onDestroy(){scope.cancel();player.release();db.close();super.onDestroy()}
}

@Composable
private fun MusicApp(db:MusicDatabase,player:MusicPlayerManager,permission:Boolean,scanning:Boolean,result:String,onPermission:()->Unit){
 val songs by db.songDao().observeSongs().collectAsState(emptyList())
 val artists by db.songDao().observeArtists().collectAsState(emptyList())
 val albums by db.songDao().observeAlbums().collectAsState(emptyList())
 var tab by remember{mutableStateOf(0)}
 MaterialTheme{
  Scaffold(topBar={TopAppBar(title={Text("Music Library")})}){pad->
   Column(Modifier.padding(pad).fillMaxSize()){
    if(!permission){
     Text("Music access is required to scan songs on this device.",Modifier.padding(16.dp))
     Button(onClick=onPermission,Modifier.padding(horizontal=16.dp)){Text("Allow music access")}
    }else{
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
      listOf("Songs","Artists","Albums").forEachIndexed{i,label->TextButton(onClick={tab=i}){Text(if(tab==i)"● $label" else label)}}
     }
     if(scanning)LinearProgressIndicator(Modifier.fillMaxWidth())
     if(result.isNotBlank())Text(result,Modifier.padding(16.dp))
     when(tab){0->SongList(songs){player.play(it)};1->EntityList(artists){it.artist};else->EntityList(albums){it.album}}
    }
   }
  }
 }
}

@Composable
private fun SongList(songs:List<SongEntity>,onPlay:(SongEntity)->Unit){
 if(songs.isEmpty())Text("No local music found.",Modifier.padding(16.dp))
 LazyColumn{
  items(songs,key={it.mediaStoreId}){song->
   ListItem(headlineContent={Text(song.title)},supportingContent={Text(song.artist+" • "+song.album)},modifier=Modifier.clickable{onPlay(song)})
   HorizontalDivider()
  }
 }
}

@Composable
private fun EntityList(items:List<SongEntity>,label:(SongEntity)->String){
 if(items.isEmpty())Text("Nothing found.",Modifier.padding(16.dp))
 LazyColumn{items(items,key={it.mediaStoreId}){item->ListItem(headlineContent={Text(label(item))})}}
}
