package com.fractaleye.wallpaper;

import android.service.wallpaper.WallpaperService;

import androidx.annotation.NonNull;

/** Живые обои «Fractal Eye». Вся работа происходит в {@link FractalEngine}. */
public class FractalWallpaperService extends WallpaperService {
    /** Единый тег для всех логов приложения. */
    static final String TAG = "FractalEye";

    @NonNull
    @Override
    public Engine onCreateEngine() {
        return new FractalEngine(this);
    }
}
