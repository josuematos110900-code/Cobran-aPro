package androidx.work;
import android.content.Context;
public abstract class Worker extends ListenableWorker { public Worker(Context c, WorkerParameters p){ super(c,p);} public abstract Result doWork(); }
