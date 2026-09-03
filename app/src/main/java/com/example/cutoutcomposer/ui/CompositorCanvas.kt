package com.example.cutoutcomposer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.cutoutcomposer.SceneViewModel

/**
 * A custom Canvas that draws the room and the cutout object,
 * and handles transformations or painting via gestures.
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
            .pointerInput(state.isBrushMode) {
                if (state.isBrushMode) {
                    // Combine tap and drag to avoid gesture cancellation
                    detectDragGestures(
                        onDragStart = { offset -> viewModel.paintAt(offset, size.toSize()) },
                        onDrag = { change, _ -> 
                            viewModel.paintAt(change.position, size.toSize())
                            change.consume()
                        }
                    )
                } else {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        viewModel.updateTransform(pan, zoom, rotation)
                    }
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

        // Layer 2: Foreground Occlusion
        state.room?.let { roomBitmap ->
            state.fgMask?.let { maskBitmap ->
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(size.toRect(), Paint())
                    
                    drawImage(
                        image = maskBitmap.asImageBitmap(),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                    
                    drawImage(
                        image = roomBitmap.asImageBitmap(),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        blendMode = BlendMode.SrcIn
                    )
                    
                    canvas.restore()
                }
            }
        }

        // Debug Layer: Highlight detected subjects for 3 seconds
        if (state.showDebugSubjects) {
            state.roomSubjects.forEach { subject ->
                val scaleX = size.width / (state.room?.width ?: 1)
                val scaleY = size.height / (state.room?.height ?: 1)
                
                drawImage(
                    image = subject.bitmap.asImageBitmap(),
                    dstOffset = IntOffset(
                        (subject.x * scaleX).toInt(),
                        (subject.y * scaleY).toInt()
                    ),
                    dstSize = IntSize(
                        (subject.bitmap.width * scaleX).toInt(),
                        (subject.bitmap.height * scaleY).toInt()
                    ),
                    alpha = 0.5f,
                    blendMode = BlendMode.Screen
                )
            }
        }
        
        // Force redraw on mask update by reading maskUpdateCount in the draw scope
        @Suppress("UNUSED_VARIABLE")
        val forceRedraw = state.maskUpdateCount
    }
}

private fun IntSize.toSize() = Size(width.toFloat(), height.toFloat())
