package com.swordfish.lemuroid.app.shared.firegps

import timber.log.Timber
import java.io.InputStream

object LocationFileParser {
    data class ParseResult(
        val areas: List<GpsArea>,
        val malformedLineNumbers: List<Int> = emptyList(),
    )

    /**
     * Parses a semicolon-separated file with the following format:
     * AreaID; GameAreaName; DisplayName; Latitude; Longitude; Radius
     */
    fun parse(inputStream: InputStream): ParseResult {
        val areas = mutableListOf<GpsArea>()
        val malformedLineNumbers = mutableListOf<Int>()

        inputStream.bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                val lineNumber = index + 1
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachIndexed
                
                // Ignore comments starting with #
                if (trimmed.startsWith("#")) {
                    return@forEachIndexed
                }

                val delimiter = if (trimmed.contains(";")) ";" else ","
                val parts = trimmed.split(delimiter)
                if (parts.size == 6) {
                    try {
                        areas.add(
                            GpsArea(
                                id = parts[0].trim().toInt(),
                                displayName = parts[2].trim(),
                                latitude = parts[3].trim().toDouble(),
                                longitude = parts[4].trim().toDouble(),
                                radiusMeters = parts[5].trim().toFloat()
                            )
                        )
                    } catch (e: Exception) {
                        malformedLineNumbers.add(lineNumber)
                        Timber.e(e, "FireGPS: Failed to parse line $lineNumber: $line")
                    }
                } else {
                    malformedLineNumbers.add(lineNumber)
                    Timber.w("FireGPS: Malformed line $lineNumber (wrong number of columns): $line")
                }
            }
        }
        
        return ParseResult(areas, malformedLineNumbers)
    }
}
