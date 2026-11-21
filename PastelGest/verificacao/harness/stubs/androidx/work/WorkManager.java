package androidx.work;
import android.content.Context;
public abstract class WorkManager { public static WorkManager getInstance(Context c){ throw new IllegalStateException("WorkManager is not initialized properly."); }
  public abstract Operation enqueueUniqueWork(String n, ExistingWorkPolicy p, OneTimeWorkRequest r);
  public abstract Operation enqueueUniquePeriodicWork(String n, ExistingPeriodicWorkPolicy p, PeriodicWorkRequest r);
  public abstract Operation cancelUniqueWork(String n); }
