package com.pastelgest.app.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Executa a sincronização em segundo plano quando há rede (WorkManager). */
public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (!SupabaseConfig.isConfigured(c) || !new AuthManager(c).isLoggedIn()) {
            return Result.success();
        }
        try {
            new SyncManager(c).sync();
            return Result.success();
        } catch (SyncException e) {
            // Sem rede ou erro temporário do servidor: o WorkManager volta a tentar com recuo exponencial.
            if (e.httpCode == 401 || e.httpCode == 0) return Result.failure();
            return Result.retry();
        } catch (RuntimeException e) {
            return Result.retry();
        }
    }
}
