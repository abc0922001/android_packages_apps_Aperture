/*
 * SPDX-FileCopyrightText: 2022-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.ui.views

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.load
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lineageos.aperture.R
import org.lineageos.aperture.ext.smoothRotate
import org.lineageos.aperture.models.MediaType
import org.lineageos.aperture.models.Rotation
import org.lineageos.aperture.utils.ExifUtils
import java.io.InputStream

/**
 * Image/video preview fragment
 */
class CapturePreviewLayout(context: Context, attrs: AttributeSet?) : ConstraintLayout(
    context, attrs
) {
    private var uri: Uri? = null
    private var photoInputStream: InputStream? = null
    private lateinit var mediaType: MediaType

    private var exoPlayer: ExoPlayer? = null

    private val cancelButton by lazy { findViewById<ImageButton>(R.id.cancelButton) }
    private val confirmButton by lazy { findViewById<ImageButton>(R.id.confirmButton) }
    private val imageView by lazy { findViewById<ImageView>(R.id.imageView) }
    private val videoView by lazy { findViewById<PlayerView>(R.id.videoView) }

    /**
     * input is null == canceled
     * input is not null == confirmed
     */
    internal var onChoiceCallback: (input: Any?) -> Unit = {}

    private var screenRotation = Rotation.ROTATION_0

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        setOnClickListener {
            // Prevent clicks behind the view
        }

        cancelButton.setOnClickListener {
            stopPreview()
            onChoiceCallback(null)
        }
        confirmButton.setOnClickListener {
            stopPreview()
            onChoiceCallback(uri ?: photoInputStream)
        }
    }

    internal fun updateSource(uri: Uri, mediaType: MediaType) {
        this.uri = uri
        this.photoInputStream = null
        this.mediaType = mediaType

        imageView.isVisible = mediaType == MediaType.PHOTO
        videoView.isVisible = mediaType == MediaType.VIDEO

        startPreview()
    }

    internal fun updateSource(photoInputStream: InputStream) {
        this.uri = null
        this.photoInputStream = photoInputStream
        this.mediaType = MediaType.PHOTO

        imageView.isVisible = true
        videoView.isVisible = false

        startPreview()
    }

    fun setScreenRotation(screenRotation: Rotation) {
        this.screenRotation = screenRotation

        val compensationValue = screenRotation.compensationValue.toFloat()

        cancelButton.smoothRotate(compensationValue)
        confirmButton.smoothRotate(compensationValue)
    }

    private fun startPreview() {
        assert((uri == null) != (photoInputStream == null)) {
            "Expected uri or photoInputStream, not both."
        }
        when (mediaType) {
            MediaType.PHOTO -> {
                if (uri != null) {
                    imageView.rotation = 0f
                    imageView.scaleX = 1f
                    imageView.load(uri) {
                        crossfade(true)
                    }
                } else {
                    val inputStream = photoInputStream!!
                    findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                        inputStream.mark(Int.MAX_VALUE)
                        val transform = ExifUtils.getTransform(inputStream)
                        inputStream.reset()

                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        inputStream.mark(Int.MAX_VALUE)
                        BitmapFactory.decodeStream(inputStream, null, options)
                        inputStream.reset()

                        options.inSampleSize = calculateInSampleSize(options, 2048, 2048)
                        options.inJustDecodeBounds = false

                        inputStream.mark(Int.MAX_VALUE)
                        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                        inputStream.reset()

                        withContext(Dispatchers.Main) {
                            Log.d(LOG_TAG, "Preview transform=$transform screenRotation=$screenRotation")
                            imageView.rotation =
                                transform.rotation.offset.toFloat() - screenRotation.offset
                            imageView.scaleX = if (transform.mirror) {
                                -1f
                            } else {
                                1f
                            }
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
            }

            MediaType.VIDEO -> {
                exoPlayer = ExoPlayer.Builder(context)
                    .build()
                    .also {
                        videoView.player = it

                        it.setMediaItem(MediaItem.fromUri(uri!!))

                        it.playWhenReady = true
                        it.seekTo(0)
                        it.prepare()
                    }
            }
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun stopPreview() {
        when (mediaType) {
            MediaType.PHOTO -> {}
            MediaType.VIDEO -> {
                exoPlayer?.release()
                exoPlayer = null
            }
        }
    }

    companion object {
        private const val LOG_TAG = "Aperture"
    }
}
