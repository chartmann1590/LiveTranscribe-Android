package com.charles.livecaptionn

import com.charles.livecaptionn.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun fromCode_en_returnsEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
    }

    @Test
    fun fromCode_vi_returnsVietnamese() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromCode("vi"))
    }

    @Test
    fun fromCode_unknown_defaultsToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("fr"))
    }

    @Test
    fun fromCode_empty_defaultsToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode(""))
    }

    @Test
    fun code_values_areCorrect() {
        assertEquals("en", AppLanguage.ENGLISH.code)
        assertEquals("vi", AppLanguage.VIETNAMESE.code)
    }

    @Test
    fun label_values_areCorrect() {
        assertEquals("English", AppLanguage.ENGLISH.label)
        assertEquals("Vietnamese", AppLanguage.VIETNAMESE.label)
    }
}
