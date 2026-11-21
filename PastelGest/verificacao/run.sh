#!/usr/bin/env bash
# Verificação do PastelGest sem Android SDK (ver verificacao/README.md).
# Requer: JDK 17+, python3, e em TC: android-all-14-robolectric-10818077.jar,
# junit-4.13.2.jar, hamcrest-core-1.3.jar, sqlite-jdbc-3.46.1.3.jar, slf4j-api-1.7.36.jar
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PROJ="$HERE/.."
TC="${TC:?defina TC com a pasta dos jars}"
OUT="${OUT:-/tmp/pastelgest-verify}"
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/core"
ANDROID="$TC/android-all-14-robolectric-10818077.jar"
[ -f "$ANDROID" ] || ANDROID="$TC/../android-all.jar"

echo "== 1. Recursos XML e geração de R.java"
python3 "$HERE/rgen.py" "$PROJ/app/src/main/res" "$OUT/gen/com/pastelgest/app/R.java"

echo "== 2. Verificação Room (DDL + EXPLAIN de todas as @Query)"
python3 "$HERE/room_check.py" "$PROJ/app/src/main/java" "$HERE/schema_room.sql"

echo "== 3. Testes unitários (JUnit) da lógica de cálculo"
javac -nowarn -encoding UTF-8 -d "$OUT/core" -cp "$TC/junit-4.13.2.jar" \
  "$PROJ"/app/src/main/java/com/pastelgest/app/core/*.java "$PROJ"/app/src/test/java/com/pastelgest/app/core/*.java
java -cp "$OUT/core:$TC/junit-4.13.2.jar:$TC/hamcrest-core-1.3.jar" org.junit.runner.JUnitCore \
  $(cd "$PROJ/app/src/test/java" && find . -name '*Test.java' | sed 's#^\./##; s#\.java$##; s#/#.#g')

echo "== 4. Compilação de todo o código Java (framework Android 14 + stubs AndroidX)"
find "$PROJ/app/src/main/java" "$HERE/harness/stubs" "$HERE/harness/src" "$OUT/gen" -name '*.java' > "$OUT/srcs.txt"
javac -nowarn -parameters -encoding UTF-8 -source 8 -target 8 -Xlint:-options -proc:none \
  -d "$OUT/classes" -cp "$ANDROID:$TC/sqlite-jdbc-3.46.1.3.jar" @"$OUT/srcs.txt" 2>&1 | grep -v -e UnsupportedAppUsage -e '^Note:' || true
[ -f "$OUT/classes/com/pastelgest/app/ui/MainActivity.class" ] || { echo "Compilação falhou"; exit 1; }

echo "== 5. Testes de integração (repositórios reais sobre SQLite) e de sincronização"
java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$OUT/classes:$ANDROID:$TC/sqlite-jdbc-3.46.1.3.jar:$TC/slf4j-api-1.7.36.jar" \
  com.pastelgest.verify.Main "$HERE/schema_room.sql" "$PROJ/supabase/pastelgest_schema.sql"
