package com.fractaleye.wallpaper;

import static com.fractaleye.wallpaper.FractalWallpaperService.TAG;

import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Поток рендера: поднимает EGL и сцену, крутит цикл кадров, при сбое пробует ещё раз,
 * а если не вышло — заливает поверхность цветом.
 */
final class RenderThread extends Thread {
    private static final float NS_PER_S = 1_000_000_000f;
    private static final long NS_PER_MS = 1_000_000L;
    /** Поверхность меньше этого размера (px) не рисуем: пока она ещё не получила размер. */
    private static final int MIN_SURFACE_PX = 2;

    /** Размер поверхности. Публикуется целиком, чтобы ширина и высота не рассинхронизировались. */
    private static final class Size {
        static final Size EMPTY = new Size(0, 0);

        final int width;
        final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        boolean isUsable() {
            return width >= MIN_SURFACE_PX && height >= MIN_SURFACE_PX;
        }
    }

    private final SurfaceHolder holder;
    private final AssetManager assets;
    private final EglHelper egl = new EglHelper();
    private final SceneRenderer scene = new SceneRenderer();
    private final AdaptiveScaler scaler = new AdaptiveScaler();

    // Пишет главный поток, читает поток рендера.
    private volatile boolean running = true;
    private volatile boolean visible = true;
    private volatile float pageOffset = 0f;
    private volatile Size surfaceSize = Size.EMPTY;

    // Только поток рендера.
    private float yawRad = 0f;

    RenderThread(@NonNull SurfaceHolder holder, @NonNull AssetManager assets) {
        this.holder = holder;
        this.assets = assets;
        setName("FractalEyeRenderer");
    }

    void setVisible(boolean isVisible) {
        visible = isVisible;
    }

    void setPageOffset(float offset) {
        pageOffset = offset;
    }

    void setSize(int width, int height) {
        surfaceSize = new Size(width, height);
    }

    void requestStop() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        for (int attempt = 1; running && attempt <= Tuning.MAX_RENDER_ATTEMPTS; attempt++) {
            try {
                renderUntilStopped();
                return;
            } catch (Throwable e) {
                if (!running) {
                    return;
                }
                // Ловим всё: сбой драйвера не должен ронять процесс обоев.
                Log.e(TAG, "Renderer failed, attempt " + attempt + " of "
                        + Tuning.MAX_RENDER_ATTEMPTS, e);
            }
            if (attempt < Tuning.MAX_RENDER_ATTEMPTS && running) {
                nap(Tuning.RETRY_DELAY_MS);
                if (Thread.interrupted()) {
                    return;
                }
            }
        }
        if (running) {
            drawFallbackColor();
        }
    }

    private void renderUntilStopped() throws IOException {
        try {
            egl.create(holder);
            scene.init(assets);
            renderLoop();
        } finally {
            // Объекты GL удаляются при текущем контексте, поэтому раньше, чем EGL.
            try {
                scene.release();
            } finally {
                egl.release();
            }
        }
    }

    private void renderLoop() {
        final long startNs = System.nanoTime();
        scaler.reset(startNs);
        while (running) {
            Size size = surfaceSize;
            if (!visible || !size.isUsable()) {
                nap(Tuning.IDLE_NAP_MS);
                scaler.reset(System.nanoTime());
                continue;
            }

            long frameStartNs = System.nanoTime();
            float timeS = (frameStartNs - startNs) / NS_PER_S;
            yawRad += (pageOffset * Tuning.PAGE_YAW_RAD - yawRad) * Tuning.YAW_SMOOTHING;

            scene.drawFrame(size.width, size.height, scaler.getScale(), timeS, yawRad);
            if (!egl.swapBuffers()) {
                if (!running) {
                    return;
                }
                throw new IllegalStateException("eglSwapBuffers failed");
            }

            long frameEndNs = System.nanoTime();
            scaler.onFrameFinished(frameEndNs);
            long spentNs = frameEndNs - frameStartNs;
            if (spentNs < Tuning.FRAME_NS) {
                nap((Tuning.FRAME_NS - spentNs) / NS_PER_MS);
            }
        }
    }

    /** Последний шанс: однотонная заливка через Canvas, чтобы обои не остались чёрными. */
    private void drawFallbackColor() {
        try {
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Tuning.FALLBACK_COLOR);
                holder.unlockCanvasAndPost(canvas);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Fallback fill failed", e);
        }
    }

    private void nap(long ms) {
        try {
            Thread.sleep(Math.max(1L, ms));
        } catch (InterruptedException e) {
            // interrupt() — сигнал остановки: сохраняем флаг, цикл увидит running == false.
            Thread.currentThread().interrupt();
        }
    }
}
