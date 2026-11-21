package com.pastelgest.app.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.pastelgest.app.data.dao.AuditDao;
import com.pastelgest.app.data.dao.ClientDao;
import com.pastelgest.app.data.dao.DebtDao;
import com.pastelgest.app.data.dao.ExpenseDao;
import com.pastelgest.app.data.dao.IngredientDao;
import com.pastelgest.app.data.dao.ProductDao;
import com.pastelgest.app.data.dao.ProductionDao;
import com.pastelgest.app.data.dao.PurchaseDao;
import com.pastelgest.app.data.dao.RecipeDao;
import com.pastelgest.app.data.dao.SaleDao;
import com.pastelgest.app.data.dao.SettingsDao;
import com.pastelgest.app.data.dao.SupplierDao;
import com.pastelgest.app.data.dao.SyncLogDao;
import com.pastelgest.app.data.entity.AppSetting;
import com.pastelgest.app.data.entity.AuditLog;
import com.pastelgest.app.data.entity.Client;
import com.pastelgest.app.data.entity.Debt;
import com.pastelgest.app.data.entity.Expense;
import com.pastelgest.app.data.entity.Ingredient;
import com.pastelgest.app.data.entity.Payment;
import com.pastelgest.app.data.entity.Product;
import com.pastelgest.app.data.entity.ProductMovement;
import com.pastelgest.app.data.entity.Production;
import com.pastelgest.app.data.entity.ProductionItem;
import com.pastelgest.app.data.entity.Purchase;
import com.pastelgest.app.data.entity.PurchaseItem;
import com.pastelgest.app.data.entity.PurchasePayment;
import com.pastelgest.app.data.entity.Recipe;
import com.pastelgest.app.data.entity.RecipeCostSnapshot;
import com.pastelgest.app.data.entity.RecipeIngredient;
import com.pastelgest.app.data.entity.Sale;
import com.pastelgest.app.data.entity.SaleItem;
import com.pastelgest.app.data.entity.StockMovement;
import com.pastelgest.app.data.entity.Supplier;
import com.pastelgest.app.data.entity.SyncLog;

@Database(
        entities = {
                Supplier.class, Ingredient.class, StockMovement.class,
                Purchase.class, PurchaseItem.class, PurchasePayment.class,
                Recipe.class, RecipeIngredient.class, RecipeCostSnapshot.class,
                Product.class, ProductMovement.class,
                Production.class, ProductionItem.class,
                Client.class, Sale.class, SaleItem.class, Debt.class, Payment.class,
                Expense.class, AuditLog.class, AppSetting.class, SyncLog.class
        },
        version = AppDatabase.VERSION,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    public static final int VERSION = 1;
    public static final String NAME = "pastelgest.db";

    private static volatile AppDatabase instance;

    public abstract SupplierDao supplierDao();
    public abstract IngredientDao ingredientDao();
    public abstract PurchaseDao purchaseDao();
    public abstract RecipeDao recipeDao();
    public abstract ProductDao productDao();
    public abstract ProductionDao productionDao();
    public abstract SaleDao saleDao();
    public abstract ClientDao clientDao();
    public abstract DebtDao debtDao();
    public abstract ExpenseDao expenseDao();
    public abstract AuditDao auditDao();
    public abstract SettingsDao settingsDao();
    public abstract SyncLogDao syncLogDao();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = build(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private static AppDatabase build(Context ctx) {
        return Room.databaseBuilder(ctx, AppDatabase.class, NAME)
                .addMigrations(Migrations.ALL)
                // Não usamos fallbackToDestructiveMigration: os dados financeiros nunca são apagados.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(new Callback() {
                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        db.execSQL("PRAGMA foreign_keys = ON");
                    }
                })
                .build();
    }

    /** Fecha a base de dados (usado antes de restaurar uma cópia de segurança). */
    public static synchronized void closeInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
