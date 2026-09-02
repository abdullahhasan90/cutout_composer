package com.example.cutoutcomposer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SceneViewModelTest {

    private lateinit var viewModel: SceneViewModel

    @Before
    fun setup() {
        viewModel = SceneViewModel()
    }

    @Test
    fun `updateTransform with offset updates state offset`() {
        val initialOffset = viewModel.state.value.offset
        val delta = Offset(10f, 20f)
        
        viewModel.updateTransform(delta, 1f, 0f)
        
        assertEquals(initialOffset + delta, viewModel.state.value.offset)
    }

    @Test
    fun `updateTransform with scale factor updates state scale`() {
        val initialScale = viewModel.state.value.scale
        val factor = 1.5f
        
        viewModel.updateTransform(Offset.Zero, factor, 0f)
        
        assertEquals(initialScale * factor, viewModel.state.value.scale, 0.001f)
    }

    @Test
    fun `updateTransform with rotation updates state rotation`() {
        val initialRotation = viewModel.state.value.rotation
        val delta = 45f
        
        viewModel.updateTransform(Offset.Zero, 1f, delta)
        
        assertEquals(initialRotation + delta, viewModel.state.value.rotation, 0.001f)
    }
}
