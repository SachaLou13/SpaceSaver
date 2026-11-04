package com.sachalou.spacesaver

import org.junit.Assert.assertEquals
import org.junit.Test

class FileCategoryTest {
    @Test fun categorizesCommonAndUnknownFiles() {
        val cases = mapOf(
            "image/heic" to FileCategory.IMAGE,
            "video/mp4" to FileCategory.VIDEO,
            "audio/flac" to FileCategory.AUDIO,
            "application/pdf" to FileCategory.DOCUMENT,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to FileCategory.DOCUMENT,
            "text/plain" to FileCategory.DOCUMENT,
            "application/zip" to FileCategory.OTHER,
            "" to FileCategory.OTHER
        )
        cases.forEach { (mime, category) -> assertEquals(mime, category, FileCategory.fromMime(mime)) }
    }
}
