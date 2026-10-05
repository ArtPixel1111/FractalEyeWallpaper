package com.fractaleye.wallpaper;

import static com.fractaleye.wallpaper.FractalWallpaperService.TAG;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

/** Иконка приложения: сразу открывает предпросмотр живых обоев с кнопкой «Установить». */
public class SetWallpaperActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!openPreview() && !openChooser()) {
            Toast.makeText(this, R.string.wallpaper_unavailable, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    /** Прямой предпросмотр наших обоев. Возвращает false, если система его не открыла. */
    private boolean openPreview() {
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, FractalWallpaperService.class));
            startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Live wallpaper preview is unavailable, falling back to the chooser", e);
            return false;
        }
    }

    /** Запасной вариант: общий список живых обоев. */
    private boolean openChooser() {
        try {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Live wallpaper chooser is unavailable", e);
            return false;
        }
    }
}
