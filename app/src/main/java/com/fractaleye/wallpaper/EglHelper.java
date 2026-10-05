package com.fractaleye.wallpaper;

import static com.fractaleye.wallpaper.FractalWallpaperService.TAG;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

/** Создание и освобождение EGL: дисплей, контекст OpenGL ES 2.0 и оконная поверхность. */
final class EglHelper {
    private static final int[] CONFIG_ATTRIBS = {
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
        EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
        EGL14.EGL_NONE
    };
    private static final int[] CONTEXT_ATTRIBS = {
        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL14.EGL_NONE
    };
    private static final int[] SURFACE_ATTRIBS = {EGL14.EGL_NONE};

    // Только поток рендера.
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface = EGL14.EGL_NO_SURFACE;

    /**
     * Поднимает EGL и делает контекст текущим.
     *
     * @throws IllegalStateException если любой вызов EGL не удался; в тексте код eglGetError()
     */
    void create(@NonNull SurfaceHolder holder) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw failure("eglGetDisplay");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw failure("eglInitialize");
        }

        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, CONFIG_ATTRIBS, 0, configs, 0, 1, count, 0)
                || count[0] < 1) {
            throw failure("eglChooseConfig (подходящих конфигураций: " + count[0] + ")");
        }

        context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT, CONTEXT_ATTRIBS, 0);
        if (context == EGL14.EGL_NO_CONTEXT) {
            throw failure("eglCreateContext");
        }
        surface = EGL14.eglCreateWindowSurface(display, configs[0], holder, SURFACE_ATTRIBS, 0);
        if (surface == EGL14.EGL_NO_SURFACE) {
            throw failure("eglCreateWindowSurface");
        }
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw failure("eglMakeCurrent");
        }
        if (!EGL14.eglSwapInterval(display, 1)) {
            Log.w(TAG, "eglSwapInterval(1) failed, EGL error 0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    /** Показывает готовый кадр. Возвращает false, если поверхность потеряна. */
    boolean swapBuffers() {
        return EGL14.eglSwapBuffers(display, surface);
    }

    /** Безопасно вызывать повторно и после неудачного {@link #create}. */
    void release() {
        if (display == EGL14.EGL_NO_DISPLAY) {
            return;
        }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, surface);
            surface = EGL14.EGL_NO_SURFACE;
        }
        if (context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(display, context);
            context = EGL14.EGL_NO_CONTEXT;
        }
        EGL14.eglTerminate(display);
        display = EGL14.EGL_NO_DISPLAY;
    }

    private static IllegalStateException failure(String call) {
        return new IllegalStateException(
                call + " failed, EGL error 0x" + Integer.toHexString(EGL14.eglGetError()));
    }
}
