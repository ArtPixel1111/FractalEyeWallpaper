package com.fractaleye.wallpaper;

/**
 * Все настроечные значения в одном месте. Единицы измерения указаны в имени (_NS, _MS, _RAD)
 * или в комментарии.
 *
 * <p>Бюджет кадра: цель — 30 кадров/с, то есть {@link #FRAME_NS} ≈ 33,3 мс. Каждые
 * {@link #PROBE_WINDOW_NS} считается среднее время кадра: если оно больше
 * {@link #SLOW_FRAME_MS}, разрешение сцены быстро уменьшается; если держится ниже
 * {@link #FAST_FRAME_MS} несколько окон подряд — медленно растёт (см. {@link AdaptiveScaler}).
 */
final class Tuning {
    private Tuning() { }

    // ---- Качество фрактала (пресет «Лёгкое» из fractal-eye.html) ----

    /** Максимум шагов луча на пиксель. Больше — чётче края, но тяжелее. Шейдер режет на 200. */
    static final float STEPS = 90f;

    /** Итераций фрактала. Больше — больше деталей, но тяжелее. Шейдер режет на 12. */
    static final float ITERS = 7f;

    /** Расстояние от камеры до центра сцены, ед. сцены. Фрактал целиком в сфере радиуса 1,3. */
    static final float ZOOM = 3.4f;

    // ---- Адаптивное разрешение: доля размера экрана, в которую рисуется сцена ----

    static final float SCALE_MIN = 0.30f;
    static final float SCALE_MAX = 0.60f;
    static final float SCALE_START = 0.50f;

    // ---- Бюджет кадра ----

    /** Длительность кадра при 30 кадрах/с, нс. Больше FPS — больше нагрузка и расход батареи. */
    static final long FRAME_NS = 33_333_333L;

    /** Окно усреднения времени кадра, нс: достаточно, чтобы сгладить скачки, и мало, чтобы
     * реагировать за секунду. */
    static final long PROBE_WINDOW_NS = 700_000_000L;

    /** Среднее время кадра выше этого порога (около 24 кадров/с) — уменьшаем разрешение, мс. */
    static final float SLOW_FRAME_MS = 42f;

    /** Среднее время кадра ниже этого порога (держим 30 кадров/с) — можно увеличивать, мс. */
    static final float FAST_FRAME_MS = 36f;

    /** Множитель разрешения при перегрузке. Падаем быстро. */
    static final float SCALE_DOWN_FACTOR = 0.85f;

    /** Множитель разрешения при запасе по времени. Растём медленно, чтобы не качаться. */
    static final float SCALE_UP_FACTOR = 1.06f;

    /** Сколько «быстрых» окон подряд нужно перед ростом разрешения (около 2,8 с). */
    static final int GOOD_WINDOWS_TO_GROW = 4;

    // ---- Анимация (времена в секундах, углы в радианах) ----

    /** Базовый угловой радиус зрачка. */
    static final float PUPIL_BASE_RAD = .2f;
    /** Амплитуда «дыхания» зрачка. */
    static final float PUPIL_PULSE_RAD = .055f;
    /** Скорость «дыхания» зрачка, рад/с. */
    static final double PUPIL_PULSE_RAD_PER_S = 1.1;

    /** Амплитуда наклона глаза вверх-вниз. */
    static final float TILT_AMPLITUDE_RAD = .32f;
    /** Скорость наклона, рад/с. */
    static final double TILT_RAD_PER_S = .4;

    /** Амплитуда поворота глаза влево-вправо. */
    static final float PAN_AMPLITUDE_RAD = .5f;
    /** Скорость поворота, рад/с. */
    static final double PAN_RAD_PER_S = .27;
    /** Сдвиг фазы поворота относительно наклона, чтобы движение не повторялось. */
    static final double PAN_PHASE_RAD = 1.0;

    /** Поворот глаза при сдвиге рабочего стола на единицу (сдвиг лежит в -0,5…0,5). */
    static final float PAGE_YAW_RAD = 1.2f;
    /** Доля пути к целевому повороту за кадр: чем меньше, тем плавнее. */
    static final float YAW_SMOOTHING = .08f;

    // ---- Поток рендера ----

    /** Период проверки, пока обои не видны или поверхность пуста, мс. */
    static final long IDLE_NAP_MS = 100L;

    /** Сколько раз пробуем поднять рендер, прежде чем сдаться и залить поверхность цветом. */
    static final int MAX_RENDER_ATTEMPTS = 2;

    /** Пауза перед повторной попыткой, мс. */
    static final long RETRY_DELAY_MS = 500L;

    /**
     * Сколько главный поток ждёт остановки рендера, мс. Ждать нужно: после
     * onSurfaceDestroyed нельзя трогать Surface. Поток просыпается по interrupt и выходит
     * в пределах одного кадра, поэтому в норме ожидание намного короче.
     */
    static final long STOP_JOIN_TIMEOUT_MS = 250L;

    /** Цвет заливки, если OpenGL не удалось запустить: тёмно-коричневый, как фон шейдера. */
    static final int FALLBACK_COLOR = 0xFF0D0603;
}
