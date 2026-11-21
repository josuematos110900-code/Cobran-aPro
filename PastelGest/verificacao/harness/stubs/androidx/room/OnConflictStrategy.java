package androidx.room;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface OnConflictStrategy { int NONE=0; int REPLACE=1; int ROLLBACK=2; int ABORT=3; int FAIL=4; int IGNORE=5; }
