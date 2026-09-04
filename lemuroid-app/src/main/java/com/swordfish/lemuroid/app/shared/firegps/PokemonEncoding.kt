package com.swordfish.lemuroid.app.shared.firegps

object PokemonEncoding {
    private val charMap = mapOf(
        ' ' to 0x00,
        
        // Punctuation & Symbols
        '/' to 0x21, '(' to 0x22, ')' to 0x23, '+' to 0x2E,
        '&' to 0x2D, '…' to 0xB0, '“' to 0xB1, '”' to 0xB2,
        '‘' to 0xB3, '’' to 0xB4, '♂' to 0xB5, '♀' to 0xB6,
        '$' to 0xB7, ',' to 0xB8, '*' to 0xB9,
        '!' to 0xAB, '?' to 0xAC, '.' to 0xAD, '-' to 0xAE,
        ':' to 0xF0,
        
        // Numbers
        '0' to 0xA1, '1' to 0xA2, '2' to 0xA3, '3' to 0xA4, '4' to 0xA5,
        '5' to 0xA6, '6' to 0xA7, '7' to 0xA8, '8' to 0xA9, '9' to 0xAA,
        
        // Uppercase A-Z
        'A' to 0xBB, 'B' to 0xBC, 'C' to 0xBD, 'D' to 0xBE, 'E' to 0xBF,
        'F' to 0xC0, 'G' to 0xC1, 'H' to 0xC2, 'I' to 0xC3, 'J' to 0xC4,
        'K' to 0xC5, 'L' to 0xC6, 'M' to 0xC7, 'N' to 0xC8, 'O' to 0xC9,
        'P' to 0xCA, 'Q' to 0xCB, 'R' to 0xCC, 'S' to 0xCD, 'T' to 0xCE,
        'U' to 0xCF, 'V' to 0xD0, 'W' to 0xD1, 'X' to 0xD2, 'Y' to 0xD3, 'Z' to 0xD4,
        
        // Lowercase a-z
        'a' to 0xD5, 'b' to 0xD6, 'c' to 0xD7, 'd' to 0xD8, 'e' to 0xD9,
        'f' to 0xDA, 'g' to 0xDB, 'h' to 0xDC, 'i' to 0xDD, 'j' to 0xDE,
        'k' to 0xDF, 'l' to 0xE0, 'm' to 0xE1, 'n' to 0xE2, 'o' to 0xE3,
        'p' to 0xE4, 'q' to 0xE5, 'r' to 0xE6, 's' to 0xE7, 't' to 0xE8,
        'u' to 0xE9, 'v' to 0xEA, 'w' to 0xEB, 'x' to 0xEC, 'y' to 0xED, 'z' to 0xEE,
        
        // Extended Latin - Uppercase
        'À' to 0x51, 'Á' to 0x52, 'Â' to 0x53, 'Ç' to 0x54, 'È' to 0x55,
        'É' to 0x56, 'Ê' to 0x57, 'Ë' to 0x58, 'Ì' to 0x59, 'Í' to 0x5A,
        'Î' to 0x5B, 'Ï' to 0x5C, 'Ò' to 0x5D, 'Ó' to 0x5E, 'Ô' to 0x5F,
        'Œ' to 0x60, 'Ù' to 0x61, 'Ú' to 0x62, 'Û' to 0x63, 'Ñ' to 0x64,
        'ß' to 0x65, 'Ä' to 0xF1, 'Ö' to 0xF2, 'Ü' to 0xF3,
        
        // Extended Latin - Lowercase
        'à' to 0x66, 'á' to 0x67, 'â' to 0x68, 'ç' to 0x69, 'è' to 0x6A,
        'é' to 0x6B, 'ê' to 0x6C, 'ë' to 0x6D, 'ì' to 0x6E, 'í' to 0x6F,
        'î' to 0x70, 'ï' to 0x71, 'ò' to 0x72, 'ó' to 0x73, 'ô' to 0x74,
        'œ' to 0x75, 'ù' to 0x76, 'ú' to 0x77, 'û' to 0x78, 'ñ' to 0x79,
        'ä' to 0xF4, 'ö' to 0xF5, 'ü' to 0xF6

    )

    fun encode(text: String, maxLength: Int): ByteArray {
        // Substitute å/Å to a/A, makes Scandinavian text look nicer
        val processedText = text
            .replace('å', 'a')
            .replace('Å', 'A')

        val bytes = ByteArray(maxLength) { 0xFF.toByte() }
        for (i in 0 until minOf(processedText.length, maxLength - 1)) {
            val char = processedText[i]
            bytes[i] = (charMap[char] ?: 0xAC).toByte() // Default to '?' (0xAC) for unknown characters
        }
        bytes[minOf(processedText.length, maxLength - 1)] = 0xFF.toByte() // Terminator
        return bytes
    }
}
