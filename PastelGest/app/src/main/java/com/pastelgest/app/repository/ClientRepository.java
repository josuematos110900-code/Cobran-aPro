package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.data.entity.Client;
import com.pastelgest.app.data.model.ClientBalance;
import com.pastelgest.app.data.model.SaleRow;

import java.util.List;

public class ClientRepository extends BaseRepository {
    public ClientRepository(Context c) {
        super(c);
    }

    public List<ClientBalance> search(String q) {
        return db.clientDao().searchWithBalance(q == null ? "" : q.trim());
    }

    public List<Client> all() {
        return db.clientDao().all();
    }

    public Client get(String id) {
        return id == null ? null : db.clientDao().get(id);
    }

    public long balance(String id) {
        return db.clientDao().balance(id);
    }

    public long totalPurchased(String id) {
        return db.clientDao().totalPurchased(id);
    }

    public List<SaleRow> sales(String id) {
        return db.saleDao().byClient(id);
    }

    public Client save(Client input) {
        return tx(() -> {
            String name = required(input.name, "Indique o nome do cliente.");
            String phone = input.phone == null ? "" : input.phone.replaceAll("[^0-9+]", "");
            if (!phone.isEmpty() && phone.replace("+", "").length() < 9) {
                throw new BusinessException("Número de telefone inválido (mínimo 9 dígitos).");
            }
            Client existing = input.id == null || input.id.isEmpty() ? null : db.clientDao().get(input.id);
            if (db.clientDao().countByPhone(phone, existing == null ? "" : existing.id) > 0) {
                throw new BusinessException("Já existe um cliente com este telefone.");
            }
            Client c = existing != null ? existing : new Client();
            if (existing == null) c.initNew();
            c.name = name;
            c.phone = phone;
            c.address = trim(input.address);
            c.notes = trim(input.notes);
            if (existing == null) {
                db.clientDao().insert(c);
                audit("cliente", c.id, "CRIAR", name);
            } else {
                c.touch();
                db.clientDao().update(c);
                audit("cliente", c.id, "EDITAR", name);
            }
            return c;
        });
    }

    public void archive(String id) {
        tx(() -> {
            Client c = db.clientDao().get(id);
            if (c == null) throw new BusinessException("Cliente não encontrado.");
            long bal = db.clientDao().balance(id);
            if (bal > 0) throw new BusinessException("O cliente tem dívidas em aberto. Não pode ser eliminado.");
            c.deleted = 1;
            c.touch();
            db.clientDao().update(c);
            audit("cliente", id, "ELIMINAR", c.name);
            return null;
        });
    }
}
