package androidx.room;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Update { int onConflict() default 3; }
