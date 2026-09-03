package com.example.cutoutcomposer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cutoutcomposer.SceneState

@Composable
fun BrushControls(
    state: SceneState,
    onRadiusChange: (Float) -> Unit,
    onToggleEraser: (Boolean) -> Unit,
    onClearMask: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Brush Settings", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.Clear, contentDescription = "Exit")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Radius", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.brushRadius,
                    onValueChange = onRadiusChange,
                    valueRange = 10f..200f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !state.isEraser,
                    onClick = { onToggleEraser(false) },
                    label = { Text("Brush") },
                    leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null) }
                )
                FilterChip(
                    selected = state.isEraser,
                    onClick = { onToggleEraser(true) },
                    label = { Text("Eraser") },
                    leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null) }
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClearMask,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
    }
}
