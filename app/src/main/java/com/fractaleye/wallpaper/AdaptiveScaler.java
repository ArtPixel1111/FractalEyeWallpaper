package com.fractaleye.wallpaper;

/**
 * Подбирает разрешение сцены по среднему времени кадра: быстро снижает при перегрузке
 * и медленно повышает, когда запас стабилен. Пороги и множители — в {@link Tuning}.
 * Вызывается только из потока рендера.
 */
final class AdaptiveScaler {
    private static final float NS_PER_MS = 1_000_000f;

    private float scale = Tuning.SCALE_START;
    private long windowStartNs;
    private int windowFrames;
    private int goodWindows;

    /** Доля размера экрана (SCALE_MIN…SCALE_MAX), в которую сейчас рисуется сцена. */
    float getScale() {
        return scale;
    }

    /** Начинает измерение заново, например после паузы: время простоя в среднее не входит. */
    void reset(long nowNs) {
        windowStartNs = nowNs;
        windowFrames = 0;
        goodWindows = 0;
    }

    /** Вызывается после каждого показанного кадра. */
    void onFrameFinished(long nowNs) {
        windowFrames++;
        long elapsedNs = nowNs - windowStartNs;
        if (elapsedNs <= Tuning.PROBE_WINDOW_NS) {
            return;
        }

        float avgMs = elapsedNs / NS_PER_MS / windowFrames;
        if (avgMs > Tuning.SLOW_FRAME_MS) {
            scale = Math.max(Tuning.SCALE_MIN, scale * Tuning.SCALE_DOWN_FACTOR);
            goodWindows = 0;
        } else if (avgMs < Tuning.FAST_FRAME_MS && scale < Tuning.SCALE_MAX) {
            goodWindows++;
            if (goodWindows >= Tuning.GOOD_WINDOWS_TO_GROW) {
                scale = Math.min(Tuning.SCALE_MAX, scale * Tuning.SCALE_UP_FACTOR);
                goodWindows = 0;
            }
        } else {
            goodWindows = 0;
        }
        windowStartNs = nowNs;
        windowFrames = 0;
    }
}
