# Guia de instalação no CodeAssist

Este guia explica como colocar o PastelGest no **CodeAssist** (IDE Android para telemóvel) e compilá-lo.

Há duas formas de o fazer:

* **A. Importar o ZIP** (`entregas/PastelGest_projecto_completo.zip`): é o mais rápido.
* **B. Criar o projecto no CodeAssist e copiar os ficheiros à mão**, usando a pasta `entregas/PastelGest_CodeAssist/`, onde os ficheiros estão separados por tipo.

---

## A. Importar o ZIP

1. Copie `PastelGest_projecto_completo.zip` para o telemóvel, por exemplo para `Download/`.
2. Extraia-o com um gestor de ficheiros para a pasta de projectos do CodeAssist. Normalmente é `/storage/emulated/0/CodeAssistProjects/`, mas confirme no próprio CodeAssist, em *Settings*.
3. Abra o CodeAssist, escolha *Open project* e seleccione a pasta `PastelGest`.
4. Aguarde a resolução das dependências (é preciso Internet na primeira vez) e depois carregue em **Run / Build**.

## B. Criar o projecto e copiar os ficheiros

1. No CodeAssist, crie um projecto novo do tipo **Empty Activity (Java)** com estes dados:
   * Nome: `PastelGest`
   * Pacote: `com.pastelgest.app`
   * Linguagem: Java
   * Min SDK: 26
2. Apague o `MainActivity.java` e o `activity_main.xml` que o modelo criou.
3. Copie os ficheiros da pasta `entregas/PastelGest_CodeAssist/` para estes destinos:

| Pasta de origem | Destino no projecto CodeAssist |
|---|---|
| `1_java/com/pastelgest/app/**` | `app/src/main/java/com/pastelgest/app/**` |
| `2_xml_layouts/*.xml` | `app/src/main/res/layout/` |
| `3_xml_values/values/*.xml` | `app/src/main/res/values/` |
| `3_xml_values/values-night/*.xml` | `app/src/main/res/values-night/` |
| `4_recursos/drawable/*.xml` | `app/src/main/res/drawable/` |
| `4_recursos/mipmap-anydpi-v26/*.xml` | `app/src/main/res/mipmap-anydpi-v26/` |
| `4_recursos/anim/`, `color/`, `menu/`, `xml/` | `app/src/main/res/anim/`, `color/`, `menu/`, `xml/` |
| `5_manifest/AndroidManifest.xml` | `app/src/main/AndroidManifest.xml` (substituir) |
| `6_configuracao/app_build.gradle` | `app/build.gradle` (substituir) |
| `6_configuracao/settings.gradle`, `build.gradle`, `gradle.properties` | raiz do projecto |
| `7_testes/**` | `app/src/test/java/**` (opcional) |
| `8_supabase/pastelgest_schema.sql` | **não vai para o telemóvel**: execute-o no Supabase |

4. Mantenha **exactamente** a estrutura de pastas Java (`com/pastelgest/app/...`). Os `package` dos ficheiros dependem dela.

---

## Dependências (app/build.gradle)

```gradle
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'androidx.core:core:1.12.0'
implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'
implementation 'androidx.room:room-runtime:2.6.1'
annotationProcessor 'androidx.room:room-compiler:2.6.1'
implementation 'androidx.work:work-runtime:2.9.0'
```

Não são usadas bibliotecas do JitPack, Kotlin, ViewBinding nem DataBinding.

* Os gráficos são desenhados com uma View própria (`BarChartView`).
* Os PDF usam `android.graphics.pdf.PdfDocument`, que já faz parte do Android.
* O Supabase é acedido por HTTP (`HttpURLConnection` + `org.json`), sem SDK externo.

### Pontos de atenção no CodeAssist

1. **Room precisa do annotation processor.** A linha `annotationProcessor 'androidx.room:room-compiler:2.6.1'` gera a classe `AppDatabase_Impl`.
   * Se o CodeAssist ignorar processadores de anotações, a compilação conclui, mas a app fecha ao abrir com o erro `Cannot find implementation for com.pastelgest.app.data.AppDatabase. AppDatabase_Impl does not exist`.
   * Nesse caso, actualize o CodeAssist para uma versão com suporte a *annotation processing*, ou compile uma vez no Android Studio.
2. **compileSdk 34.** O Material 1.11 e o AndroidX Core 1.12 exigem o SDK 34 instalado no CodeAssist (*Settings › SDK*).
3. **Java 8.** O código não usa APIs posteriores ao Java 8. Lambdas e referências a métodos são suportados pelo D8.
4. Se o CodeAssist avisar sobre `room.schemaLocation`, o aviso não impede a compilação. Pode criar a pasta `app/schemas` ou ignorá-lo.
5. Se houver erro de memória ao compilar, feche outras aplicações e, nas definições do CodeAssist, aumente a memória do compilador, se essa opção existir.

---

## Primeira utilização da app

1. *Mais › Definições*: indique o nome da pastelaria, NIF, telefone e logótipo, e toque em **Guardar definições**.
2. *Stock › Novo ingrediente*: registe os ingredientes. Pode indicar o stock que já tem e o valor que pagou por ele.
3. *Mais › Compras › Nova compra*: as compras fazem entrar stock e definem o custo médio.
4. *Mais › Receitas e custos › Nova receita*: o produto acabado é criado automaticamente, e o custo por pastel aparece em tempo real.
5. *Produção › Nova produção*: verifique a disponibilidade e confirme. Os ingredientes são descontados e os pastéis entram em stock.
6. *Vendas › Nova venda*: registe vendas pagas ou fiadas e partilhe o comprovativo em PDF.
7. *Mais › Lucro real* e *Relatórios*: acompanhe os resultados e exporte em PDF ou CSV.
8. (Opcional) *Mais › Sincronização*: ligue ao Supabase (ver README, secção 5).
