package androidx.work;
import java.util.concurrent.TimeUnit;
public final class PeriodicWorkRequest extends WorkRequest { public static final class Builder extends WorkRequest.Builder<Builder, PeriodicWorkRequest> { public Builder(Class<? extends ListenableWorker> k, long i, TimeUnit u){} } }
