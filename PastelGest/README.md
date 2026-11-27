# PastelGest — Gestão profissional de pastelaria (Android)

Aplicação Android nativa em **Java + XML**, **offline-first**, com base de dados local **Room** e sincronização com **Supabase**. Interface em português de Portugal e valores em **Kz**.

| | |
|---|---|
| Linguagem | Java 8 (compatível com CodeAssist) |
| Interfaces | XML, Material Design 3 (tema claro e escuro) |
| Base de dados local | Room 2.6.1 (22 tabelas, 50 índices, chaves estrangeiras) |
| Base de dados online | Supabase (PostgreSQL + RLS), via API REST |
| Mínimo / alvo | Android 8.0 (API 26) / Android 14 (API 34) |
| Pacote | `com.pastelgest.app` |
| Dimensão | 139 classes Java (~10 700 linhas) e 110 ficheiros XML (~5 100 linhas) |

---

## 1. Arquitectura

```
┌───────────────────────────── UI (Activities / Fragments / XML) ─────────────────────────────┐
│ MainActivity + navegação inferior: Painel · Vendas · Produção · Stock · Mais                │
│ Formulários, detalhes, listas genéricas (SimpleListActivity, DetailActivity, RowAdapter)    │
└───────────────┬─────────────────────────────────────────────────────────────────────────────┘
                │ Async (thread pool) — a BD nunca é acedida na thread principal
┌───────────────▼──────────────── Repositórios (regras de negócio) ───────────────────────────┐
│ Ingredient · Supplier · Purchase · Recipe · Product · Production · Sale · Debt · Client     │
│ Expense · Report · Settings · Audit      ── todas as escritas em transacção + auditoria     │
│ StockService: única porta de alteração de stock (movimentações imutáveis, custo médio)      │
└───────────────┬──────────────────────────────────────────┬──────────────────────────────────┘
                │                                          │
┌───────────────▼──────── core (Java puro) ───────────┐   ┌▼──────────── sync ──────────────────┐
│ Money (Kz em cêntimos) · Units (kg/g, l/ml, un)     │   │ SupabaseClient (HTTP) · AuthManager │
│ CostMath (custo médio) · RecipeCostCalculator       │   │ SyncManager (push/pull, LWW)        │
│ ProductionPlanner · ProfitCalculator · Period       │   │ SyncWorker + SyncScheduler (rede)   │
└─────────────────────────────────────────────────────┘   └─────────────────────────────────────┘
                │                                          │
┌───────────────▼─────────── Room (SQLite) ───────────────▼──── Supabase (PostgreSQL + RLS) ────┐
│ Entities · DAOs · AppDatabase · Migrations              supabase/pastelgest_schema.sql         │
└────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Princípios de integridade financeira

* **Sem vírgula flutuante:** dinheiro em `long` de cêntimos de Kz; quantidades em `long` de milésimas da unidade base (g, ml, un). Os cálculos intermédios usam `BigDecimal`.
* **Conversões correctas:** 1 kg = 1000 g e 1 l = 1000 ml. Cada ingrediente só aceita unidades compatíveis com o seu tipo.
* **Stock = soma das movimentações.** Cada entrada ou saída gera um registo imutável (`stock_movements`, `product_movements`). As quantidades e valores guardados nos ingredientes e produtos são caches, recalculáveis a partir desses registos.
* **Custo médio ponderado:** cada consumo retira do stock o valor proporcional. Quando se consome tudo, retira o valor total, sem resíduos de arredondamento.
* **Custo histórico:** cada produção grava o seu custo real e um snapshot do custo da receita. Os pastéis entram em stock a esse custo, e a venda regista o CPV (custo dos produtos vendidos) desse momento. Uma subida de preço posterior não altera produções nem vendas antigas.
* **Os ingredientes só são descontados na produção.** A venda desconta apenas produtos acabados.
* **Lucro real** = vendas − CPV − despesas operacionais − desperdícios − ajustes negativos.
  * As compras não são despesa: entram em stock e só passam a custo quando são consumidas.
  * As vendas fiadas contam como venda e geram uma conta a receber. Só os pagamentos efectivos contam como dinheiro recebido.
* **Nada é apagado.** "Eliminar" é sempre lógico (`deleted = 1`). Anular uma venda, compra ou despesa exige motivo, gera movimentos de estorno e fica registado na auditoria.
* **Anti-duplicação:** cada venda, compra, produção ou pagamento recebe um UUID quando o ecrã abre. Uma segunda gravação do mesmo documento é recusada, e o botão fica bloqueado durante a gravação.
* **Transacções:** todas as operações que tocam stock ou dinheiro correm em `runInTransaction`. Se algo falhar, nada fica alterado.

---

## 2. Módulos e onde estão

| Módulo pedido | Ecrãs | Lógica |
|---|---|---|
| A. Painel inicial | `DashboardFragment` (períodos Dia/Semana/Mês, 11 indicadores, gráfico de 7 dias, alertas) | `ReportRepository.dashboard` |
| B. Ingredientes e stock | `StockFragment`, `IngredientForm/DetailActivity` (entradas e saídas, desperdício, ajuste, eliminação lógica) | `IngredientRepository`, `StockService` |
| C. Compras | `PurchaseList/Form/DetailActivity` (vários produtos, descontos por linha e global, pago/parcial/pendente, pagamento posterior, anulação, correcção auditada) | `PurchaseRepository` |
| D. Receitas e custo | `RecipeList/Form/DetailActivity` (fotografia, ingredientes, rendimento, embalagem, outros custos, custo por pastel em tempo real, margem, histórico de custos) | `RecipeRepository`, `RecipeCostCalculator` |
| E. Produção | `ProductionFragment`, `ProductionForm/DetailActivity` (necessidades, verificação de stock, ingredientes em falta, perdas, custo real) | `ProductionRepository`, `ProductionPlanner` |
| F. Vendas | `SalesFragment`, `SaleForm/DetailActivity` (descontos autorizados, paga/fiada, entrada, métodos de pagamento, comprovativo PDF, anulação) | `SaleRepository` |
| G. Clientes e dívidas | `ClientList/Form/DetailActivity`, `DebtListActivity` (pagamentos parciais e globais, vencidas, comprovativos) | `ClientRepository`, `DebtRepository` |
| H. Despesas | `ExpenseList/FormActivity` (fixas e variáveis, comprovativo opcional, anulação) | `ExpenseRepository` |
| I. Lucro real | `ProfitActivity` (demonstração de resultados por período) | `ProfitCalculator`, `ReportRepository.profit` |
| J. Relatórios | `ReportsActivity`: 10 relatórios, períodos com datas personalizadas, exportação **CSV** e **PDF** | `ReportRepository`, `ReportExporter`, `PdfWriter` |
| K. Definições | `SettingsActivity`: dados comerciais, logótipo, tema, PIN, limite de desconto, cópias de segurança, auditoria, informações; `SyncActivity` | `SettingsRepository`, `BackupHelper`, `PinSecurity` |

---

## 3. Estrutura de ficheiros

```
PastelGest/
├── settings.gradle, build.gradle, gradle.properties, gradlew, gradlew.bat, gradle/wrapper/
├── app/
│   ├── build.gradle                       dependências e configuração Android
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/AndroidManifest.xml
│       ├── main/java/com/pastelgest/app/
│       │   ├── PastelGestApp.java         tema, sincronização periódica, bloqueio por PIN
│       │   ├── core/        (8)  Money, Units, CostMath, RecipeCostCalculator, ProductionPlanner,
│       │   │                     ProfitCalculator, Period, BusinessException
│       │   ├── data/        AppDatabase, Migrations
│       │   │   ├── entity/  (23) SyncEntity + 21 entidades sincronizadas + SyncLog
│       │   │   ├── dao/     (13) DAOs com 104 consultas
│       │   │   └── model/   (13) resultados de consultas (linhas com nomes, totais)
│       │   ├── repository/  (15) regras de negócio transaccionais
│       │   ├── sync/        (7)  SupabaseConfig, SupabaseClient, AuthManager, SyncManager,
│       │   │                     SyncWorker, SyncScheduler, SyncException
│       │   ├── util/        (11) Async, Prefs, Fmt, PinSecurity, ThemeHelper, ImageStore,
│       │   │                     PdfWriter, ReceiptPdf, ReportExporter, ShareHelper, BackupHelper
│       │   └── ui/          (46) Activities, Fragments, adapter/, widget/BarChartView
│       ├── main/res/
│       │   ├── layout/      (33)  anim/ (6)  drawable/ (55: 39 ícones vectoriais)  color/ (2)
│       │   ├── values/      colors, themes, strings (158), arrays, dimens, supabase_config
│       │   ├── values-night/colors.xml    modo escuro
│       │   ├── menu/ mipmap-anydpi-v26/ xml/ (FileProvider, regras de backup)
├── supabase/pastelgest_schema.sql        esquema completo + RLS + triggers
├── docs/ANDROID_STUDIO.md                 guia passo a passo para o Android Studio
└── docs/CODEASSIST.md                     guia passo a passo para o CodeAssist
```

Na raiz do repositório existem também:
* `entregas/PastelGest_projecto_completo.zip`: o projecto completo.
* `entregas/PastelGest_CodeAssist/`: os mesmos ficheiros organizados por tipo (Java, XML, recursos, manifest, configuração), para transferência manual.

---

## 4. Compilar

### CodeAssist (Android)
Siga **[docs/CODEASSIST.md](docs/CODEASSIST.md)**.

### Android Studio
O projecto inclui o Gradle Wrapper completo (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`; Gradle 8.9, AGP 8.7.3).
Basta *File › Open* e escolher a pasta `PastelGest`. Guia completo: **[docs/ANDROID_STUDIO.md](docs/ANDROID_STUDIO.md)**.

---

## 5. Configurar o Supabase (opcional: a app funciona 100 % offline)

1. Crie um projecto em <https://supabase.com>.
2. Abra **SQL Editor › New query**, cole o conteúdo de `supabase/pastelgest_schema.sql` e carregue em **Run**. O script pode ser reexecutado sem problemas.
3. Em **Authentication › Providers**, confirme que *Email* está activo. Para dispensar a confirmação por email, desligue *Confirm email* (decisão sua, por razões de segurança).
4. Em **Project Settings › API** (ou *API Keys*), copie o **Project URL** e a chave pública **anon** ou **publishable**.
   **Nunca use a chave `service_role` / `secret` na aplicação.** A app recusa-a se a reconhecer.
5. Na aplicação, abra *Mais › Sincronização*: cole o URL e a chave, toque em **Guardar configuração** e depois em **Criar conta** ou **Entrar**.
   Em alternativa, preencha os valores por omissão em `app/src/main/res/values/supabase_config.xml` antes de compilar.
6. Para usar a mesma conta noutros telemóveis da pastelaria, basta entrar com o mesmo email: os dados descem automaticamente.

**Como funciona a sincronização**
* Tudo é gravado primeiro no Room. Os registos alterados ficam marcados como pendentes (`syncState = 1`).
* O WorkManager sincroniza logo após cada alteração e a cada 15 minutos, sempre que houver rede. Quando a Internet volta, o envio é retomado automaticamente, com recuo exponencial entre tentativas.
* **Envio:** upsert por `(owner_id, id)`. Os registos-razão (movimentos, pagamentos, auditoria) só são inseridos (`ignore-duplicates`), por isso nunca se duplicam.
* **Recepção:** cursor `server_seq` com janela de sobreposição. Uma linha remota só substitui a local se tiver `updated_at` mais recente. No servidor, um trigger impede igualmente que dados antigos substituam dados novos.
* Depois de cada recepção, stocks e saldos são recalculados a partir dos registos-razão. Assim, vendas feitas em dois telemóveis ao mesmo tempo descontam ambas o stock correctamente.

---

## 6. Segurança e dados locais

* `allowBackup="false"` e regras de extracção que excluem os dados: a base de dados financeira não sai do telemóvel sem o seu controlo.
* PIN opcional (hash SHA-256 com sal), pedido ao abrir a app e após 1 minuto em segundo plano. Após 5 tentativas erradas, bloqueia durante 30 s.
* Cópias de segurança manuais: *Definições › Cópia de segurança* exporta o ficheiro da BD. O restauro valida a versão, as tabelas e o `integrity_check` antes de substituir os dados.
* Só HTTPS (`usesCleartextTraffic="false"`). Os tokens Supabase ficam em preferências privadas da aplicação.

---

## 7. Limitações conhecidas

1. **Primeira compilação:** o projecto ainda não foi compilado com o Android SDK. Se o Android Studio ou o CodeAssist indicarem algum erro, deverá ser pontual.
2. **Room:** o `room-compiler` (annotationProcessor) tem de correr na compilação para gerar `AppDatabase_Impl`.
3. **Interface:** ainda não foi experimentada num telemóvel real.
4. **Fotografias, logótipo e comprovativos de despesas** ficam só no dispositivo onde foram escolhidos. Os caminhos sincronizam, mas as imagens não (não há upload para o Supabase Storage).
5. **Conflitos:** a regra é *last-write-wins* por registo. Se dois dispositivos editarem o mesmo documento offline, prevalece a edição mais recente. Movimentos, pagamentos e auditoria são aditivos e nunca se perdem.
6. **Contas:** os dispositivos da pastelaria partilham uma conta Supabase. Não há perfis de funcionário com permissões diferentes.
7. **Produções confirmadas não são anuláveis.** Os erros corrigem-se com ajustes de inventário ou desperdícios, que ficam auditados.
8. **Dados locais:** estão protegidos pelo isolamento do Android, `allowBackup=false` e o PIN. A base de dados não está cifrada (não usa SQLCipher).
9. **Comprovativos ≠ factura fiscal.** Em Angola, a facturação legal exige software certificado pela AGT.
