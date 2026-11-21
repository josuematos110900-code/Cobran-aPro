package androidx.room;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Database { Class<?>[] entities(); int version(); boolean exportSchema() default true; }
