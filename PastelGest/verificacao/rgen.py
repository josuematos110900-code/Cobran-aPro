"""Gera R.java a partir de res/ e verifica todas as referências @tipo/nome nos XML."""
import os, re, sys, xml.etree.ElementTree as ET
res = sys.argv[1]; out = sys.argv[2]; pkg = "com.pastelgest.app"
types = {}
def add(t, n): types.setdefault(t, set()).add(n)
errors = []
ANDROID = "{http://schemas.android.com/apk/res/android}"
for d in sorted(os.listdir(res)):
    base = d.split("-")[0]
    for f in sorted(os.listdir(os.path.join(res, d))):
        p = os.path.join(res, d, f)
        try:
            tree = ET.parse(p)
        except ET.ParseError as e:
            errors.append(f"XML inválido {p}: {e}"); continue
        name = f.rsplit(".", 1)[0]
        if base == "values":
            for el in tree.getroot():
                n = el.get("name")
                tag = el.tag
                if tag == "string-array": add("array", n)
                elif tag in ("string","color","dimen","bool","integer"): add(tag, n)
                elif tag == "style": add("style", n.replace(".", "_"))
                elif tag == "attr": add("attr", n)
        else:
            add(base, name)
        txt = open(p, encoding="utf-8").read()
        for m in re.finditer(r'@\+id/([A-Za-z0-9_]+)', txt): add("id", m.group(1))
# verificar referências
defined = {t: set(v) for t, v in types.items()}
stylesRaw = set()
for d in os.listdir(res):
    if d.startswith("values"):
        for f in os.listdir(os.path.join(res,d)):
            for el in ET.parse(os.path.join(res,d,f)).getroot():
                if el.tag=="style": stylesRaw.add(el.get("name"))
for d in sorted(os.listdir(res)):
    for f in sorted(os.listdir(os.path.join(res, d))):
        p = os.path.join(res, d, f)
        txt = open(p, encoding="utf-8").read()
        for m in re.finditer(r'"@(?!android:)(?!\+)([a-z]+)/([A-Za-z0-9_.]+)"', txt):
            t, n = m.group(1), m.group(2)
            key = n.replace(".", "_") if t == "style" else n
            if t == "style" and n.startswith(("Widget.Material3.", "Theme.Material3.")):
                continue
            if t not in defined or key not in defined[t]:
                errors.append(f"{p}: referência inexistente @{t}/{n}")
        for m in re.finditer(r'<style name="([^"]+)"( parent="([^"]*)")?', txt):
            n, par = m.group(1), m.group(3)
            if par is None and "." in n:
                implicit = n.rsplit(".",1)[0]
                if implicit not in stylesRaw: errors.append(f"{p}: estilo {n} herda implicitamente de {implicit} inexistente")
# R.java
lines = [f"package {pkg};", "", "public final class R {"]
counter = 0x7f010000
for t in sorted(types):
    lines.append(f"    public static final class {t} {{")
    for n in sorted(types[t]):
        counter += 1
        lines.append(f"        public static final int {n} = 0x{counter:08x};")
    lines.append("    }")
lines.append("}")
os.makedirs(os.path.dirname(out), exist_ok=True)
open(out, "w").write("\n".join(lines) + "\n")
print("tipos:", {t: len(v) for t, v in types.items()})
if errors:
    print("\n".join(errors)); sys.exit(1)
print("Referências XML: OK")
