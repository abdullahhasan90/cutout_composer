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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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
            val isLoading by viewModel.isLoading.collectAsState()
            
            var tempUri by remember { mutableStateOf<Uri?>(null) }
            var cameraAction by remember { mutableStateOf<CameraTarget>(CameraTarget.NONE) }

            // Launchers
            val galleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                uri?.let {
                    if (cameraAction == CameraTarget.ROOM) viewModel.setRoomImage(this, it)
                    else viewModel.setObjectImage(this, it)
                }
            }

            val cameraLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicture()
            ) { success ->
                if (success) {
                    tempUri?.let {
                        if (cameraAction == CameraTarget.ROOM) viewModel.setRoomImage(this, it)
                        else viewModel.setObjectImage(this, it)
                    }
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    val uri = createImageUri()
                    tempUri = uri
                    cameraLauncher.launch(uri)
                }
            }

            fun handleCameraAction(target: CameraTarget) {
                cameraAction = target
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    val uri = createImageUri()
                    tempUri = uri
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
                            onExport = {
                                viewModel.exportResult(this@MainActivity) { uri ->
                                    val msg = if (uri != null) "Saved to Gallery!" else "Failed to save."
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

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
