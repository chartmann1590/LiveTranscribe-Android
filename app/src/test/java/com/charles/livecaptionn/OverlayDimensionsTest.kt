package com.charles.livecaptionn

import com.charles.livecaptionn.settings.CaptionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayDimensionsTest {

    @Test
    fun defaultOverlayWidth_is320dp() {
        assertEquals(320, CaptionSettings().overlayWidthDp)
    }

    @Test
    fun defaultOverlayHeight_is180dp() {
        assertEquals(180, CaptionSettings().overlayHeightDp)
    }

    @Test
    fun minOverlayWidth_isLessThanDefault() {
        assertTrue(CaptionSettings.MIN_OVERLAY_WIDTH_DP < CaptionSettings.DEFAULT_OVERLAY_WIDTH_DP)
    }

    @Test
    fun minOverlayHeight_isLessThanDefault() {
        assertTrue(CaptionSettings.MIN_OVERLAY_HEIGHT_DP < CaptionSettings.DEFAULT_OVERLAY_HEIGHT_DP)
    }

    @Test
    fun copy_overlayDimensions_areIndependent() {
        val original = CaptionSettings()
        val modified = original.copy(overlayWidthDp = 500, overlayHeightDp = 300)
        assertEquals(500, modified.overlayWidthDp)
        assertEquals(300, modified.overlayHeightDp)
        assertEquals(320, original.overlayWidthDp)
        assertEquals(180, original.overlayHeightDp)
    }
}
