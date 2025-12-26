/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.utils

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LutUtils {
    data class Lut3D(val size: Int, val data: ByteBuffer)

    fun loadCubeLut(file: File): Lut3D? {
        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                var size = -1
                val values = ArrayList<Float>()

                while (reader.readLine().also { line = it } != null) {
                    val l = line!!.trim()
                    if (l.isEmpty() || l.startsWith("#")) continue

                    if (l.startsWith("LUT_3D_SIZE")) {
                        val parts = l.split("\\s+".toRegex())
                        if (parts.size >= 2) {
                            size = parts[1].toInt()
                        }
                        continue
                    }
                    
                    if (l.startsWith("TITLE") || l.startsWith("DOMAIN_")) continue

                    val parts = l.split("\\s+".toRegex())
                    if (parts.size == 3) {
                         try {
                            values.add(parts[0].toFloat())
                            values.add(parts[1].toFloat())
                            values.add(parts[2].toFloat())
                        } catch (e: NumberFormatException) {
                            // Ignore malformed lines
                        }
                    }
                }

                if (size > 0 && values.size == size * size * size * 3) {
                     val buffer = ByteBuffer.allocateDirect(values.size * 4)
                        .order(ByteOrder.nativeOrder())
                     values.forEach { buffer.putFloat(it) }
                     buffer.position(0)
                     return Lut3D(size, buffer)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
