package androidx.work;
import java.util.concurrent.TimeUnit;
public abstract class WorkRequest { public abstract static class Builder<B extends Builder<?,?>, W extends WorkRequest> {
  public final B setConstraints(Constraints c){return null;} public final B setBackoffCriteria(BackoffPolicy p, long d, TimeUnit u){return null;}
  public B setInitialDelay(long d, TimeUnit u){return null;} public final W build(){return null;} } }
