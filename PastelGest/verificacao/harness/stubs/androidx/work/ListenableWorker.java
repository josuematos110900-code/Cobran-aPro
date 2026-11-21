package androidx.work;
import android.content.Context;
public abstract class ListenableWorker { public ListenableWorker(Context c, WorkerParameters p){} public final Context getApplicationContext(){ return null; }
  public abstract static class Result { public static Result success(){return null;} public static Result retry(){return null;} public static Result failure(){return null;} } }
