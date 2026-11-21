# Verificação sem Android SDK

Ferramentas usadas para verificar o PastelGest num ambiente sem o Android SDK. **Não fazem parte da aplicação** e não devem ser copiadas para o CodeAssist.

| Ficheiro | Função |
|---|---|
| `run.sh` | Executa toda a verificação (passos 1–5) |
| `rgen.py` | Valida os XML, gera `R.java` e verifica todas as referências `@tipo/nome` |
| `room_check.py` | Gera o DDL Room (`schema_room.sql`) e valida as 104 `@Query` com `EXPLAIN` |
| `gen_supabase_sql.py` | Gera `supabase/pastelgest_schema.sql` a partir do esquema Room (paridade de colunas) |
| `supabase_check.mjs` | Executa o SQL num PostgreSQL real (PGlite) e testa RLS, triggers e upserts |
| `harness/stubs/` | Stubs de assinaturas AndroidX/Material/Room/WorkManager (só para compilar e testar) |
| `harness/src/` | DAO dinâmico sobre JDBC, contexto falso, servidor Supabase emulado, testes de integração e de sincronização |

Execução:

```bash
# jars necessários (Maven Central): android-all-14-robolectric-10818077, junit 4.13.2,
# hamcrest-core 1.3, sqlite-jdbc 3.46.1.3, slf4j-api 1.7.36
TC=/caminho/para/jars ./run.sh
npm i @electric-sql/pglite@0.2.17 && node supabase_check.mjs ../supabase/pastelgest_schema.sql
```
