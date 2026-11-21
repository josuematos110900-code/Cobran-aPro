package com.pastelgest.app.sync;

/** Erro de comunicação com o Supabase. */
public class SyncException extends Exception {
    public final int httpCode;

    public SyncException(String message, int httpCode) {
        super(message);
        this.httpCode = httpCode;
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
        this.httpCode = -1;
    }
}
