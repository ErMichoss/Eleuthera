package com.tuapp.eleuthera.domain.model

enum class AudioFormat(val label: String, val extension: String, val mimeType: String) {
    /** Se recodifica a MP3: lo reproduce cualquier aparato, pero tarda más y pierde algo de calidad. */
    MP3("MP3 (compatible)", "mp3", "audio/mpeg"),
    /** Se guarda el audio AAC tal como lo sirve YouTube: más rápido y sin pérdida añadida. */
    M4A("M4A (sin recodificar)", "m4a", "audio/mp4"),
}
