package com.fractaleye.wallpaper;

import android.content.res.AssetManager;
import android.opengl.GLES20;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Рисует фрактальный глаз: сначала сцена в текстуру пониженного разрешения, затем текстура
 * растягивается на весь экран. Владеет всеми объектами GL и удаляет их в {@link #release()}.
 * Вызывается только из потока рендера при текущем контексте EGL.
 */
final class SceneRenderer {
    private static final int MIN_TARGET_PX = 2;
    private static final int FLOAT_BYTES = 4;
    private static final int COMPONENTS_PER_VERTEX = 2;
    private static final int VERTEX_COUNT = 3;
    /** Один треугольник, целиком накрывающий экран (clip-space от -1 до 1). */
    private static final float[] FULLSCREEN_TRIANGLE = {-1f, -1f, 3f, -1f, -1f, 3f};

    // Повторно используемые буферы: в цикле кадров ничего не выделяем.
    private final float[] rotation = new float[9];
    private final int[] idScratch = new int[1];
    private FloatBuffer vertices;

    private int sceneProgram;
    private int uResolution;
    private int uPupil;
    private int uZoom;
    private int uSteps;
    private int uIters;
    private int uTime;
    private int uRotation;

    private int blitProgram;
    private int uBlitTexture;
    private int uBlitResolution;

    private int texture;
    private int framebuffer;
    private int targetWidth;
    private int targetHeight;

    /** Компилирует шейдеры из ассетов и готовит вершины. */
    void init(@NonNull AssetManager assets) throws IOException {
        String vertexSrc = GlUtils.readAsset(assets, "quad.vert");

        sceneProgram = GlUtils.buildProgram(vertexSrc, GlUtils.readAsset(assets, "shader.frag"));
        uResolution = GlUtils.uniformLocation(sceneProgram, "uResolution");
        uPupil = GlUtils.uniformLocation(sceneProgram, "uPupil");
        uZoom = GlUtils.uniformLocation(sceneProgram, "uZoom");
        uSteps = GlUtils.uniformLocation(sceneProgram, "uSteps");
        uIters = GlUtils.uniformLocation(sceneProgram, "uIters");
        uTime = GlUtils.uniformLocation(sceneProgram, "uTime");
        uRotation = GlUtils.uniformLocation(sceneProgram, "uRotation");

        blitProgram = GlUtils.buildProgram(vertexSrc, GlUtils.readAsset(assets, "blit.frag"));
        uBlitTexture = GlUtils.uniformLocation(blitProgram, "uTexture");
        uBlitResolution = GlUtils.uniformLocation(blitProgram, "uResolution");

        vertices = ByteBuffer.allocateDirect(FULLSCREEN_TRIANGLE.length * FLOAT_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertices.put(FULLSCREEN_TRIANGLE);
        vertices.position(0);
        GLES20.glEnableVertexAttribArray(GlUtils.POSITION_ATTRIB_INDEX);
        GLES20.glVertexAttribPointer(GlUtils.POSITION_ATTRIB_INDEX, COMPONENTS_PER_VERTEX,
                GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        // Dithering reduces visible 8-bit banding in the dark vignette on OLED panels.
        GLES20.glEnable(GLES20.GL_DITHER);
        GlUtils.checkError("init");
    }

    /**
     * Рисует один кадр.
     *
     * @param screenWidth ширина экрана, px
     * @param screenHeight высота экрана, px
     * @param scale доля размера экрана, в которую рисуется сцена
     * @param timeS время с начала показа, с
     * @param yawRad дополнительный поворот глаза от сдвига рабочего стола, рад
     */
    void drawFrame(int screenWidth, int screenHeight, float scale, float timeS, float yawRad) {
        ensureTarget(screenWidth, screenHeight, scale);
        drawScene(timeS, yawRad);
        blit(screenWidth, screenHeight);
        GlUtils.checkError("drawFrame");
    }

    /** Удаляет все объекты GL. Безопасно вызывать повторно и после неудачного {@link #init}. */
    void release() {
        if (framebuffer != 0) {
            idScratch[0] = framebuffer;
            GLES20.glDeleteFramebuffers(1, idScratch, 0);
            framebuffer = 0;
        }
        if (texture != 0) {
            idScratch[0] = texture;
            GLES20.glDeleteTextures(1, idScratch, 0);
            texture = 0;
        }
        if (sceneProgram != 0) {
            GLES20.glDeleteProgram(sceneProgram);
            sceneProgram = 0;
        }
        if (blitProgram != 0) {
            GLES20.glDeleteProgram(blitProgram);
            blitProgram = 0;
        }
        targetWidth = 0;
        targetHeight = 0;
        vertices = null;
        rotation[0] = rotation[4] = rotation[8] = 1f;
    }

    private void ensureTarget(int screenWidth, int screenHeight, float scale) {
        int width = Math.max(MIN_TARGET_PX, Math.round(screenWidth * scale));
        int height = Math.max(MIN_TARGET_PX, Math.round(screenHeight * scale));
        if (width == targetWidth && height == targetHeight) {
            return;
        }

        if (texture == 0) {
            GLES20.glGenTextures(1, idScratch, 0);
            texture = idScratch[0];
            GLES20.glGenFramebuffers(1, idScratch, 0);
            framebuffer = idScratch[0];
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        setTextureParam(GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        setTextureParam(GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        setTextureParam(GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        setTextureParam(GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
                != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer incomplete");
        }
        targetWidth = width;
        targetHeight = height;
        GlUtils.checkError("ensureTarget");
    }

    private static void setTextureParam(int name, int value) {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, name, value);
    }

    private void drawScene(float timeS, float yawRad) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glViewport(0, 0, targetWidth, targetHeight);
        GLES20.glUseProgram(sceneProgram);
        GLES20.glUniform2f(uResolution, targetWidth, targetHeight);
        GLES20.glUniform1f(uPupil, Tuning.PUPIL_BASE_RAD + Tuning.PUPIL_PULSE_RAD
                * (float) Math.sin(timeS * Tuning.PUPIL_PULSE_RAD_PER_S));
        GLES20.glUniform1f(uZoom, Tuning.ZOOM);
        GLES20.glUniform1f(uSteps, Tuning.STEPS);
        GLES20.glUniform1f(uIters, Tuning.ITERS);
        GLES20.glUniform1f(uTime, timeS);

        float tilt = Tuning.TILT_AMPLITUDE_RAD
                * (float) Math.sin(timeS * Tuning.TILT_RAD_PER_S);
        float pan = Tuning.PAN_AMPLITUDE_RAD
                * (float) Math.sin(timeS * Tuning.PAN_RAD_PER_S + Tuning.PAN_PHASE_RAD)
                + yawRad;
        fillRotation(tilt, pan);
        GLES20.glUniformMatrix3fv(uRotation, 1, false, rotation, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, VERTEX_COUNT);
    }

    /**
     * Заполняет матрицу поворота по столбцам, как ждёт GLSL: наклон на tilt вокруг X
     * и поворот на pan вокруг Y.
     */
    private void fillRotation(float tilt, float pan) {
        float cx = (float) Math.cos(tilt);
        float sx = (float) Math.sin(tilt);
        float cy = (float) Math.cos(pan);
        float sy = (float) Math.sin(pan);
        rotation[0] = cy;
        rotation[1] = 0f;
        rotation[2] = -sy;
        rotation[3] = sy * sx;
        rotation[4] = cx;
        rotation[5] = cy * sx;
        rotation[6] = sy * cx;
        rotation[7] = -sx;
        rotation[8] = cy * cx;
    }

    private void blit(int screenWidth, int screenHeight) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, screenWidth, screenHeight);
        GLES20.glUseProgram(blitProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(uBlitTexture, 0);
        GLES20.glUniform2f(uBlitResolution, screenWidth, screenHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, VERTEX_COUNT);
    }
}
