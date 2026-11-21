package com.pastelgest.app.sync;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.pastelgest.app.util.Prefs;

import java.util.concurrent.TimeUnit;

/** Agenda sincronizações: após cada alteração local e periodicamente, sempre com rede. */
public final class SyncScheduler {
    private static final String NOW = "pastelgest_sync_now";
    private static final String PERIODIC = "pastelgest_sync_periodic";

    private SyncScheduler() { }

    private static boolean enabled(Context c) {
        return Prefs.bool(c, Prefs.AUTO_SYNC, true) && SupabaseConfig.isConfigured(c) && new AuthManager(c).isLoggedIn();
    }

    private static Constraints network() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /** Pedido de sincronização: executa logo que houver Internet. */
    public static void requestSync(Context c) {
        try {
            if (!enabled(c)) return;
            OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                    .setConstraints(network())
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build();
            WorkManager.getInstance(c).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req);
        } catch (IllegalStateException ignored) {
            // WorkManager não inicializado (ex.: testes): a sincronização periódica tratará do envio.
        }
    }

    public static void schedulePeriodic(Context c) {
        try {
            WorkManager wm = WorkManager.getInstance(c);
            if (!enabled(c)) {
                wm.cancelUniqueWork(PERIODIC);
                return;
            }
            PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(network())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build();
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req);
        } catch (IllegalStateException ignored) {
            // ver acima
        }
    }
}
