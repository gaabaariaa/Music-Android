package com.gaabaariaa.music
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
class MainActivity:ComponentActivity(){
 override fun onCreate(state:Bundle?){super.onCreate(state);setContent{MusicApp()}}
}
@Composable private fun MusicApp(){MaterialTheme{Surface{Text("Music")}}}
