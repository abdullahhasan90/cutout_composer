package com.example.cutoutcomposer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun ImageActionButtons(
    onRoomGallery: () -> Unit,
    onRoomCamera: () -> Unit,
    onObjectGallery: () -> Unit,
    onObjectCamera: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRoomOptions by remember { mutableStateOf(false) }
    var showObjectOptions by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (showRoomOptions) {
                ActionOption(Icons.Default.PhotoLibrary, "Room Gallery", onRoomGallery)
                ActionOption(Icons.Default.CameraAlt, "Room Camera", onRoomCamera)
            }
            Button(onClick = { 
                showRoomOptions = !showRoomOptions
                showObjectOptions = false
            }) {
                Text("Pick Room")
            }

            if (showObjectOptions) {
                ActionOption(Icons.Default.PhotoLibrary, "Object Gallery", onObjectGallery)
                ActionOption(Icons.Default.CameraAlt, "Object Camera", onObjectCamera)
            }
            Button(onClick = { 
                showObjectOptions = !showObjectOptions
                showRoomOptions = false
            }) {
                Text("Pick Object")
            }

            FloatingActionButton(
                onClick = onExport,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Save, contentDescription = "Export")
            }
        }
    }
}

@Composable
fun ActionOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    SmallFloatingActionButton(onClick = onClick) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
