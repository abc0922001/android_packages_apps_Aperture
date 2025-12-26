/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture.gl

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import org.lineageos.aperture.utils.LutUtils
import java.util.concurrent.atomic.AtomicBoolean

class LutProcessor : SurfaceProcessor {

    private val thread = HandlerThread("LutProcessorThread")
    private val handler: Handler
    private val executor: java.util.concurrent.Executor

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null

    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputTextureId = 0
    private var lutTextureId = 0
    private var lutSize = 0f

    private var program = 0
    private var uTexMatrixLoc = -1
    private var uLutSizeLoc = -1
    
    private val outputSurfaces = mutableMapOf<SurfaceOutput, EGLSurface>()
    
    // Transform matrix for the texture
    private val textureMatrix = FloatArray(16)
    
    private val isReleased = AtomicBoolean(false)
    
    private var pendingLut: LutUtils.Lut3D? = null
    private var currentLutData: LutUtils.Lut3D? = null

    init {
        thread.start()
        handler = Handler(thread.looper)
        executor = java.util.concurrent.Executor { handler.post(it) }
        
        executor.execute {
            initGl()
        }
    }

    fun setLut(lut: LutUtils.Lut3D?) {
        if (isReleased.get()) return
        executor.execute {
            pendingLut = lut
            // If we already have a GL context and program, we can try to update
            if (program != 0) {
                 updateLutTexture()
            }
        }
    }

    private fun initGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        eglConfig = configs[0]

        val ctxAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val tempSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, tempSurface, tempSurface, eglContext)

        program = GlUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uLutSizeLoc = GLES20.glGetUniformLocation(program, "uLutSize")
        
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        inputTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        
        inputSurfaceTexture = SurfaceTexture(inputTextureId)
        inputSurfaceTexture!!.setOnFrameAvailableListener({
             handler.post { drawFrame() }
        }, handler)
    }

    private fun updateLutTexture() {
        val lut = pendingLut
        if (lut != null && (currentLutData != lut)) {
             if (lutTextureId != 0) {
                 val ids = intArrayOf(lutTextureId)
                 GLES20.glDeleteTextures(1, ids, 0)
             }
             lutTextureId = GlUtils.createTexture3D(lut.data, lut.size)
             lutSize = lut.size.toFloat()
             currentLutData = lut
        } else if (lut == null && lutTextureId != 0) {
             val ids = intArrayOf(lutTextureId)
             GLES20.glDeleteTextures(1, ids, 0)
             lutTextureId = 0
             currentLutData = null
        }
    }

    override fun onInputSurface(request: SurfaceRequest) {
        if (isReleased.get()) {
            request.willNotProvideSurface()
            return
        }
        
        executor.execute {
            inputSurfaceTexture!!.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            val surface = Surface(inputSurfaceTexture)
            request.provideSurface(surface, executor) {
                surface.release()
                inputSurfaceTexture!!.release()
            }
        }
    }

    override fun onOutputSurface(output: SurfaceOutput) {
        if (isReleased.get()) {
            output.close()
            return
        }
        executor.execute {
            val surface = output.getSurface(executor) {
                executor.execute {
                    outputSurfaces.remove(output)?.let { eglSurf ->
                        EGL14.eglDestroySurface(eglDisplay, eglSurf)
                    }
                }
            }
            
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            val eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
            outputSurfaces[output] = eglSurface
        }
    }

    private fun drawFrame() {
        if (isReleased.get()) return
        
        inputSurfaceTexture!!.updateTexImage()
        inputSurfaceTexture!!.getTransformMatrix(textureMatrix)
        
        updateLutTexture()

        GLES20.glUseProgram(program)
        
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
        
        if (lutTextureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sLut"), 1)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uHasLut"), 1)
            GLES20.glUniform1f(uLutSizeLoc, lutSize)
        } else {
             GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uHasLut"), 0)
        }
        
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, textureMatrix, 0)

        val vertexBuffer = GlUtils.createFloatBuffer(FULL_RECTANGLE_COORDS)
        val textureBuffer = GlUtils.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS)
        
        val aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val aTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
        
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 8, textureBuffer)

        for ((output, eglSurf) in outputSurfaces) {
             EGL14.eglMakeCurrent(eglDisplay, eglSurf, eglSurf, eglContext)
             GLES20.glViewport(0, 0, output.size.width, output.size.height)
             GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
             EGL14.eglSwapBuffers(eglDisplay, eglSurf)
        }
    }

    fun release() {
        if (isReleased.getAndSet(true)) return
        executor.execute {
            if (program != 0) GLES20.glDeleteProgram(program)
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
            thread.quitSafely()
        }
    }
    
    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private val FULL_RECTANGLE_COORDS = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )
        private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
        
        private const val VERTEX_SHADER = """#version 300 es
            in vec4 aPosition;
            in vec4 aTextureCoord;
            out vec2 vTextureCoord;
            uniform mat4 uTexMatrix;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            precision mediump sampler3D;
            uniform samplerExternalOES sTexture;
            uniform mediump sampler3D sLut;
            uniform int uHasLut;
            uniform float uLutSize;
            in vec2 vTextureCoord;
            out vec4 fragColor;
            void main() {
                vec4 color = texture(sTexture, vTextureCoord);
                if (uHasLut == 1) {
                    vec3 scale = vec3((uLutSize - 1.0) / uLutSize);
                    vec3 offset = vec3(1.0 / (2.0 * uLutSize));
                    vec3 lutCoord = color.rgb * scale + offset;
                    vec3 lutColor = texture(sLut, lutCoord).rgb;
                    fragColor = vec4(lutColor, color.a);
                } else {
                    fragColor = color;
                }
            }
        """
    }
}
