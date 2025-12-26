/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.models

import java.io.File

data class Lut(
    val id: String,
    val name: String,
    val file: File
)
