/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.utils

import androidx.exifinterface.media.ExifInterface
import org.lineageos.aperture.models.Rotation
import org.lineageos.aperture.models.Transform
import java.io.InputStream

class ExifUtils {
    companion object {
        private fun getOrientation(inputStream: InputStream): Int {
            inputStream.mark(Int.MAX_VALUE)
            val orientation =
                ExifInterface(inputStream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
            inputStream.reset()
            return orientation
        }

        private fun orientationToTransform(exifOrientation: Int): Transform {
            return when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> Transform(Rotation.ROTATION_90, false)
                ExifInterface.ORIENTATION_ROTATE_180 -> Transform(Rotation.ROTATION_180, false)
                ExifInterface.ORIENTATION_ROTATE_270 -> Transform(Rotation.ROTATION_270, false)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Transform(Rotation.ROTATION_0, true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> Transform(Rotation.ROTATION_180, true)
                ExifInterface.ORIENTATION_TRANSPOSE -> Transform(Rotation.ROTATION_270, true)
                ExifInterface.ORIENTATION_TRANSVERSE -> Transform(Rotation.ROTATION_90, true)
                else -> Transform.DEFAULT
            }
        }

        fun getTransform(inputStream: InputStream): Transform {
            return orientationToTransform(getOrientation(inputStream))
        }
    }
}
