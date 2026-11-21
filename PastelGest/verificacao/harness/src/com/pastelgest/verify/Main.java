package com.pastelgest.verify;

/** Executa todas as verificações de integração. Uso: Main <schema_room.sql> <supabase.sql> */
public class Main {
    public static void main(String[] args) throws Exception {
        IntegrationTest.schema = args[0];
        int failures = IntegrationTest.run();
        if (args.length > 1) {
            SyncTest.schema = args[0];
            SyncTest.supabaseSql = args[1];
            failures += SyncTest.run();
        }
        System.out.println(failures == 0 ? "\nRESULTADO: TODAS AS VERIFICAÇÕES PASSARAM" : "\nRESULTADO: " + failures + " FALHA(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
