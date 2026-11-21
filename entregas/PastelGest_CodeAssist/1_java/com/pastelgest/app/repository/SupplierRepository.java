package com.pastelgest.app.repository;

import android.content.Context;

import com.pastelgest.app.core.BusinessException;
import com.pastelgest.app.data.entity.Supplier;

import java.util.List;

public class SupplierRepository extends BaseRepository {
    public SupplierRepository(Context c) {
        super(c);
    }

    public List<Supplier> search(String q) {
        return db.supplierDao().search(q == null ? "" : q.trim());
    }

    public Supplier get(String id) {
        return id == null ? null : db.supplierDao().get(id);
    }

    public Supplier save(Supplier input) {
        return tx(() -> {
            String name = required(input.name, "Indique o nome do fornecedor.");
            if (input.email != null && !input.email.trim().isEmpty() && !input.email.contains("@")) {
                throw new BusinessException("Email inválido.");
            }
            Supplier existing = input.id == null || input.id.isEmpty() ? null : db.supplierDao().get(input.id);
            if (db.supplierDao().countByName(name, existing == null ? "" : existing.id) > 0) {
                throw new BusinessException("Já existe um fornecedor com este nome.");
            }
            Supplier s = existing != null ? existing : new Supplier();
            if (existing == null) s.initNew();
            s.name = name;
            s.phone = trim(input.phone);
            s.email = trim(input.email);
            s.address = trim(input.address);
            s.nif = trim(input.nif);
            s.notes = trim(input.notes);
            if (existing == null) {
                db.supplierDao().insert(s);
                audit("fornecedor", s.id, "CRIAR", name);
            } else {
                s.touch();
                db.supplierDao().update(s);
                audit("fornecedor", s.id, "EDITAR", name);
            }
            return s;
        });
    }

    public void archive(String id) {
        tx(() -> {
            Supplier s = db.supplierDao().get(id);
            if (s == null) throw new BusinessException("Fornecedor não encontrado.");
            s.deleted = 1;
            s.touch();
            db.supplierDao().update(s);
            audit("fornecedor", id, "ELIMINAR", s.name);
            return null;
        });
    }
}
