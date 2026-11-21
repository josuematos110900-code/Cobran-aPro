package com.pastelgest.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Execução de trabalho de base de dados fora da thread principal. */
public final class Async {
    public interface Success<T> {
        void accept(T value);
    }

    public interface Failure {
        void accept(Throwable error);
    }

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Async() { }

    public static <T> void run(Callable<T> work, Success<T> onSuccess, Failure onError) {
        IO.execute(() -> {
            try {
                T result = work.call();
                MAIN.post(() -> {
                    if (onSuccess != null) onSuccess.accept(result);
                });
            } catch (Throwable t) {
                MAIN.post(() -> {
                    if (onError != null) onError.accept(unwrap(t));
                });
            }
        });
    }

    public static void background(Runnable r) {
        IO.execute(r);
    }

    public static void main(Runnable r) {
        MAIN.post(r);
    }

    /** Room envolve excepções verificadas em RuntimeException; recupera a causa original. */
    public static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur
                && !(cur instanceof com.pastelgest.app.core.BusinessException)) {
            cur = cur.getCause();
        }
        return cur;
    }
}
