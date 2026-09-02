package com.example.cutoutcomposer

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset

/**
 * Represents the immutable state of the composition scene.
 *
 * @property room The background bitmap (the room).
 * @property cutout The object bitmap to be transformed (the cutout).
 * @property offset The current (x, y) position of the cutout.
 * @property scale The current scale factor of the cutout.
 * @property rotation The current rotation angle in degrees.
 */
data class SceneState(
    val room: Bitmap? = null,
    val cutout: Bitmap? = null,
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotation: Float = 0f
)
