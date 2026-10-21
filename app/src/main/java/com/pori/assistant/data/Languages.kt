package com.pori.assistant.data

data class Lang(val tag: String, val name: String)

object Languages {
    const val AUTO = "auto"

    /** Speech languages offered in the picker (the AI itself understands far more). */
    val ALL = listOf(
        Lang("en-US", "English"), Lang("hi-IN", "Hindi"), Lang("ur-PK", "Urdu"),
        Lang("ar-SA", "Arabic"), Lang("bn-BD", "Bengali"), Lang("id-ID", "Indonesian"),
        Lang("fil-PH", "Filipino (Tagalog)"), Lang("ms-MY", "Malay"), Lang("fa-IR", "Persian"),
        Lang("tr-TR", "Turkish"), Lang("ru-RU", "Russian"), Lang("es-ES", "Spanish"),
        Lang("fr-FR", "French"), Lang("de-DE", "German"), Lang("pt-BR", "Portuguese"),
        Lang("it-IT", "Italian"), Lang("nl-NL", "Dutch"), Lang("sw-KE", "Swahili"),
        Lang("ta-IN", "Tamil"), Lang("te-IN", "Telugu"), Lang("mr-IN", "Marathi"),
        Lang("gu-IN", "Gujarati"), Lang("pa-IN", "Punjabi"), Lang("ne-NP", "Nepali"),
        Lang("si-LK", "Sinhala"), Lang("th-TH", "Thai"), Lang("vi-VN", "Vietnamese"),
        Lang("zh-CN", "Chinese"), Lang("ja-JP", "Japanese"), Lang("ko-KR", "Korean")
    )

    val picker: List<Lang> = listOf(Lang(AUTO, "Auto-detect")) + ALL

    fun detectable(): List<String> = ALL.map { it.tag }

    fun nameOf(tag: String): String =
        if (tag == AUTO) "Auto-detect" else ALL.firstOrNull { it.tag == tag }?.name ?: tag
}
