package com.pastelgest.app.data;

import androidx.room.migration.Migration;

/**
 * Migrações da base de dados Room.
 *
 * A versão actual do esquema é a 1 (primeira versão publicada), por isso ainda
 * não existem migrações. O esquema de cada versão é exportado para
 * app/schemas/ (room.schemaLocation). Ao alterar uma entidade:
 *  1. incrementar AppDatabase.VERSION;
 *  2. acrescentar aqui um objecto Migration(versãoAnterior, novaVersão) com o SQL
 *     de alteração (ALTER TABLE / CREATE TABLE / CREATE INDEX);
 *  3. acrescentá-lo ao array ALL.
 * Nunca se usa migração destrutiva, para não perder histórico financeiro.
 */
public final class Migrations {
    public static final Migration[] ALL = new Migration[0];

    private Migrations() { }
}
