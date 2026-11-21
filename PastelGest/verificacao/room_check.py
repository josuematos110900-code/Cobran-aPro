#!/usr/bin/env python3
"""
Verificação estática das classes Room do PastelGest (substitui parcialmente o
room-compiler quando este não está disponível):
  1. gera o DDL SQLite a partir das @Entity (mesmas regras de nomes do Room);
  2. valida cada @Query com EXPLAIN no SQLite (sintaxe, tabelas e colunas);
  3. confirma que cada :parametro existe na assinatura do método;
  4. confirma que as colunas devolvidas cobrem os campos do tipo de retorno.
Uso: python3 room_check.py <dir java> <saida schema.sql>
"""
import os, re, sys, sqlite3

root = sys.argv[1]
out_sql = sys.argv[2]
ent_dir = os.path.join(root, "com/pastelgest/app/data/entity")
dao_dir = os.path.join(root, "com/pastelgest/app/data/dao")
model_dir = os.path.join(root, "com/pastelgest/app/data/model")

def fields_of(path):
    src = open(path, encoding="utf-8").read()
    body = src[src.index("{", src.index(" class ")):]
    out = []
    pending = []
    for m in re.finditer(r'(@\w+(?:\([^)]*\))?)|public\s+(?!static)(?:final\s+)?([\w.<>]+)\s+(\w+)\s*(?:=[^;]+)?;', body):
        if m.group(1):
            pending.append(m.group(1)); continue
        out.append((m.group(2), m.group(3), pending)); pending = []
    ext = re.search(r'class\s+\w+\s+extends\s+(\w+)', src)
    return src, out, ext.group(1) if ext else None

entities = {}
for f in sorted(os.listdir(ent_dir)):
    src, flds, parent = fields_of(os.path.join(ent_dir, f))
    em = re.search(r'@Entity\(tableName = "(\w+)"(.*?)\)\npublic class (\w+)', src, re.S)
    if not em:
        continue
    table, rest, cls = em.group(1), em.group(2), em.group(3)
    allf = list(flds)
    if parent:
        _, pf, _ = fields_of(os.path.join(ent_dir, parent + ".java"))
        allf = allf + pf
    entities[cls] = dict(table=table, fields=allf, rest=rest)

ddl = []
for cls, e in entities.items():
    cols, pk = [], None
    for t, n, ann in e["fields"]:
        sqlt = {"long": "INTEGER NOT NULL", "int": "INTEGER NOT NULL", "String": "TEXT", "double": "REAL NOT NULL"}[t]
        if any(a.startswith("@PrimaryKey") for a in ann):
            pk = n
            if "autoGenerate = true" in " ".join(ann):
                cols.append(f"`{n}` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"); pk = None; continue
            if t == "String": sqlt = "TEXT NOT NULL"
        cols.append(f"`{n}` {sqlt}")
    if pk: cols.append(f"PRIMARY KEY(`{pk}`)")
    for fk in re.finditer(r'@ForeignKey\(entity = (\w+)\.class, parentColumns = "(\w+)", childColumns = "(\w+)"\)', e["rest"]):
        cols.append(f"FOREIGN KEY(`{fk.group(3)}`) REFERENCES `{entities[fk.group(1)]['table']}`(`{fk.group(2)}`) ON UPDATE NO ACTION ON DELETE NO ACTION")
    ddl.append(f"CREATE TABLE IF NOT EXISTS `{e['table']}` ({', '.join(cols)});")
    for ix in re.finditer(r'@Index\((?:value = )?(\{[^}]*\}|"\w+")(, unique = true)?\)', e["rest"]):
        names = re.findall(r'"(\w+)"', ix.group(1))
        uniq = "UNIQUE " if ix.group(2) else ""
        ddl.append(f"CREATE {uniq}INDEX IF NOT EXISTS `index_{e['table']}_{'_'.join(names)}` ON `{e['table']}` ({', '.join('`'+n+'`' for n in names)});")
    # FK sem índice gera aviso no Room
    fkcols = re.findall(r'childColumns = "(\w+)"', e["rest"])
    idxcols = [re.findall(r'"(\w+)"', m.group(1))[0] for m in re.finditer(r'@Index\((?:value = )?(\{[^}]*\}|"\w+")', e["rest"])]
    for c in fkcols:
        if c not in idxcols: print(f"AVISO: {cls}.{c} é chave estrangeira sem índice")

con = sqlite3.connect(":memory:")
con.execute("PRAGMA foreign_keys=ON")
for s in ddl: con.execute(s)
open(out_sql, "w").write("\n".join(ddl) + "\n")

models = {}
for f in os.listdir(model_dir):
    _, flds, _ = fields_of(os.path.join(model_dir, f))
    models[f[:-5]] = flds

def expected_columns(typename):
    if typename in entities:
        return {n for t, n, a in entities[typename]["fields"]}
    if typename in models:
        cols = set()
        for t, n, a in models[typename]:
            if any(x.startswith("@Embedded") for x in a): cols |= expected_columns(t)
            else: cols.add(n)
        return cols
    return None

errors, nq = [], 0
for f in sorted(os.listdir(dao_dir)):
    src = open(os.path.join(dao_dir, f), encoding="utf-8").read()
    for m in re.finditer(r'@Query\("((?:[^"\\]|\\.)*)"\)\s*\n\s*([\w.<>]+)\s+(\w+)\(([^)]*)\);', src):
        q, ret, name, params = m.group(1).replace('\\"', '"'), m.group(2), m.group(3), m.group(4)
        nq += 1
        pnames = [p.strip().split()[-1] for p in params.split(",") if p.strip()]
        used = re.findall(r':(\w+)', q)
        for u in used:
            if u not in pnames: errors.append(f"{f}.{name}: parâmetro :{u} não existe na assinatura")
        for p in pnames:
            if p not in used: errors.append(f"{f}.{name}: parâmetro {p} não usado na query")
        sql = re.sub(r':\w+', '?', q)
        try:
            cur = con.execute("EXPLAIN " + sql, [1] * sql.count("?"))
            cur.fetchall()
            if sql.lstrip().upper().startswith("SELECT"):
                cur = con.execute(sql, [1] * sql.count("?"))
                got = {d[0] for d in cur.description}
                inner = re.sub(r'List<(\w+)>', r'\1', ret)
                exp = expected_columns(inner)
                if exp is not None:
                    missing = exp - got
                    extra = got - exp
                    if missing: errors.append(f"{f}.{name}: colunas em falta para {inner}: {sorted(missing)}")
                    if extra: errors.append(f"{f}.{name}: colunas sem campo em {inner}: {sorted(extra)}")
                elif len(got) != 1:
                    errors.append(f"{f}.{name}: retorno {ret} mas a query devolve {len(got)} colunas")
        except sqlite3.Error as e:
            errors.append(f"{f}.{name}: SQL inválido: {e}\n    {sql}")
print(f"Entidades: {len(entities)} | Tabelas: {len([d for d in ddl if d.startswith('CREATE TABLE')])} | Índices: {len([d for d in ddl if 'INDEX' in d])} | Queries verificadas: {nq}")
if errors:
    print("\n".join(errors)); sys.exit(1)
print("Room: todas as queries são válidas.")
