package com.example.cutoutcomposer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cutoutcomposer.ml.SubjectSegmenter
import com.example.cutoutcomposer.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
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

    /**
     * Initializes the scene with bitmaps.
     */
    fun setImages(room: Bitmap, cutout: Bitmap) {
        _state.update { it.copy(room = room, cutout = cutout) }
    }

    /**
     * Loads a room image from a URI.
     */
    fun setRoomImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            val bitmap = withContext(Dispatchers.IO) {
                BitmapUtils.loadAndDownsample(context, uri)
            }
            _state.update { it.copy(room = bitmap) }
            _isLoading.value = false
        }
    }

    /**
     * Loads an object image, segments it, and adds it as a cutout.
     */
    fun setObjectImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            val fullBitmap = withContext(Dispatchers.IO) {
                BitmapUtils.loadAndDownsample(context, uri)
            }
            
            if (fullBitmap != null) {
                val segmented = subjectSegmenter.segment(fullBitmap)
                if (segmented != null) {
                    val feathered = withContext(Dispatchers.Default) {
                        BitmapUtils.applyAlphaBlur(segmented)
                    }
                    _state.update { it.copy(cutout = feathered, offset = Offset.Zero, scale = 1f, rotation = 0f) }
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Updates the current transformation state.
     */
    fun updateTransform(offsetDelta: Offset, scaleFactor: Float, rotationDelta: Float) {
        _state.update { currentState ->
            currentState.copy(
                offset = currentState.offset + offsetDelta,
                scale = (currentState.scale * scaleFactor).coerceIn(0.1f, 10f),
                rotation = (currentState.rotation + rotationDelta) % 360f
            )
        }
    }

    /**
     * Exports the current scene to the gallery.
     */
    fun exportResult(context: Context, onComplete: (Uri?) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val currentState = _state.value
            val room = currentState.room
            val cutout = currentState.cutout
            
            if (room != null) {
                val resultUri = withContext(Dispatchers.IO) {
                    val output = Bitmap.createBitmap(room.width, room.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)
                    
                    // Layer 0
                    canvas.drawBitmap(room, 0f, 0f, null)
                    
                    // Layer 1
                    if (cutout != null) {
                        val matrix = Matrix()
                        val pivotX = cutout.width / 2f
                        val pivotY = cutout.height / 2f
                        
                        // Order: Scale -> Rotate -> Translate
                        matrix.postScale(currentState.scale, currentState.scale, pivotX, pivotY)
                        matrix.postRotate(currentState.rotation, pivotX, pivotY)
                        matrix.postTranslate(currentState.offset.x, currentState.offset.y)
                        
                        canvas.drawBitmap(cutout, matrix, null)
                    }
                    
                    BitmapUtils.saveToGallery(context, output, "CutoutComposer_${System.currentTimeMillis()}")
                }
                onComplete(resultUri)
            }
            _isLoading.value = false
        }
    }
}
