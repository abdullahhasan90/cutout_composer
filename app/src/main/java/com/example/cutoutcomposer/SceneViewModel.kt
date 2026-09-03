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
 * All spatial properties (offset, brushRadius) are stored in "Bitmap Pixels"
 * of the room background to ensure resolution independence.
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
                            showDebugSubjects = subjects.isNotEmpty(),
                            // Initialize offset to center of the bitmap
                            offset = Offset(bitmap.width / 2f, bitmap.height / 2f)
                        ) 
                    }

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
                val fullBitmap = withContext(Dispatchers.IO) {
                    BitmapUtils.loadAndDownsample(context, uri)
                }
                
                if (fullBitmap != null) {
                    val segmented = subjectSegmenter.segmentForeground(fullBitmap)
                    fullBitmap.recycle()
                    
                    if (segmented != null) {
                        val feathered = withContext(Dispatchers.Default) {
                            BitmapUtils.applyAlphaBlur(segmented)
                        }
                        
                        _state.update { currentState ->
                            currentState.copy(
                                cutout = feathered, 
                                // Keep existing offset if room is already loaded, otherwise center
                                offset = if (currentState.room != null) currentState.offset else Offset.Zero,
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
     * @param offsetDelta The pan delta in SCREEN pixels.
     */
    fun updateTransform(offsetDelta: Offset, scaleFactor: Float, rotationDelta: Float, canvasSize: Size) {
        val state = _state.value
        if (state.isBrushMode || state.room == null) return

        // Convert screen delta to bitmap delta
        val scaleX = state.room.width.toFloat() / canvasSize.width
        val scaleY = state.room.height.toFloat() / canvasSize.height
        val bitmapDelta = Offset(offsetDelta.x * scaleX, offsetDelta.y * scaleY)

        _state.update { currentState ->
            currentState.copy(
                offset = currentState.offset + bitmapDelta,
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
        _state.update { it.copy(fgMask = mask, maskUpdateCount = it.maskUpdateCount + 1) }
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

            if (!snapped) {
                // Brush radius is already in bitmap units now
                canvas.drawCircle(bitmapX, bitmapY, state.brushRadius, paint)
            }

            _state.update { it.copy(maskUpdateCount = it.maskUpdateCount + 1) }
        }
    }

    private fun isTapInSubject(x: Float, y: Float, subject: SubjectSegmenter.SubjectMask): Boolean {
        if (x < subject.x || y < subject.y || x >= subject.x + subject.bitmap.width || y >= subject.y + subject.bitmap.height) {
            return false
        }
        val radius = 10
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
                            
                            // Transform using Bitmap-space coordinates
                            matrix.postScale(currentState.scale, currentState.scale, pivotX, pivotY)
                            matrix.postRotate(currentState.rotation, pivotX, pivotY)
                            // Subtracting pivot ensures the object center matches the offset point
                            matrix.postTranslate(currentState.offset.x - pivotX, currentState.offset.y - pivotY)
                            
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

    override fun onCleared() {
        super.onCleared()
        val state = _state.value
        state.room?.recycle()
        state.cutout?.recycle()
        state.fgMask?.recycle()
        state.roomSubjects.forEach { it.bitmap.recycle() }
    }
}
