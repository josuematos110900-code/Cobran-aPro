# Abrir e compilar o PastelGest no Android Studio

## Requisitos

| Item | Versão |
|---|---|
| Android Studio | Ladybug (2024.2) ou mais recente |
| JDK do Gradle | 17 ou superior (o JDK que vem com o Android Studio serve) |
| Android SDK | Platform **34** (Android 14) e Build-Tools 34 ou superior |
| Gradle | 8.9, descarregado automaticamente pelo Wrapper incluído |
| Android Gradle Plugin | 8.7.3 |
| Internet | Necessária na primeira abertura, para descarregar o Gradle e as bibliotecas |

## Passos

1. **Extraia** o ZIP (botão direito › *Extrair Tudo…*) para uma pasta curta, sem acentos nem espaços, por exemplo `C:\Projectos`. Não abra o ZIP directamente: o Android Studio não lê ficheiros dentro de um ZIP.
2. Fica a pasta `C:\Projectos\PastelGest`. Confirme que, **dentro dela**, vê `settings.gradle`, `build.gradle`, `gradlew.bat` e a pasta `app`.
3. No Android Studio: **File › Open** e seleccione exactamente essa pasta `PastelGest`.
   Se aparecer o erro *"does not contain a Gradle build"*, abriu a pasta errada: escolha a pasta onde está o `settings.gradle`.
4. Se perguntar se confia no projecto, escolha **Trust Project**.
5. Aguarde o **Gradle Sync** (barra inferior). Na primeira vez demora alguns minutos.
   * Se faltar o SDK 34, o Android Studio mostra a ligação *Install missing platform*: aceite.
   * Se pedir o JDK: **File › Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** e escolha *jbr-17* ou *Embedded JDK*.
6. Ligue um telemóvel com *Depuração USB* activa, ou crie um emulador em **Device Manager** (API 26 ou superior).
7. Carregue em **Run ▶** (configuração `app`).

### Pela linha de comandos

```bash
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

### APK para distribuir (release)

1. **Build › Generate Signed App Bundle / APK › APK**.
2. Crie uma *keystore* nova e guarde-a em local seguro: sem ela não poderá publicar actualizações.
3. Escolha *release*. O APK fica em `app/release/`.

## Se o Android Studio sugerir actualizações

* *"Upgrade Android Gradle Plugin"*: pode aceitar o **AGP Upgrade Assistant**. O projecto não usa APIs obsoletas do AGP.
* Se actualizar para `compileSdk 35` ou superior, altere também `targetSdk` em `app/build.gradle` e confirme o ecrã em modo *edge-to-edge* (Android 15).

## Problemas frequentes

| Mensagem | Solução |
|---|---|
| `Unsupported Java. Your build is currently configured to use Java 11` | Gradle JDK = 17 (ver passo 5) |
| `Failed to find target with hash string 'android-34'` | Tools › SDK Manager › instalar Android 14 (API 34) |
| `Could not resolve com.android.tools.build:gradle` | Verifique a Internet ou o proxy (File › Settings › HTTP Proxy) |
| `AppDatabase_Impl does not exist` ao abrir a app | Faça **Build › Rebuild Project**: o `room-compiler` gera esta classe durante a compilação |
| Avisos `room.schemaLocation` | Não são erros; o esquema é exportado para `app/schemas/` |

## Configuração opcional do Supabase

Antes de compilar, pode preencher `app/src/main/res/values/supabase_config.xml` com o URL do projecto e a chave **pública** (anon/publishable). Também pode fazê-lo depois, na própria app, em *Mais › Sincronização*. Veja o README, secção 5.
