package com.charles.livecaptionn
 
import com.charles.livecaptionn.compatibility.CompatibilityTier
import com.charles.livecaptionn.compatibility.DeviceCompatibilityChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
 
class DeviceCompatibilityTest {
 
    @Test
    fun highEndDevice_evaluatesToPassed() {
        // Tensor G3 / Snapdragon 8 Gen 3 spec: 12 GB RAM, 8 cores, 64-bit, 50 GB free storage
        val (tier, reasons) = DeviceCompatibilityChecker.evaluateTier(
            totalRamMb = 12000L,
            cpuCores = 8,
            is64Bit = true,
            freeStorageMb = 50000L
        )
        assertEquals(CompatibilityTier.PASSED, tier)
        assertTrue(reasons.isEmpty())
    }
 
    @Test
    fun kindleTablet_evaluatesToBorderline() {
        // Amazon Fire HD 8 spec: 2783 MB RAM, 4 cores, 32-bit (armeabi-v7a), 2100 MB free storage
        val (tier, reasons) = DeviceCompatibilityChecker.evaluateTier(
            totalRamMb = 2783L,
            cpuCores = 4,
            is64Bit = false,
            freeStorageMb = 2100L
        )
        assertEquals(CompatibilityTier.BORDERLINE, tier)
        assertTrue(reasons.any { it.contains("2.5 GB minimum") || it.contains("4 GB+ is recommended") })
        assertTrue(reasons.any { it.contains("32-bit") })
        assertTrue(reasons.any { it.contains("4 cores") })
    }
 
    @Test
    fun lowRamDevice_evaluatesToUnsupported() {
        // Low RAM device: 2048 MB RAM (< 2560 MB min), 4 cores, 64-bit, 10000 MB storage
        val (tier, reasons) = DeviceCompatibilityChecker.evaluateTier(
            totalRamMb = 2048L,
            cpuCores = 4,
            is64Bit = true,
            freeStorageMb = 10000L
        )
        assertEquals(CompatibilityTier.UNSUPPORTED, tier)
        assertTrue(reasons.any { it.contains("Total RAM") && it.contains("below the 2.5 GB minimum") })
    }
 
    @Test
    fun lowCpuCores_evaluatesToUnsupported() {
        // Dual-core device: 4000 MB RAM, 2 cores (< 4 min), 64-bit, 5000 MB storage
        val (tier, reasons) = DeviceCompatibilityChecker.evaluateTier(
            totalRamMb = 4000L,
            cpuCores = 2,
            is64Bit = true,
            freeStorageMb = 5000L
        )
        assertEquals(CompatibilityTier.UNSUPPORTED, tier)
        assertTrue(reasons.any { it.contains("at least 4 cores are required") })
    }
 
    @Test
    fun lowStorage_evaluatesToUnsupported() {
        // Low storage: 8000 MB RAM, 8 cores, 64-bit, 100 MB free storage (< 350 MB min)
        val (tier, reasons) = DeviceCompatibilityChecker.evaluateTier(
            totalRamMb = 8000L,
            cpuCores = 8,
            is64Bit = true,
            freeStorageMb = 100L
        )
        assertEquals(CompatibilityTier.UNSUPPORTED, tier)
        assertTrue(reasons.any { it.contains("Free internal storage") && it.contains("under the 350 MB") })
    }
 
    @Test
    fun borderlineRamOnly_evaluatesToBorderline() {
        // 3000 MB RAM (between 2560 and 3840), 8 cores, 64-bit, 10000 MB storage
        val (tier, reasons) = DeviceCompatibilityChecker.evaluateTier(
            totalRamMb = 3000L,
            cpuCores = 8,
            is64Bit = true,
            freeStorageMb = 10000L
        )
        assertEquals(CompatibilityTier.BORDERLINE, tier)
        assertEquals(1, reasons.size)
        assertTrue(reasons[0].contains("4 GB+ is recommended"))
    }
 
    @Test
    fun deviceSpecs_helperProperties() {
        val (passedTier, _) = DeviceCompatibilityChecker.evaluateTier(8000L, 8, true, 5000L)
        val (unsupportedTier, _) = DeviceCompatibilityChecker.evaluateTier(1500L, 2, false, 100L)
        val (borderlineTier, _) = DeviceCompatibilityChecker.evaluateTier(3000L, 4, false, 500L)
 
        val passedSpecs = com.charles.livecaptionn.compatibility.DeviceSpecs(
            8000L, 8, true, 5000L, "Pixel 8", "Android 14", passedTier, emptyList()
        )
        assertTrue(passedSpecs.canRunOnDevice)
        assertFalse(passedSpecs.requiresWarning)
 
        val unsupportedSpecs = com.charles.livecaptionn.compatibility.DeviceSpecs(
            1500L, 2, false, 100L, "Old Phone", "Android 8", unsupportedTier, listOf("reasons")
        )
        assertFalse(unsupportedSpecs.canRunOnDevice)
        assertFalse(unsupportedSpecs.requiresWarning)
 
        val borderlineSpecs = com.charles.livecaptionn.compatibility.DeviceSpecs(
            3000L, 4, false, 500L, "Kindle", "Android 9", borderlineTier, listOf("reasons")
        )
        assertTrue(borderlineSpecs.canRunOnDevice)
        assertTrue(borderlineSpecs.requiresWarning)
    }
}
