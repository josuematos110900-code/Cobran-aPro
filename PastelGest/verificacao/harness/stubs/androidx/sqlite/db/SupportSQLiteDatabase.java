package androidx.sqlite.db;
import android.content.ContentValues; import android.database.Cursor;
public interface SupportSQLiteDatabase {
  void execSQL(String sql); void execSQL(String sql, Object[] args);
  Cursor query(String sql); Cursor query(String sql, Object[] args);
  long insert(String table, int conflict, ContentValues v);
  int update(String table, int conflict, ContentValues v, String where, Object[] args);
  void beginTransaction(); void setTransactionSuccessful(); void endTransaction();
}
