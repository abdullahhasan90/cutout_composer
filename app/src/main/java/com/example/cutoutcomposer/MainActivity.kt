package com.example.cutoutcomposer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cutoutcomposer.ui.BrushControls
import com.example.cutoutcomposer.ui.CompositorCanvas
import com.example.cutoutcomposer.ui.ImageActionButtons
import com.example.cutoutcomposer.ui.theme.CutoutComposerTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SceneViewModel = viewModel()
            val state by viewModel.state.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()
            
            // Use rememberSaveable to survive activity recreation (e.g., during camera trip)
            var tempUriString by rememberSaveable { mutableStateOf<String?>(null) }
            val tempUri = tempUriString?.let { Uri.parse(it) }
            
            var cameraAction by rememberSaveable { mutableStateOf(CameraTarget.NONE) }

            // Launchers
            val galleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                uri?.let {
                    if (cameraAction == CameraTarget.ROOM) viewModel.setRoomImage(this@MainActivity, it)
                    else viewModel.setObjectImage(this@MainActivity, it) {
                        Toast.makeText(this@MainActivity, "Could not extract object. Try a clearer photo.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            val cameraLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicture()
            ) { success ->
                if (success) {
                    tempUri?.let {
                        if (cameraAction == CameraTarget.ROOM) viewModel.setRoomImage(this@MainActivity, it)
                        else viewModel.setObjectImage(this@MainActivity, it) {
                            Toast.makeText(this@MainActivity, "Could not extract object. Try a clearer photo.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    val uri = createImageUri()
                    tempUriString = uri.toString()
                    cameraLauncher.launch(uri)
                }
            }

            fun handleCameraAction(target: CameraTarget) {
                cameraAction = target
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    val uri = createImageUri()
                    tempUriString = uri.toString()
                    cameraLauncher.launch(uri)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            CutoutComposerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        CompositorCanvas(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (!state.isBrushMode) {
                            ImageActionButtons(
                                onRoomGallery = {
                                    cameraAction = CameraTarget.ROOM
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                onRoomCamera = { handleCameraAction(CameraTarget.ROOM) },
                                onObjectGallery = {
                                    cameraAction = CameraTarget.OBJECT
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                onObjectCamera = { handleCameraAction(CameraTarget.OBJECT) },
                                onBrushToggle = { viewModel.toggleBrushMode() },
                                onExport = {
                                    viewModel.exportResult(this@MainActivity) { uri ->
                                        val msg = if (uri != null) "Saved to Gallery!" else "Failed to save."
                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        } else {
                            BrushControls(
                                state = state,
                                onRadiusChange = { viewModel.updateBrushSettings(it, state.isEraser) },
                                onToggleEraser = { viewModel.updateBrushSettings(state.brushRadius, it) },
                                onClearMask = { viewModel.clearMask() },
                                onExit = { viewModel.toggleBrushMode() },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createImageUri(): Uri {
        val imageFolder = File(filesDir, "Images")
        if (!imageFolder.exists()) imageFolder.mkdirs()
        val file = File(imageFolder, "temp_image_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(this, "com.example.cutoutcomposer.fileprovider", file)
    }

    enum class CameraTarget { NONE, ROOM, OBJECT }
}
