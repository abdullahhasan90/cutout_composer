package com.example.cutoutcomposer.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

/**
 * A wrapper for ML Kit Subject Segmentation API.
 */
class SubjectSegmenter {

    // Helper to get a client for a specific mode to save memory
    private fun getClient(multiple: Boolean) = SubjectSegmentation.getClient(
        if (multiple) {
            SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(
                    SubjectSegmenterOptions.SubjectResultOptions.Builder()
                        .enableSubjectBitmap()
                        .build()
                )
                .build()
        } else {
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
        }
    )

    /**
     * Extracts only the foreground (for the Object photo).
     * Uses a clean, single-subject client to minimize memory pressure.
     */
    suspend fun segmentForeground(bitmap: Bitmap): Bitmap? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val client = getClient(multiple = false)
            val result = client.process(image).await()
            result.foregroundBitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts individual subjects (for the Room photo).
     */
    suspend fun segmentSubjects(bitmap: Bitmap): List<SubjectMask> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val client = getClient(multiple = true)
            val result = client.process(image).await()
            result.subjects.map { subject ->
                val subjectBitmap = subject.bitmap ?: return@map null
                SubjectMask(
                    bitmap = subjectBitmap,
                    x = subject.startX.toFloat(),
                    y = subject.startY.toFloat()
                )
            }.filterNotNull()
        } catch (e: Exception) {
            emptyList()
        }
    }

    data class SubjectMask(
        val bitmap: Bitmap,
        val x: Float,
        val y: Float
    )
}
