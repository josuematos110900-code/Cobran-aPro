package com.pastelgest.app.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.pastelgest.app.data.entity.Client;
import com.pastelgest.app.data.model.ClientBalance;

import java.util.List;

@Dao
public interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(Client c);

    @Update
    void update(Client c);

    @Query("SELECT * FROM clients WHERE id = :id")
    Client get(String id);

    @Query("SELECT * FROM clients WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    List<Client> all();

    @Query("SELECT c.*, COALESCE((SELECT SUM(d.balanceCents) FROM debts d WHERE d.clientId = c.id AND d.status = 'ABERTA' AND d.deleted = 0), 0) AS balanceCents FROM clients c WHERE c.deleted = 0 AND (:q = '' OR c.name LIKE '%' || :q || '%' OR c.phone LIKE '%' || :q || '%') ORDER BY c.name COLLATE NOCASE")
    List<ClientBalance> searchWithBalance(String q);

    @Query("SELECT COUNT(*) FROM clients WHERE deleted = 0 AND phone = :phone AND phone <> '' AND id <> :excludeId")
    int countByPhone(String phone, String excludeId);

    @Query("SELECT COALESCE(SUM(balanceCents), 0) FROM debts WHERE clientId = :clientId AND status = 'ABERTA' AND deleted = 0")
    long balance(String clientId);

    @Query("SELECT COALESCE(SUM(totalCents), 0) FROM sales WHERE clientId = :clientId AND status = 'ACTIVA' AND deleted = 0")
    long totalPurchased(String clientId);
}
