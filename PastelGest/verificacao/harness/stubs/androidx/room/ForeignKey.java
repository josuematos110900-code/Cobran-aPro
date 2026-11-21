package androidx.room;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface ForeignKey { Class<?> entity(); String[] parentColumns(); String[] childColumns(); int onDelete() default 1; int onUpdate() default 1; boolean deferred() default false; }
