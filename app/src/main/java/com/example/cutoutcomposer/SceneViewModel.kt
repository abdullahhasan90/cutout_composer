package com.example.cutoutcomposer

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cutoutcomposer.ml.SubjectSegmenter
import com.example.cutoutcomposer.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel responsible for managing the transformation state of the cutout.
 */
class SceneViewModel : ViewModel() {
    private val _state = MutableStateFlow(SceneState())
    val state: StateFlow<SceneState> = _state.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val subjectSegmenter = SubjectSegmenter()
    
    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        isAntiAlias = true
    }
    
    private val eraserPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    /**
     * Loads a room image from a URI and pre-segments it for occlusion.
     */
    fun setRoomImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapUtils.loadAndDownsample(context, uri)
                }
                if (bitmap != null) {
                    val subjects = subjectSegmenter.segmentSubjects(bitmap)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Room ready: Found ${subjects.size} objects.", Toast.LENGTH_SHORT).show()
                    }
                    
                    _state.update { currentState ->
                        currentState.copy(
                            room = bitmap,
                            fgMask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888),
                            roomSubjects = subjects,
                            isBrushMode = false,
                            maskUpdateCount = currentState.maskUpdateCount + 1,
                            showDebugSubjects = subjects.isNotEmpty()
                        ) 
                    }

                    // Hide debug highlight after 3 seconds
                    if (subjects.isNotEmpty()) {
                        launch {
                            delay(3000)
                            _state.update { it.copy(showDebugSubjects = false) }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading room: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Loads an object image, segments it, and adds it as a cutout.
     */
    fun setObjectImage(context: Context, uri: Uri, onFail: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Load the raw photo
                val fullBitmap = withContext(Dispatchers.IO) {
                    BitmapUtils.loadAndDownsample(context, uri)
                }
                
                if (fullBitmap != null) {
                    // 2. Segment the object
                    val segmented = subjectSegmenter.segmentForeground(fullBitmap)
                    
                    // 3. Immediately recycle the full photo to save memory
                    fullBitmap.recycle()
                    
                    if (segmented != null) {
                        // 4. Apply feathering
                        val feathered = withContext(Dispatchers.Default) {
                            BitmapUtils.applyAlphaBlur(segmented)
                        }
                        
                        _state.update { currentState ->
                            currentState.copy(
                                cutout = feathered, 
                                offset = Offset.Zero, 
                                scale = 1f, 
                                rotation = 0f
                            ) 
                        }
                    } else {
                        withContext(Dispatchers.Main) { onFail() }
                    }
                } else {
                    withContext(Dispatchers.Main) { onFail() }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Processing error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                onFail()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Updates the current transformation state.
     */
    fun updateTransform(offsetDelta: Offset, scaleFactor: Float, rotationDelta: Float) {
        if (_state.value.isBrushMode) return
        _state.update { currentState ->
            currentState.copy(
                offset = currentState.offset + offsetDelta,
                scale = (currentState.scale * scaleFactor).coerceIn(0.1f, 10f),
                rotation = (currentState.rotation + rotationDelta) % 360f
            )
        }
    }

    /**
     * Toggles brush mode on/off.
     */
    fun toggleBrushMode() {
        _state.update { it.copy(isBrushMode = !it.isBrushMode) }
    }

    /**
     * Updates brush settings.
     */
    fun updateBrushSettings(radius: Float, isEraser: Boolean) {
        _state.update { it.copy(brushRadius = radius, isEraser = isEraser) }
    }

    /**
     * Clears the foreground mask.
     */
    fun clearMask() {
        val state = _state.value
        val mask = state.fgMask ?: return
        mask.eraseColor(Color.TRANSPARENT)
        _state.update { it.copy(maskUpdateCount = it.maskUpdateCount + 1) }
    }

    /**
     * Paints on the foreground mask at the given screen coordinates.
     */
    fun paintAt(screenOffset: Offset, canvasSize: Size) {
        val state = _state.value
        val room = state.room ?: return
        val mask = state.fgMask ?: return

        val scaleX = room.width.toFloat() / canvasSize.width
        val scaleY = room.height.toFloat() / canvasSize.height
        val bitmapX = screenOffset.x * scaleX
        val bitmapY = screenOffset.y * scaleY

        viewModelScope.launch(Dispatchers.Default) {
            val canvas = Canvas(mask)
            val paint = if (state.isEraser) eraserPaint else maskPaint
            
            // Smart Snapping Logic
            var snapped = false
            if (!state.isEraser) {
                for (subject in state.roomSubjects) {
                    if (isTapInSubject(bitmapX, bitmapY, subject)) {
                        canvas.drawBitmap(subject.bitmap, subject.x, subject.y, maskPaint)
                        snapped = true
                        break 
                    }
                }
            }

            // Manual Brush Fallback
            if (!snapped) {
                canvas.drawCircle(bitmapX, bitmapY, state.brushRadius * scaleX, paint)
            }

            _state.update { it.copy(maskUpdateCount = it.maskUpdateCount + 1) }
        }
    }

    private fun isTapInSubject(x: Float, y: Float, subject: SubjectSegmenter.SubjectMask): Boolean {
        if (x < subject.x || y < subject.y || x >= subject.x + subject.bitmap.width || y >= subject.y + subject.bitmap.height) {
            return false
        }
        val radius = 10 // Checking a 21x21 area for better snapping feel
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                val checkX = (x + dx - subject.x).toInt()
                val checkY = (y + dy - subject.y).toInt()
                if (checkX >= 0 && checkY >= 0 && checkX < subject.bitmap.width && checkY < subject.bitmap.height) {
                    val pixel = subject.bitmap.getPixel(checkX, checkY)
                    if (Color.alpha(pixel) > 100) return true
                }
            }
        }
        return false
    }

    /**
     * Exports the current scene to the gallery.
     */
    fun exportResult(context: Context, onComplete: (Uri?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentState = _state.value
                val room = currentState.room
                val cutout = currentState.cutout
                val mask = currentState.fgMask
                
                if (room != null) {
                    val resultUri = withContext(Dispatchers.IO) {
                        val output = Bitmap.createBitmap(room.width, room.height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(output)
                        
                        // Layer 0: Background
                        canvas.drawBitmap(room, 0f, 0f, null)
                        
                        // Layer 1: Cutout
                        if (cutout != null) {
                            val matrix = Matrix()
                            val pivotX = cutout.width / 2f
                            val pivotY = cutout.height / 2f
                            matrix.postScale(currentState.scale, currentState.scale, pivotX, pivotY)
                            matrix.postRotate(currentState.rotation, pivotX, pivotY)
                            matrix.postTranslate(currentState.offset.x, currentState.offset.y)
                            canvas.drawBitmap(cutout, matrix, null)
                        }

                        // Layer 2: Foreground (Occlusion)
                        if (mask != null) {
                            val paint = Paint().apply {
                                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                            }
                            val fgLayer = Bitmap.createBitmap(room.width, room.height, Bitmap.Config.ARGB_8888)
                            val fgCanvas = Canvas(fgLayer)
                            fgCanvas.drawBitmap(room, 0f, 0f, null)
                            fgCanvas.drawBitmap(mask, 0f, 0f, paint)
                            
                            canvas.drawBitmap(fgLayer, 0f, 0f, null)
                            fgLayer.recycle()
                        }
                        
                        val uri = BitmapUtils.saveToGallery(context, output, "CutoutComposer_${System.currentTimeMillis()}")
                        output.recycle()
                        uri
                    }
                    onComplete(resultUri)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                onComplete(null)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
