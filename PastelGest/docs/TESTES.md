# Testes realizados, resultados e limitações

Data: 21/11/2026

## Ambiente de verificação (transparência)

O ambiente onde o projecto foi desenvolvido **não tinha acesso ao Android SDK nem ao repositório Google Maven** (`dl.google.com` bloqueado). Por isso:

* **Não executei uma compilação Gradle/AGP completa, não gerei um APK e não testei a app num telemóvel ou emulador.**
* Em substituição, montei uma cadeia de verificação que cobre tudo o que era possível cobrir sem o SDK. Está em `verificacao/` e pode ser repetida com `verificacao/run.sh`.

| Componente | Como foi verificado |
|---|---|
| Framework Android | Compilação contra `android-all-14` (classes reais do Android 14, publicadas pelo Robolectric no Maven Central) |
| AndroidX / Material / Room / WorkManager | *Stubs* escritos à mão com as assinaturas públicas usadas (`verificacao/harness/stubs`). Servem só para verificar; **não fazem parte da app** |
| Recursos XML / `R` | Script próprio (`rgen.py`): valida o XML, gera `R.java` e confirma que todas as referências `@tipo/nome` existem |
| Room | Script `room_check.py`: gera o DDL a partir das `@Entity` (com as regras do Room) e valida cada `@Query` com `EXPLAIN` no SQLite |
| Repositórios | Executados de verdade sobre SQLite (JDBC), através de uma implementação dinâmica dos DAOs que corre as mesmas queries |
| Supabase | SQL executado num PostgreSQL real (PGlite 16.4), com o schema `auth`, papéis `anon` e `authenticated` e RLS |
| Sincronização | `SyncManager` real, com 2 dispositivos e um servidor que emula o PostgREST com as regras do SQL |

## Resultados

| # | Verificação pedida | Resultado |
|---|---|---|
| 1 | Erros de sintaxe Java | **OK**: 139 classes compiladas sem erros (javac, `-source 8`) |
| 2 | Imports e referências entre classes | **OK**: compilação completa, incluindo as 46 classes de UI |
| 3 | IDs XML e referências a recursos | **OK**: 340 IDs, 158 textos, 55 drawables, 33 layouts; 0 referências inexistentes. Corrigi um erro real (estilos `PG.Text.*` sem pai, que fariam o aapt2 falhar) |
| 4 | Dependências e versões | Conferidas manualmente (compileSdk 34 exigido pelo Material 1.11 e pelo Core 1.12). **Não resolvidas pelo Gradle** (Google Maven inacessível) |
| 5 | Manifest | 29 activities declaradas e confirmadas contra as classes; FileProvider coerente com `ShareHelper` |
| 6 | DAOs e Entities | **OK**: 22 tabelas, 50 índices, todas as FK indexadas; **104 queries** válidas no SQLite, parâmetros `:nome` coerentes e colunas que cobrem os tipos de retorno |
| 7 | Operações de stock | **OK** (integração): entradas por compra, saídas por produção, desperdício, ajuste; stock negativo impedido; caches = soma das movimentações |
| 8 | Cálculo das receitas | **OK**: conversões kg/g e l/ml, custo médio ponderado, embalagem e outros custos (JUnit e integração) |
| 9 | Custo por pastel | **OK**: custo do lote ÷ rendimento; as perdas são absorvidas pelos pastéis bons; custo histórico mantém-se após subida de preço |
| 10 | Produção | **OK**: necessidades escaladas (arredondamento para cima), recusa com lista dos ingredientes em falta, **sem alterações parciais** (transacção), anti-duplicação |
| 11 | Vendas | **OK**: total, descontos dentro do limite, CPV histórico, stock de produtos, ingredientes **não** descontados de novo, anti-duplicação, anulação com estorno e reposição |
| 12 | Dívidas e pagamentos | **OK**: entrada, pagamento parcial e global, pagamento duplicado e acima do saldo recusados, liquidação |
| 13 | Lucro | **OK**: lucro = vendas − CPV − despesas − desperdícios − ajustes; caixa e contas a receber separados; compras fora do lucro |
| 14 | Funcionamento offline | **OK**: todas as operações gravam localmente e ficam pendentes; sem rede, a sincronização falha de forma controlada e nada se perde |
| 15 | Sincronização | **OK**: envio sem duplicações, reenvio vazio, 2.º dispositivo recebe tudo, vendas simultâneas em 2 dispositivos com stock final correcto, conflito resolvido pela alteração mais recente, anulação remota propagada |
| 16 | SQL Supabase | **OK**: 18/18 (RLS, isolamento entre contas, LWW, DELETE proibido, tabelas-razão imutáveis, FK, idempotência) |

Totais: **25** testes JUnit, **96** verificações de integração, **41** de sincronização e **18** de PostgreSQL. **0 falhas.**

Durante os testes encontrei e corrigi, além dos estilos XML:
* um método inexistente do Material nos stubs (`Chip.setCheckable`, que existe na biblioteca real);
* a filtragem indevida dos menus de selecção (`Dropdown.NoFilterAdapter`);
* um caso raro de linha não recebida na sincronização (janela de sobreposição do `server_seq`).

## Limitações e o que falta confirmar

1. **Compilação real e APK.** É preciso compilar no CodeAssist ou no Android Studio. Os riscos residuais estão nas partes que não pude ligar às bibliotecas reais: atributos XML do Material, estilos-pai e assinaturas AndroidX representadas por stubs. Se o compilador apontar algo, os erros devem ser pontuais.
2. **Room annotation processor.** Validei as queries com o SQLite e repliquei as regras do Room, mas o `room-compiler` em si não correu. O CodeAssist tem de suportar `annotationProcessor` (ver docs/CODEASSIST.md).
3. **Interface não testada em dispositivo.** A UI compila e todos os botões têm acções reais ligadas aos repositórios, mas não fiz testes visuais nem de interacção (Espresso) num telemóvel.
4. **Fotografias, logótipo e comprovativos de despesas** ficam só no dispositivo onde foram escolhidos. Os caminhos sincronizam, mas as imagens não (não há upload para o Supabase Storage).
5. **Conflitos:** a regra é *last-write-wins* por registo. Se dois dispositivos editarem o mesmo documento offline, prevalece a edição mais recente. Movimentos, pagamentos e auditoria são aditivos e nunca se perdem.
6. **Contas:** os dispositivos da pastelaria partilham uma conta Supabase. Não há perfis de funcionário com permissões diferentes.
7. **Produções confirmadas não são anuláveis.** Os erros corrigem-se com ajustes de inventário ou desperdícios, que ficam auditados.
8. **Dados locais:** estão protegidos pelo isolamento do Android, `allowBackup=false` e o PIN. A base de dados não está cifrada (não usa SQLCipher).
9. **Comprovativos ≠ factura fiscal.** Em Angola, a facturação legal exige software certificado pela AGT.
10. **Gradle Wrapper:** incluído e testado (`./gradlew --version` descarrega e arranca o Gradle 8.9; o projecto é lido correctamente). A resolução do Android Gradle Plugin 8.7.3 não pôde ser testada aqui porque o Google Maven está bloqueado neste ambiente.
