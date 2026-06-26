package com.charles.livecaptionn

import com.charles.livecaptionn.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun parseBuildNumber_v1dot0dotX_returnsNumber() {
        assertEquals(1_000_042, UpdateChecker.parseBuildNumber("v1.0.42"))
    }

    @Test
    fun parseBuildNumber_withoutV_returnsNumber() {
        assertEquals(1_000_005, UpdateChecker.parseBuildNumber("1.0.5"))
    }

    @Test
    fun parseBuildNumber_largePatch_returnsNumber() {
        assertEquals(1_009_999, UpdateChecker.parseBuildNumber("v1.9.999"))
    }

    @Test
    fun parseBuildNumber_semver_majorMinor() {
        assertEquals(2_003_000, UpdateChecker.parseBuildNumber("v2.3.0"))
    }

    @Test
    fun parseBuildNumber_semverLarge() {
        assertEquals(12_034_056, UpdateChecker.parseBuildNumber("v12.34.56"))
    }

    @Test
    fun parseBuildNumber_oneComponent_returnsNull() {
        assertNull(UpdateChecker.parseBuildNumber("v1"))
    }

    @Test
    fun parseBuildNumber_twoComponents_returnsNull() {
        assertNull(UpdateChecker.parseBuildNumber("v1.0"))
    }

    @Test
    fun parseBuildNumber_nonNumeric_returnsNull() {
        assertNull(UpdateChecker.parseBuildNumber("v1.0.alpha"))
    }

    @Test
    fun parseBuildNumber_empty_returnsNull() {
        assertNull(UpdateChecker.parseBuildNumber(""))
    }

    @Test
    fun parseBuildNumber_garbage_returnsNull() {
        assertNull(UpdateChecker.parseBuildNumber("not-a-tag"))
    }

    @Test
    fun parseBuildNumber_suffix_parsesBaseVersion() {
        assertEquals(1_000_042, UpdateChecker.parseBuildNumber("v1.0.42-beta"))
    }

    @Test
    fun parseBuildNumber_dotSuffix_parsesBaseVersion() {
        assertEquals(1_000_042, UpdateChecker.parseBuildNumber("v1.0.42.hotfix"))
    }
}
