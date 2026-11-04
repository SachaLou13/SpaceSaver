package com.sachalou.spacesaver

enum class FileCategory(val label: String) {
    IMAGE("Photos"), VIDEO("Vidéos"), AUDIO("Audio"), DOCUMENT("Documents"), OTHER("Autres");
    companion object {
        fun fromMime(mime: String): FileCategory = when {
            mime.startsWith("image/") -> IMAGE
            mime.startsWith("video/") -> VIDEO
            mime.startsWith("audio/") -> AUDIO
            mime.startsWith("text/") || mime == "application/pdf" ||
                mime.contains("officedocument") || mime.contains("msword") ||
                mime.contains("opendocument") -> DOCUMENT
            else -> OTHER
        }
    }
}
