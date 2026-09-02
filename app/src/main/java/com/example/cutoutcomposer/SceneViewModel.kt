package com.example.cutoutcomposer

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel responsible for managing the transformation state of the cutout.
 */
class SceneViewModel : ViewModel() {
    private val _state = MutableStateFlow(SceneState())
    val state: StateFlow<SceneState> = _state.asStateFlow()

    /**
     * Initializes the scene with bitmaps.
     */
    fun setImages(room: Bitmap, cutout: Bitmap) {
        _state.update { it.copy(room = room, cutout = cutout) }
    }

    /**
     * Updates the current transformation state.
     *
     * @param offsetDelta The change in position to apply.
     * @param scaleFactor The multiplier for the current scale.
     * @param rotationDelta The change in rotation angle.
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
}
