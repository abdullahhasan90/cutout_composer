package com.example.cutoutcomposer

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.example.cutoutcomposer.ml.SubjectSegmenter

/**
 * Represents the immutable state of the composition scene.
 *
 * @property room The background bitmap (the room).
 * @property fgMask The foreground occlusion mask (alpha-only, same size as room).
 * @property roomSubjects The list of detected subjects in the room for smart-snapping.
 * @property cutout The object bitmap to be transformed (the cutout).
 * @property offset The current (x, y) position of the cutout.
 * @property scale The current scale factor of the cutout.
 * @property rotation The current rotation angle in degrees.
 * @property isBrushMode Whether the user is currently painting the foreground mask.
 * @property brushRadius The radius of the painting brush.
 * @property isEraser Whether the brush is currently in eraser mode.
 */
data class SceneState(
    val room: Bitmap? = null,
    val fgMask: Bitmap? = null,
    val roomSubjects: List<SubjectSegmenter.SubjectMask> = emptyList(),
    val cutout: Bitmap? = null,
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val isBrushMode: Boolean = false,
    val brushRadius: Float = 50f,
    val isEraser: Boolean = false,
    val maskUpdateCount: Int = 0,
    val showDebugSubjects: Boolean = false
)
