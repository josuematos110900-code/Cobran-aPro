package androidx.room;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Entity { String tableName() default ""; Index[] indices() default {}; ForeignKey[] foreignKeys() default {}; String[] primaryKeys() default {}; }
