package com.example.cutoutcomposer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import kotlin.math.min

/**
 * A custom Canvas that draws the room and the cutout object.
 * Background is drawn using "Fit" scale (preserving aspect ratio) to avoid stretching.
 */
@Composable
fun CompositorCanvas(
    viewModel: SceneViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    key(state.maskUpdateCount) {
        Canvas(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(state.isBrushMode, state.room) {
                    val room = state.room ?: return@pointerInput
                    
                    // Calculate "Fit" scale and offset
                    val canvasSize = size.toSize()
                    val bitmapSize = Size(room.width.toFloat(), room.height.toFloat())
                    val scale = min(canvasSize.width / bitmapSize.width, canvasSize.height / bitmapSize.height)
                    val contentSize = bitmapSize * scale
                    val dx = (canvasSize.width - contentSize.width) / 2f
                    val dy = (canvasSize.height - contentSize.height) / 2f

                    if (state.isBrushMode) {
                        detectDragGestures(
                            onDragStart = { offset -> 
                                viewModel.paintAt(
                                    screenOffset = offset - Offset(dx, dy), 
                                    canvasSize = contentSize
                                ) 
                            },
                            onDrag = { change, _ -> 
                                viewModel.paintAt(
                                    screenOffset = change.position - Offset(dx, dy), 
                                    canvasSize = contentSize
                                )
                                change.consume()
                            }
                        )
                    } else {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            viewModel.updateTransform(
                                offsetDelta = pan, 
                                scaleFactor = zoom, 
                                rotationDelta = rotation, 
                                canvasSize = contentSize
                            )
                        }
                    }
                }
        ) {
            val room = state.room ?: return@Canvas
            val roomWidth = room.width.toFloat()
            val roomHeight = room.height.toFloat()
            
            // Calculate "Fit" scale and offset for rendering
            val canvasWidth = size.width
            val canvasHeight = size.height
            val scale = min(canvasWidth / roomWidth, canvasHeight / roomHeight)
            val drawnWidth = roomWidth * scale
            val drawnHeight = roomHeight * scale
            val dx = (canvasWidth - drawnWidth) / 2f
            val dy = (canvasHeight - drawnHeight) / 2f
            
            val contentRect = Rect(dx, dy, dx + drawnWidth, dy + drawnHeight)

            // Layer 0: Room Background (Preserving Aspect Ratio)
            drawImage(
                image = room.asImageBitmap(),
                dstOffset = IntOffset(dx.toInt(), dy.toInt()),
                dstSize = IntSize(drawnWidth.toInt(), drawnHeight.toInt())
            )

            // Layer 1: Cutout Object
            state.cutout?.let { cutoutBitmap ->
                val pivotX = cutoutBitmap.width / 2f
                val pivotY = cutoutBitmap.height / 2f
                
                // Project Bitmap-space coordinates to Screen-space (relative to drawn background)
                val screenX = dx + (state.offset.x * scale)
                val screenY = dy + (state.offset.y * scale)

                withTransform({
                    translate(screenX, screenY)
                    rotate(state.rotation, pivot = Offset.Zero)
                    scale(state.scale * scale, state.scale * scale, pivot = Offset.Zero)
                    translate(-pivotX, -pivotY)
                }) {
                    drawImage(image = cutoutBitmap.asImageBitmap())
                }
            }

            // Layer 2: Foreground Occlusion
            state.fgMask?.let { maskBitmap ->
                drawIntoCanvas { canvas ->
                    // Clip the occlusion layer to only the background area
                    canvas.saveLayer(contentRect, Paint())
                    
                    drawImage(
                        image = maskBitmap.asImageBitmap(),
                        dstOffset = IntOffset(dx.toInt(), dy.toInt()),
                        dstSize = IntSize(drawnWidth.toInt(), drawnHeight.toInt())
                    )
                    
                    drawImage(
                        image = room.asImageBitmap(),
                        dstOffset = IntOffset(dx.toInt(), dy.toInt()),
                        dstSize = IntSize(drawnWidth.toInt(), drawnHeight.toInt()),
                        blendMode = BlendMode.SrcIn
                    )
                    
                    canvas.restore()
                }
            }

            // Debug Layer: Highlight detected subjects
            if (state.showDebugSubjects) {
                state.roomSubjects.forEach { subject ->
                    drawImage(
                        image = subject.bitmap.asImageBitmap(),
                        dstOffset = IntOffset(
                            (dx + subject.x * scale).toInt(),
                            (dy + subject.y * scale).toInt()
                        ),
                        dstSize = IntSize(
                            (subject.bitmap.width * scale).toInt(),
                            (subject.bitmap.height * scale).toInt()
                        ),
                        alpha = 0.5f,
                        blendMode = BlendMode.Screen
                    )
                }
            }
        }
    }
}

private fun IntSize.toSize() = Size(width.toFloat(), height.toFloat())
