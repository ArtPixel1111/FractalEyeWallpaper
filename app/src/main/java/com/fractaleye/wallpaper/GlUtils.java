package com.fractaleye.wallpaper;

import static com.fractaleye.wallpaper.FractalWallpaperService.TAG;

import android.content.res.AssetManager;
import android.opengl.GLES20;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Вспомогательные функции OpenGL ES 2.0: шейдеры, программы, ассеты, проверка ошибок. */
final class GlUtils {
    /** Индекс атрибута aPosition во всех программах. */
    static final int POSITION_ATTRIB_INDEX = 0;

    private GlUtils() { }

    /**
     * Собирает программу из двух шейдеров. После линковки сами шейдеры удаляются.
     *
     * @throws IllegalStateException если компиляция или линковка не удалась (в тексте лог GL)
     */
    static int buildProgram(@NonNull String vertexSrc, @NonNull String fragmentSrc) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc);
        int fragmentShader;
        try {
            fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc);
        } catch (RuntimeException e) {
            GLES20.glDeleteShader(vertexShader);
            throw e;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glBindAttribLocation(program, POSITION_ATTRIB_INDEX, "aPosition");
        GLES20.glLinkProgram(program);

        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);

        // Программа уже держит скомпилированный код, шейдеры больше не нужны.
        GLES20.glDetachShader(program, vertexShader);
        GLES20.glDetachShader(program, fragmentShader);
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Program link failed: " + log);
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Shader compile failed: " + log);
        }
        return shader;
    }

    /** Место uniform-переменной. Если её нет (или шейдер её выкинул), пишет предупреждение. */
    static int uniformLocation(int program, @NonNull String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) {
            Log.w(TAG, "Uniform not found (misspelled or optimized out): " + name);
        }
        return location;
    }

    /**
     * Только в debug-сборке: бросает исключение, если после операции {@code op} в GL есть ошибка.
     * В release вызов вырезается вместе с проверкой.
     */
    static void checkError(@NonNull String op) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException("GL error 0x" + Integer.toHexString(error)
                    + " after " + op);
        }
    }

    @NonNull
    static String readAsset(@NonNull AssetManager assets, @NonNull String name)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(assets.open(name), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
            return text.toString();
        }
    }
}
