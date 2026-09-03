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

    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()

    private val segmenter = SubjectSegmentation.getClient(options)

    /**
     * Processes a bitmap and returns the segmented foreground bitmap.
     */
    suspend fun segment(bitmap: Bitmap): Bitmap? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = segmenter.process(image).await()
            result.foregroundBitmap
        } catch (e: Exception) {
            null
        }
    }
}
