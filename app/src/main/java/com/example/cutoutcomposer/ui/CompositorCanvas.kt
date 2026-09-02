package com.example.cutoutcomposer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.example.cutoutcomposer.SceneViewModel

/**
 * A custom Canvas that draws the room and the cutout object,
 * and handles transformations via gestures.
 */
@Composable
fun CompositorCanvas(
    viewModel: SceneViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    viewModel.updateTransform(pan, zoom, rotation)
                }
            }
    ) {
        // Layer 0: Room Background
        state.room?.let { bitmap ->
            drawImage(
                image = bitmap.asImageBitmap(),
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }

        // Layer 1: Cutout Object
        state.cutout?.let { bitmap ->
            withTransform({
                translate(state.offset.x, state.offset.y)
                val pivot = Offset(bitmap.width / 2f, bitmap.height / 2f)
                rotate(state.rotation, pivot = pivot)
                scale(state.scale, state.scale, pivot = pivot)
            }) {
                drawImage(image = bitmap.asImageBitmap())
            }
        }
    }
}
