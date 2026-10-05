package com.fractaleye.wallpaper;

import static com.fractaleye.wallpaper.FractalWallpaperService.TAG;

import android.content.res.AssetManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Жизненный цикл обоев: запускает и останавливает {@link RenderThread}.
 * Все колбэки приходят в главном потоке; работу с GL здесь не выполняем.
 */
final class FractalEngine extends WallpaperService.Engine {
    private final AssetManager assets;

    // Поля ниже трогает только главный поток.
    @Nullable private RenderThread renderThread;
    private boolean visible = true;
    private float pageOffset = 0f;

    FractalEngine(@NonNull WallpaperService service) {
        // Engine — внутренний класс WallpaperService, поэтому нужен явный внешний экземпляр.
        service.super();
        this.assets = service.getAssets();
    }

    @Override
    public void onVisibilityChanged(boolean isVisible) {
        visible = isVisible;
        if (renderThread != null) {
            renderThread.setVisible(isVisible);
        }
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep,
            int xPixels, int yPixels) {
        // xOffset идёт от 0 до 1 по страницам рабочего стола; центрируем в -0,5…0,5.
        pageOffset = xStep > 0f ? xOffset - 0.5f : 0f;
        if (renderThread != null) {
            renderThread.setPageOffset(pageOffset);
        }
    }

    @Override
    public void onSurfaceCreated(SurfaceHolder holder) {
        super.onSurfaceCreated(holder);
        stopRenderer(true);
        RenderThread thread = new RenderThread(holder, assets);
        thread.setVisible(visible);
        thread.setPageOffset(pageOffset);
        renderThread = thread;
        thread.start();
    }

    @Override
    public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        super.onSurfaceChanged(holder, format, width, height);
        if (renderThread != null) {
            renderThread.setSize(width, height);
        }
    }

    @Override
    public void onSurfaceDestroyed(SurfaceHolder holder) {
        // После возврата из этого метода Surface трогать нельзя, поэтому ждём выхода потока.
        stopRenderer(true);
        super.onSurfaceDestroyed(holder);
    }

    @Override
    public void onDestroy() {
        // Surface к этому моменту уже уничтожен, поток освободит EGL сам: не блокируем главный.
        stopRenderer(false);
        super.onDestroy();
    }

    private void stopRenderer(boolean waitForExit) {
        RenderThread thread = renderThread;
        renderThread = null;
        if (thread == null) {
            return;
        }
        thread.requestStop();
        if (!waitForExit) {
            return;
        }
        try {
            thread.join(Tuning.STOP_JOIN_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            Log.w(TAG, "Render thread did not stop within " + Tuning.STOP_JOIN_TIMEOUT_MS + " ms");
        }
    }
}
