package com.drygonkiller.wordsearch.game

enum class BoardTheme(val label: String) {
    FOREST("Forest"), OCEAN("Ocean"), SUNSET("Sunset"),
}

data class BoardPalette(val tracks: List<Int>, val hintColor: Int, val heroColor: Int)

fun boardPalette(theme: BoardTheme, dark: Boolean): BoardPalette = when (theme) {
    BoardTheme.FOREST -> BoardPalette(
        listOf(if (dark) 0xFFA6DBC0.toInt() else 0xFF265D48.toInt(), 0xFFB98B43.toInt(), 0xFF6A91B1.toInt(), 0xFFB486AD.toInt()),
        if (dark) 0xFF4E3D20.toInt() else 0xFFF6E7C6.toInt(),
        0xFF204D3D.toInt(),
    )
    BoardTheme.OCEAN -> BoardPalette(
        if (dark) listOf(0xFF82C7F1.toInt(), 0xFF73D5D6.toInt(), 0xFFADB8FF.toInt(), 0xFFB6DFED.toInt())
        else listOf(0xFF2275B7.toInt(), 0xFF3C9FA6.toInt(), 0xFF747BC8.toInt(), 0xFF579CB9.toInt()),
        if (dark) 0xFF13516D.toInt() else 0xFFC5EAFA.toInt(),
        0xFF184968.toInt(),
    )
    BoardTheme.SUNSET -> BoardPalette(
        if (dark) listOf(0xFFF8AA72.toInt(), 0xFFF298AC.toInt(), 0xFFE8A2D2.toInt(), 0xFFF2CA78.toInt())
        else listOf(0xFFBE632F.toInt(), 0xFFC9637F.toInt(), 0xFFAE6698.toInt(), 0xFFC28E38.toInt()),
        if (dark) 0xFF6B354D.toInt() else 0xFFFFD5B9.toInt(),
        0xFF79394C.toInt(),
    )
}
