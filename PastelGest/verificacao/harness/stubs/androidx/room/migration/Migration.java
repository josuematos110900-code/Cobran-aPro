package androidx.room.migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
public abstract class Migration { public final int startVersion, endVersion; public Migration(int s,int e){startVersion=s;endVersion=e;} public abstract void migrate(SupportSQLiteDatabase db); }
