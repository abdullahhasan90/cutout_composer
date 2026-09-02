package com.example.cutoutcomposer

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cutoutcomposer.ui.CompositorCanvas
import com.example.cutoutcomposer.ui.theme.CutoutComposerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SceneViewModel = viewModel()

            // Load hardcoded assets for Phase 0
            LaunchedEffect(Unit) {
                val roomBitmap = BitmapFactory.decodeResource(resources, R.drawable.room_bg)
                val objectBitmap = BitmapFactory.decodeResource(resources, R.drawable.`object`)
                if (roomBitmap != null && objectBitmap != null) {
                    viewModel.setImages(roomBitmap, objectBitmap)
                }
            }

            CutoutComposerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CompositorCanvas(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
