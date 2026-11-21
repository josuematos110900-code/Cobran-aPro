package androidx.room;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Index { String[] value() default {}; boolean unique() default false; String name() default ""; }
