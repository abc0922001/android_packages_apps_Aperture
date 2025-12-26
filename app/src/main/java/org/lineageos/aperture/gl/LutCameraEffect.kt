/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.gl

import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceProcessor
import java.util.concurrent.Executor

class LutCameraEffect(
    targets: Int,
    executor: Executor,
    surfaceProcessor: SurfaceProcessor
) : CameraEffect(
    targets,
    executor,
    surfaceProcessor,
    {}
)
