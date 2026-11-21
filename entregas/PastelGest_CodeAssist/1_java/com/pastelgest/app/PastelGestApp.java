package com.pastelgest.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pastelgest.app.sync.SyncScheduler;
import com.pastelgest.app.util.ThemeHelper;

/** Aplicação: tema, sincronização periódica e controlo do bloqueio por PIN. */
public class PastelGestApp extends Application {
    /** Tempo em segundo plano após o qual o PIN volta a ser pedido. */
    private static final long LOCK_AFTER_MS = 60_000;

    private static boolean unlocked;
    private static long backgroundSince;
    private int started;

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.apply(this);
        SyncScheduler.schedulePeriodic(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (started == 0 && backgroundSince > 0
                        && System.currentTimeMillis() - backgroundSince > LOCK_AFTER_MS) {
                    unlocked = false;
                }
                started++;
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                started--;
                if (started <= 0) {
                    started = 0;
                    backgroundSince = System.currentTimeMillis();
                }
            }

            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
            @Override public void onActivityResumed(@NonNull Activity a) { }
            @Override public void onActivityPaused(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
            @Override public void onActivityDestroyed(@NonNull Activity a) { }
        });
    }

    public static boolean isUnlocked() {
        return unlocked;
    }

    public static void setUnlocked(boolean value) {
        unlocked = value;
    }
}
