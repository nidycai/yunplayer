package com.yunplayer.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.yunplayer.app.ui.YunAppRoot
import com.yunplayer.app.ui.YunViewModel
import com.yunplayer.app.ui.theme.YunPlayerTheme
import com.yunplayer.app.ui.theme.colorsFor

class MainActivity : ComponentActivity() {
    private val vm: YunViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* optional toast */ }

    private val openAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) vm.importLocal(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        requestPerms()

        setContent {
            val prefs by vm.prefs.collectAsState()
            val yun = colorsFor(prefs.theme)
            YunPlayerTheme(themeId = prefs.theme) {
                Surface(Modifier = Modifier.fillMaxSize(), color = yun.bgBot) {
                    YunAppRoot(
                        vm = vm,
                        onPickLocalAudio = {
                            openAudioLauncher.launch(arrayOf("audio/*", "application/ogg"))
                        },
                    )
                }
            }
        }
    }

    private fun requestPerms() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) need += Manifest.permission.READ_MEDIA_AUDIO
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) need += Manifest.permission.POST_NOTIFICATIONS
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) need += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (need.isNotEmpty()) permissionLauncher.launch(need.toTypedArray())
    }
}
