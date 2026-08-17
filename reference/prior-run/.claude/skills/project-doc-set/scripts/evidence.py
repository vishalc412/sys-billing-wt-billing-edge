#!/usr/bin/env python3
"""Gather the evidence a document set is written from. Run this FIRST, always.

One command, no decision to get wrong. It looks for a `.crib/` code graph and
queries it; failing that it scans the repo. Either way it writes
`<out-dir>/evidence.md`, and nothing downstream should be drafted without it.

    python evidence.py <repo> --out-dir docs/<slug>/.evidence [--feature FLL]

Why this is mandatory: a document written from filenames describes a system
nobody built. The graph knows which method calls which, which class owns which
table, and where errors are raised and caught. That is the difference between
an HLD a reviewer trusts and one they correct.

Exit codes: 0 evidence written · 3 nothing found (repo path wrong?)
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import sqlite3
import subprocess
import sys
from collections import Counter, defaultdict, deque
from pathlib import Path

TRAVERSE = ("calls", "member-of", "owned-by", "references", "implements",
            "inherits", "handles", "writes", "reads", "renders")


def find_db(repo: Path) -> Path | None:
    for rel in (".crib/index/crib.sqlite", ".crib/index/composite/crib.sqlite",
                ".crib/crib.sqlite"):
        p = repo / rel
        if p.exists():
            return p
    hits = sorted((repo / ".crib").rglob("crib.sqlite")) if (repo / ".crib").is_dir() else []
    return hits[0] if hits else None


# Path noise that carries no architectural meaning. Stripping it is what turns
# a component name from "FTCCloud" into "fll/schedule".
NOISE = ("src", "main", "java", "javascript", "typescript", "resources", "kotlin",
         "org", "com", "net", "io", "firstinspires", "ftc", "common", "test")


def module_of(path: str | None, depth: int = 1) -> str:
    """Component name for a file.

    In a monorepo one top-level module usually holds everything, so grouping at
    depth 1 collapses the whole feature into a single box and the diagram says
    nothing. Deeper segments — the package the code actually lives in — are what
    a reader recognises as components.
    """
    if not path:
        return "(unknown)"
    parts = [p for p in Path(path).parts if p]
    if not parts:
        return "(unknown)"
    if depth <= 1:
        return parts[0]
    root, rest = parts[0], parts[1:-1]  # drop the filename
    meaningful = [p for p in rest if p.lower() not in NOISE]
    if not meaningful:
        return root
    return "/".join(meaningful[: depth - 1]) or root


def pick_depth(paths: list[str]) -> int:
    """Use the shallowest grouping that does not put most of the slice in one box."""
    for d in (1, 2, 3):
        groups = Counter(module_of(p, d) for p in paths)
        if not groups:
            continue
        top = groups.most_common(1)[0][1]
        if len(groups) >= 3 and top <= 0.7 * sum(groups.values()):
            return d
    return 3


def loc(j: dict) -> str:
    f = j.get("file") or ""
    span = j.get("span") or {}
    return f"{f}:{span.get('start')}" if f and span.get("start") else f


class Crib:
    def __init__(self, db_path: Path):
        self.db = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
        self.db.row_factory = sqlite3.Row
        self._node_cache: dict[str, dict] = {}

    def node(self, nid: str) -> dict | None:
        if nid not in self._node_cache:
            row = self.db.execute("SELECT json FROM nodes WHERE id=?", (nid,)).fetchone()
            self._node_cache[nid] = json.loads(row[0]) if row else None
        return self._node_cache[nid]

    def seeds(self, feature: str, limit: int) -> set[str]:
        """Anything whose name, qualified name, or path carries the feature term.

        Path matching matters as much as name matching: a feature usually lives
        in a package or directory, and its helper classes rarely repeat the term.
        """
        like = f"%{feature.lower()}%"
        found: set[str] = set()
        q = ("SELECT id, json FROM nodes WHERE kind IN "
             "('symbol','field','table','column','http-call','file') "
             "AND (lower(file) LIKE ? OR lower(name) LIKE ?) LIMIT ?")
        for row in self.db.execute(q, (like, like, limit * 4)):
            j = json.loads(row["json"])
            hay = " ".join(str(j.get(k, "")) for k in ("name", "qualifiedName", "file")).lower()
            if feature.lower() in hay:
                found.add(row["id"])
        if len(found) < limit // 4:  # sparse term — let full-text widen it
            try:
                for row in self.db.execute(
                        "SELECT id FROM nodes_fts WHERE nodes_fts MATCH ? LIMIT ?",
                        (feature, limit)):
                    if row["id"]:
                        found.add(row["id"])
            except sqlite3.Error:
                pass
        return set(list(found)[: limit * 4])

    def expand(self, seeds: set[str], hops: int, cap: int) -> set[str]:
        """Follow structural edges outward. Two hops reaches collaborators
        without dragging in the entire framework."""
        seen, frontier = set(seeds), deque((s, 0) for s in seeds)
        placeholders = ",".join("?" * len(TRAVERSE))
        while frontier and len(seen) < cap:
            nid, d = frontier.popleft()
            if d >= hops:
                continue
            for col_a, col_b in (("src", "dst"), ("dst", "src")):
                q = (f"SELECT {col_b} AS other FROM edges "
                     f"WHERE {col_a}=? AND rel IN ({placeholders})")
                for row in self.db.execute(q, (nid, *TRAVERSE)):
                    o = row["other"]
                    if o and o not in seen:
                        seen.add(o)
                        frontier.append((o, d + 1))
                        if len(seen) >= cap:
                            break
        return seen

    def edges_within(self, ids: set[str], rel: str) -> list[tuple[str, str]]:
        out = []
        for row in self.db.execute("SELECT src, dst FROM edges WHERE rel=?", (rel,)):
            if row["src"] in ids and row["dst"] in ids:
                out.append((row["src"], row["dst"]))
        return out


def build_slice(crib: Crib, feature: str, hops: int, cap: int) -> dict:
    seeds = crib.seeds(feature, cap)
    ids = crib.expand(seeds, hops, cap)

    by_kind: dict[str, list[dict]] = defaultdict(list)
    for nid in ids:
        j = crib.node(nid)
        if j:
            by_kind[j.get("kind", "?")].append(j)

    all_paths = [j.get("file") for v in by_kind.values() for j in v if j.get("file")]
    depth = pick_depth(all_paths)
    modules = Counter(module_of(p, depth) for p in all_paths)
    calls = crib.edges_within(ids, "calls")

    # component -> component call traffic, which is the component view
    comp = Counter()
    for s, d in calls:
        a, b = crib.node(s), crib.node(d)
        if a and b:
            ma, mb = module_of(a.get("file"), depth), module_of(b.get("file"), depth)
            if ma != mb:
                comp[(ma, mb)] += 1

    # entry points: http-calls in the slice, plus symbols nothing in-slice calls
    called = {d for _, d in calls}
    entries = [j for j in by_kind.get("http-call", [])]
    roots = [j for j in by_kind.get("symbol", [])
             if j["id"] not in called and j.get("type") in (None, "method", "function")]

    tables = by_kind.get("table", [])
    tnames = {t.get("name") for t in tables}
    cols = [c for c in by_kind.get("column", []) if c.get("table") in tnames]

    chains = []
    call_map = defaultdict(list)
    for s, d in calls:
        call_map[s].append(d)
    def owner(j: dict) -> str:
        qn = j.get("qualifiedName") or j.get("name") or ""
        return qn.split(".")[0] or Path(j.get("file") or "?").stem

    for r in roots[:40]:
        chain, cur, guard = [], r["id"], 0
        while cur and guard < 6:
            j = crib.node(cur)
            if not j:
                break
            chain.append(j)
            # Prefer a callee in a different class: a sequence diagram of one
            # class talking to itself tells the reader nothing.
            nxt = call_map.get(cur, [])
            here = owner(j)
            cross = [n for n in nxt if (crib.node(n) or {}) and owner(crib.node(n) or {}) != here]
            cur = (cross or nxt or [None])[0]
            guard += 1
        if len({owner(j) for j in chain}) > 1:
            chains.append(chain)
    # Richest interactions first — most distinct participants, then longest.
    chains.sort(key=lambda ch: (-len({owner(j) for j in ch}), -len(ch)))

    return {
        "feature": feature,
        "counts": {k: len(v) for k, v in sorted(by_kind.items(), key=lambda kv: -len(kv[1]))},
        "modules": modules.most_common(),
        "component_edges": [{"from": a, "to": b, "calls": n} for (a, b), n in comp.most_common(40)],
        "entry_points": entries[:60],
        "roots": roots[:40],
        "symbols": by_kind.get("symbol", [])[:150],
        "tables": tables[:60],
        "columns": cols[:200],
        "http_calls": by_kind.get("http-call", [])[:60],
        "raises": by_kind.get("raise", [])[:60],
        "handlers": by_kind.get("exception-handler", [])[:60],
        "conditions": by_kind.get("condition", [])[:40],
        "explanations": by_kind.get("explanation", [])[:30],
        "call_chains": [[{"name": j.get("qualifiedName") or j.get("name"), "at": loc(j)}
                         for j in ch] for ch in chains[:10]],
        "slice_size": len(ids),
    }


def render_md(s: dict) -> str:
    L = [f"# Evidence slice — {s['feature']}", "",
         f"{s['slice_size']} graph nodes. Every line carries `file:line`; open only what you must.", ""]

    L += ["## Modules involved", ""]
    L += [f"- `{m}` — {n} nodes" for m, n in s["modules"][:15]]

    if s["component_edges"]:
        L += ["", "## Cross-module call traffic", "",
              "| From | To | Calls |", "|---|---|---:|"]
        L += [f"| `{e['from']}` | `{e['to']}` | {e['calls']} |" for e in s["component_edges"][:20]]

    if s["http_calls"]:
        L += ["", "## HTTP surface", ""]
        L += [f"- `{h.get('httpMethod','')} {h.get('routePath','')}` "
              f"({h.get('framework','')}) — {loc(h)}" for h in s["http_calls"][:30]]

    if s["call_chains"]:
        L += ["", "## Call chains (raw material for sequence diagrams)", ""]
        for ch in s["call_chains"]:
            L.append("- " + " → ".join(f"`{c['name']}`" for c in ch))
            L.append(f"  - starts at {ch[0]['at']}")

    if s["tables"]:
        L += ["", "## Data", ""]
        bycol = defaultdict(list)
        for c in s["columns"]:
            bycol[c.get("table")].append(f"{c.get('name')} {c.get('dataType','')}".strip())
        for t in s["tables"][:20]:
            L.append(f"- **{t.get('name')}** — {loc(t)}")
            for col in bycol.get(t.get("name"), [])[:12]:
                L.append(f"  - {col}")

    if s["handlers"] or s["raises"]:
        L += ["", "## Failure handling", "",
              "_What the code actually does on error — the LLD section most often missing._", ""]
        L += [f"- catches `{h.get('whenSelector')}` — {loc(h)}" for h in s["handlers"][:20]]
        L += [f"- raises `{r.get('name')}`"
              + (f': "{str(r.get("errorMessage"))[:60]}"' if r.get("errorMessage") else "")
              + f" — {loc(r)}" for r in s["raises"][:20]]

    if s["conditions"]:
        L += ["", "## Guards on these paths", ""]
        for c in s["conditions"][:15]:
            expr = re.sub(r"\s+", " ", str(c.get("expr") or "")).strip()[:100]
            L.append(f"- `{expr}` — {loc(c)}")

    if s["explanations"]:
        L += ["", "## Existing code commentary", ""]
        L += [f"- {loc(e)}" for e in s["explanations"][:15]]

    L += ["", "## Node counts", "", ", ".join(f"{k}={v}" for k, v in s["counts"].items())]
    return "\n".join(L) + "\n"


def render_mermaid(s: dict) -> str:
    ids: dict[str, str] = {}

    def nid(label: str) -> str:
        if label not in ids:
            ids[label] = "n%d" % len(ids)
        return ids[label]

    out = [f"# Generated diagrams — {s['feature']}", "",
           "Generated from the code graph, so they reflect what is built. Prune to what",
           "is load-bearing before publishing — a diagram of everything explains nothing.", ""]

    if s["component_edges"]:
        out += ["## Component view", "", "```mermaid",
                f"%% caption: Figure 1 — {s['feature']} component view",
                "flowchart TB"]
        mods = {e["from"] for e in s["component_edges"][:15]} | \
               {e["to"] for e in s["component_edges"][:15]}
        for m in sorted(mods):
            out.append(f'  {nid(m)}["{m}"]')
        for e in s["component_edges"][:15]:
            out.append(f'  {nid(e["from"])} -->|{e["calls"]} calls| {nid(e["to"])}')
        out += ["```", ""]

    for i, ch in enumerate(s["call_chains"][:3], start=2):
        out += [f"## Flow {i - 1}", "", "```mermaid",
                f"%% caption: Figure {i} — {ch[0]['name']} flow", "sequenceDiagram"]
        parts = []
        for c in ch:
            p = (c["name"] or "?").split(".")[0][:24]
            if p not in parts:
                parts.append(p)
                out.append(f"  participant {nid(p)} as {p}")
        for a, b in zip(ch, ch[1:]):
            pa = (a["name"] or "?").split(".")[0][:24]
            pb = (b["name"] or "?").split(".")[0][:24]
            meth = (b["name"] or "?").split(".")[-1][:30]
            out.append(f"  {nid(pa)}->>{nid(pb)}: {meth}")
        out += ["```", ""]

    if s["tables"]:
        out += ["## Data model", "", "```mermaid",
                f"%% caption: Figure — {s['feature']} data model", "erDiagram"]
        bycol = defaultdict(list)
        for c in s["columns"]:
            bycol[c.get("table")].append(c)
        for t in s["tables"][:8]:
            name = re.sub(r"\W", "_", str(t.get("name")))
            out.append(f"  {name} {{")
            for c in bycol.get(t.get("name"), [])[:10]:
                dt = re.sub(r"\W+", "_", str(c.get("dataType") or "text"))[:20]
                cn = re.sub(r"\W", "_", str(c.get("name")))
                out.append(f"    {dt} {cn}")
            out.append("  }")
        out += ["```", ""]
    return "\n".join(out)




SKIP_DIRS = {
    ".git", ".hg", ".svn", "node_modules", "venv", ".venv", "env", "__pycache__",
    "dist", "build", "target", "out", "bin", "obj", ".idea", ".vscode", ".gradle",
    ".mvn", "vendor", "coverage", ".pytest_cache", ".mypy_cache", ".tox", ".next",
    "site-packages", ".terraform", "migrations_backup",
}
SKIP_SUFFIX = {
    ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".pdf", ".zip", ".tar", ".gz",
    ".jar", ".war", ".class", ".pyc", ".so", ".dll", ".dylib", ".woff", ".woff2",
    ".ttf", ".eot", ".mp4", ".lock", ".min.js", ".map",
}
MAX_BYTES = 400_000

MANIFESTS = {
    "pyproject.toml": "Python", "requirements.txt": "Python", "setup.py": "Python",
    "package.json": "JavaScript/TypeScript", "pom.xml": "Java (Maven)",
    "build.gradle": "Java/Kotlin (Gradle)", "build.gradle.kts": "Java/Kotlin (Gradle)",
    "go.mod": "Go", "Cargo.toml": "Rust", "Gemfile": "Ruby", "composer.json": "PHP",
    "mix.exs": "Elixir", "pubspec.yaml": "Dart",
}
INFRA_FILES = (
    "dockerfile", "docker-compose", "kubernetes", "k8s", "helm", "chart.yaml",
    "terraform", ".tf", "serverless", "cloudformation", "jenkinsfile", ".gitlab-ci",
    "buildspec", "skaffold", "kustomization",
)

# label -> (pattern, which languages it applies to). Kept deliberately shallow:
# the goal is a map, not a parse tree.
PATTERNS: dict[str, list[tuple[str, str]]] = {
    "entrypoint": [
        (r'@(?:app|router)\.(get|post|put|patch|delete)\(\s*["\']([^"\']+)', "py"),
        (r'@(?:Get|Post|Put|Patch|Delete)Mapping\(\s*(?:value\s*=\s*)?["\']([^"\']+)', "java"),
        (r'@RequestMapping\(\s*(?:value\s*=\s*)?["\']([^"\']+)', "java"),
        (r'@(?:RestController|Controller)\b', "java"),
        (r'\b(?:app|router)\.(get|post|put|patch|delete)\(\s*["\']([^"\']+)', "js"),
        (r'@(?:Get|Post|Put|Patch|Delete)\(\s*["\']?([^"\')]*)', "js"),
        (r'http\.HandleFunc\(\s*"([^"]+)', "go"),
        (r'@(?:KafkaListener|RabbitListener|SqsListener|StreamListener)\b[^)]{0,120}', "java"),
        (r'@(?:Scheduled|EventListener)\b[^)]{0,80}', "java"),
        (r'def\s+(?:main|handler|lambda_handler)\s*\(', "py"),
        (r'\bfunc\s+main\s*\(', "go"),
        (r'public\s+static\s+void\s+main\s*\(', "java"),
    ],
    "data": [
        (r'class\s+(\w+)\s*\([^)]*(?:Base|Model|db\.Model)[^)]*\)', "py"),
        (r'__tablename__\s*=\s*["\'](\w+)', "py"),
        (r'@(?:Entity|Table|Document)\b[^)]{0,80}', "java"),
        (r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`"]?(\w+)', "sql"),
        (r'ALTER\s+TABLE\s+[`"]?(\w+)[`"]?\s+(\w+)', "sql"),
        (r'CREATE\s+(?:UNIQUE\s+)?INDEX\s+[`"]?(\w+)', "sql"),
        (r'@(?:Column|Id|GeneratedValue|ManyToOne|OneToMany)\b', "java"),
    ],
    "integration": [
        (r'(?:httpx|requests|aiohttp)\.(get|post|put|patch|delete)\(', "py"),
        (r'(?:RestTemplate|WebClient|HttpClient|Feign)\b', "java"),
        (r'\b(?:fetch|axios)\s*[\.\(]', "js"),
        (r'(?:producer|consumer)\.(?:send|publish|subscribe)\w*\(\s*["\']?([\w.\-]*)', "any"),
        (r'(?:topic|queue|exchange)\s*[:=]\s*["\']([\w.\-]+)', "any"),
        (r'@FeignClient\([^)]{0,120}', "java"),
    ],
    "config": [
        (r'os\.(?:getenv|environ(?:\.get)?)\(\s*["\'](\w+)', "py"),
        (r'System\.getenv\(\s*"(\w+)', "java"),
        (r'@Value\(\s*"\$\{([^}:]+)', "java"),
        (r'process\.env\.(\w+)', "js"),
        (r'os\.Getenv\(\s*"(\w+)', "go"),
        (r'^\s*([A-Z][A-Z0-9_]{3,})\s*[:=]', "env"),
    ],
    "guardrail": [
        (r'\b(?:UNIQUE|PRIMARY\s+KEY|FOREIGN\s+KEY|NOT\s+NULL|CHECK)\b', "sql"),
        (r'@(?:Transactional|Retryable|CircuitBreaker|RateLimiter|Valid|PreAuthorize)\b', "java"),
        (r'\b(?:timeout|retries|retry|backoff|max_attempts|deadline)\s*[:=]\s*([\w.]+)', "any"),
        (r'\bunique\s*=\s*True', "py"),
        (r'\bwith\s+(?:session|transaction|uow)\b', "py"),
    ],
}
TEST_PATTERNS = [
    (r'def\s+(test_\w+)', "py"),
    (r'@Test\b[\s\S]{0,80}?\b(?:public|void)\s+(\w+)\s*\(', "java"),
    (r'\b(?:it|test)\s*\(\s*["\'`]([^"\'`]{4,80})', "js"),
    (r'\bfunc\s+(Test\w+)', "go"),
]

EXT_LANG = {
    ".py": "py", ".java": "java", ".kt": "java", ".scala": "java",
    ".js": "js", ".jsx": "js", ".ts": "js", ".tsx": "js",
    ".go": "go", ".rb": "rb", ".rs": "rs", ".cs": "cs", ".php": "php",
    ".sql": "sql", ".ddl": "sql",
    ".env": "env", ".properties": "env", ".yaml": "env", ".yml": "env", ".toml": "env",
}


def language_of(p: Path) -> str:
    return EXT_LANG.get(p.suffix.lower(), "any")


def iter_files(root: Path, scope: list[Path] | None):
    bases = scope or [root]
    for base in bases:
        if base.is_file():
            yield base
            continue
        for p in base.rglob("*"):
            if not p.is_file():
                continue
            if any(part in SKIP_DIRS for part in p.parts):
                continue
            if p.suffix.lower() in SKIP_SUFFIX or p.name.endswith(".min.js"):
                continue
            try:
                if p.stat().st_size > MAX_BYTES:
                    continue
            except OSError:
                continue
            yield p


def changed_files(root: Path, since: str) -> list[Path]:
    try:
        r = subprocess.run(["git", "-C", str(root), "diff", "--name-only", f"{since}..HEAD"],
                           capture_output=True, text=True, timeout=30)
        if r.returncode != 0:
            return []
        return [root / line.strip() for line in r.stdout.splitlines() if line.strip()]
    except (OSError, subprocess.TimeoutExpired):
        return []


def scan_fallback(root: Path, scope: list[Path] | None, since: str | None) -> dict:
    out: dict = {
        "root": str(root),
        "stack": [],
        "layout": [],
        "entrypoint": [], "data": [], "integration": [],
        "config": [], "guardrail": [], "tests": [],
        "infra": [], "docs": [], "crib": None,
        "counts": {}, "unscanned_note": None,
    }

    if (root / ".crib" / "INDEX.md").exists():
        out["crib"] = ".crib/INDEX.md"
    elif (root / ".crib").is_dir():
        out["crib"] = ".crib/ (no INDEX.md)"

    files = None
    if since:
        files = [f for f in changed_files(root, since) if f.exists()]
        if not files:
            out["unscanned_note"] = f"no changes found since {since}; fell back to full scan"
            files = None
    file_list = files if files is not None else list(iter_files(root, scope))
    if files is not None:
        out["unscanned_note"] = out["unscanned_note"] or f"delta scan: {len(files)} file(s) changed since {since}"

    # Stack, from manifests.
    for p in file_list:
        if p.name in MANIFESTS:
            entry = f"{MANIFESTS[p.name]} — {p.relative_to(root)}"
            if entry not in out["stack"]:
                out["stack"].append(entry)
            try:
                text = p.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            for dep in re.findall(r'["\']?([a-zA-Z][\w.\-]{2,40})["\']?\s*[=:><^~]{1,3}\s*["\']?v?(\d+\.\d+[\w.\-]*)',
                                  text)[:40]:
                out["stack"].append(f"  {dep[0]}=={dep[1]}")

    # Directory shape — depth 2 with file counts, which is enough to see the layering.
    dirs: dict[str, int] = defaultdict(int)
    for p in file_list:
        try:
            rel = p.relative_to(root)
        except ValueError:
            continue
        parent = rel.parent
        dirs["/".join(parent.parts[:2]) if parent.parts else "(root)"] += 1
    out["layout"] = [f"{k}/  ({v} files)" for k, v in sorted(dirs.items(), key=lambda kv: -kv[1])[:30]]

    seen: dict[str, set] = defaultdict(set)
    for p in file_list:
        lang = language_of(p)
        try:
            text = p.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        rel = str(p.relative_to(root)) if p.is_relative_to(root) else str(p)
        low = rel.lower()

        if any(tok in low for tok in INFRA_FILES):
            out["infra"].append(rel)
        if low.endswith((".md", ".adoc", ".rst")) and "readme" in low or "/docs/" in low:
            out["docs"].append(rel)

        is_test = "test" in low or "spec" in low
        line_starts = None

        def line_of(idx: int) -> int:
            nonlocal line_starts
            if line_starts is None:
                line_starts = [0]
                for m in re.finditer(r"\n", text):
                    line_starts.append(m.end())
            lo, hi = 0, len(line_starts) - 1
            while lo < hi:
                mid = (lo + hi + 1) // 2
                if line_starts[mid] <= idx:
                    lo = mid
                else:
                    hi = mid - 1
            return lo + 1

        if is_test:
            for pat, plang in TEST_PATTERNS:
                if plang != lang:
                    continue
                for m in re.finditer(pat, text, re.MULTILINE):
                    name = next((g for g in m.groups() if g), "")
                    key = f"{rel}:{name}"
                    if name and key not in seen["tests"]:
                        seen["tests"].add(key)
                        out["tests"].append(f"{rel}:{line_of(m.start())}  {name}")
            continue

        for label, pats in PATTERNS.items():
            for pat, plang in pats:
                if plang not in (lang, "any"):
                    continue
                flags = re.MULTILINE | (re.IGNORECASE if plang == "sql" else 0)
                for m in re.finditer(pat, text, flags):
                    frag = " ".join(g for g in m.groups() if g) or m.group(0)
                    frag = re.sub(r"\s+", " ", frag).strip()[:90]
                    key = f"{label}|{rel}|{frag}"
                    if key in seen[label]:
                        continue
                    seen[label].add(key)
                    out[label].append(f"{rel}:{line_of(m.start())}  {frag}")

    for k in ("entrypoint", "data", "integration", "config", "guardrail", "tests",
              "infra", "docs", "stack"):
        out["counts"][k] = len(out[k])
        # Cap each section: past this, the digest stops being cheaper than reading.
        cap = 60 if k in ("entrypoint", "data") else 40
        if len(out[k]) > cap:
            out[k] = out[k][:cap] + [f"... +{len(out[k]) - cap} more (raise with --max)"]
    return out


def render_scan(d: dict) -> str:
    L = [f"# Repo digest — {d['root']}"]
    if d.get("crib"):
        L.append(f"\n**Crib present:** `{d['crib']}` — read it before trusting anything below.")
    if d.get("unscanned_note"):
        L.append(f"\n_{d['unscanned_note']}_")

    def section(title: str, key: str, note: str = "") -> None:
        items = d.get(key) or []
        if not items:
            return
        L.append(f"\n## {title}" + (f"\n_{note}_" if note else ""))
        L.extend(f"- {i}" for i in items)

    section("Stack", "stack")
    section("Layout", "layout")
    section("Entry points", "entrypoint", "HTTP routes, consumers, jobs, mains")
    section("Data model", "data", "entities, tables, migrations, indexes")
    section("Integrations", "integration", "outbound calls, topics, queues")
    section("Configuration", "config", "env vars and injected settings")
    section("Guardrails", "guardrail", "constraints, transactions, retries, timeouts — "
                                       "note what is ABSENT here, that is usually the finding")
    section("Tests", "tests")
    section("Infrastructure", "infra")
    section("Existing docs", "docs")
    L.append("\n## Coverage\n" + ", ".join(f"{k}={v}" for k, v in d["counts"].items()))
    L.append("\nOpen only the files this digest makes you curious about.")
    return "\n".join(L)




PLATFORM_PROBES = [
    ("Build", ["build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
               "pom.xml", "package.json", "pyproject.toml", "go.mod", "Cargo.toml"]),
    ("Containerisation", ["Dockerfile", "docker-compose.yml", "docker-compose.yaml", ".dockerignore"]),
    ("Orchestration", ["Chart.yaml", "kustomization.yaml", "values.yaml", "skaffold.yaml"]),
    ("CI/CD", ["Jenkinsfile", ".gitlab-ci.yml", "buildspec.yml"]),
    ("Runtime pin", [".tool-versions", ".java-version", ".nvmrc", "gradle.properties"]),
]


def platform_baseline(repo: Path) -> str:
    """What the feature sits on: build, runtime, database, deployment.

    A feature slice deliberately excludes this, which is how an HLD ends up
    describing a component without ever naming the platform, database or build
    system underneath it. Always gather it, whatever the scope.
    """
    L = ["## Platform baseline", "",
         "_The ground the feature stands on. An HLD that omits this describes a "
         "component floating in space._", ""]
    seen: set[str] = set()
    for label, names in PLATFORM_PROBES:
        hits = []
        for n in names:
            for p in list(repo.glob(n)) + list(repo.glob(f"*/{n}")) + list(repo.glob(f"*/*/{n}")):
                rel = str(p.relative_to(repo))
                if "/build/" in rel or "node_modules" in rel or rel in seen:
                    continue
                seen.add(rel)
                hits.append(rel)
        if hits:
            L.append(f"- **{label}:** " + ", ".join(f"`{h}`" for h in sorted(hits)[:6])
                     + (f" (+{len(hits) - 6} more)" if len(hits) > 6 else ""))

    # database engine + migration tooling, inferred from migration files
    migs = [p for p in repo.rglob("*/db/migration/*.sql") if "/build/" not in str(p)][:400]
    if not migs:
        migs = [p for p in repo.rglob("*migrations/*.sql") if "/build/" not in str(p)][:400]
    if migs:
        tool = "Flyway" if any(re.match(r"V[\d.]+__", p.name) for p in migs) else "SQL migrations"
        L.append(f"- **Schema migrations:** {tool}, {len(migs)} file(s), "
                 f"e.g. `{migs[0].relative_to(repo)}`")
        blob = " ".join(p.read_text(errors="ignore")[:2500] for p in migs[:60]).upper()
        for engine, mark in (("MySQL/MariaDB", "ENGINE="), ("PostgreSQL", "SERIAL"),
                             ("PostgreSQL", "JSONB"), ("SQL Server", "NVARCHAR")):
            if mark in blob:
                L.append(f"- **Database dialect:** {engine} (inferred from `{mark}` in migrations)")
                break

    # framework + version, straight out of the dependency declarations
    deps = []
    FW = (r"javalin|spring-boot|springframework|react|vue|express|django|flask|fastapi|"
          r"quarkus|micronaut|hibernate|jooq|jetty|ktor|next|angular")
    for man in (list(repo.glob("*/build.gradle*")) + list(repo.glob("build.gradle*"))
                + list(repo.glob("*/pom.xml")) + list(repo.glob("*/package.json"))
                + list(repo.glob("gradle/libs.versions.toml"))):
        if "/build/" in str(man) or "node_modules" in str(man):
            continue
        try:
            txt = man.read_text(errors="ignore")
        except OSError:
            continue
        # Gradle/Maven coordinate in one string: "io.javalin:javalin:6.1.3"
        for m in re.finditer(r'([\w.\-]*(?:' + FW + r')[\w.\-]*):(\d+\.\d+[\w.\-]*)', txt, re.I):
            deps.append(f"{m.group(1).split(':')[-1]} {m.group(2)}")
        # version catalog / package.json: name = "1.2.3"
        for m in re.finditer(r'"?([\w.\-]*(?:' + FW + r')[\w.\-]*)"?\s*[:=]\s*"\^?~?v?(\d+\.\d+[\w.\-]*)"',
                             txt, re.I):
            deps.append(f"{m.group(1)} {m.group(2)}")
    if deps:
        L.append("- **Frameworks:** " + ", ".join(sorted(set(deps))[:10]))

    if len(L) <= 4:
        L.append("- _nothing detected — state this as an open question rather than guessing._")
    return "\n".join(L) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("repo", type=Path)
    ap.add_argument("--feature", help="narrow to a feature area, e.g. FLL, billing")
    ap.add_argument("--out-dir", type=Path, default=Path(".evidence"))
    ap.add_argument("--hops", type=int, default=2)
    ap.add_argument("--cap", type=int, default=1200)
    ap.add_argument("--since", help="scan mode: only files changed since this git ref")
    a = ap.parse_args()

    repo = a.repo.resolve()
    if not repo.exists():
        print(f"no such path: {repo}", file=sys.stderr)
        return 3
    a.out_dir.mkdir(parents=True, exist_ok=True)

    db_path = find_db(repo)
    if db_path and a.feature:
        print(f"source: crib code graph ({db_path})")
        s = build_slice(Crib(db_path), a.feature, a.hops, a.cap)
        md = render_md(s)
        (a.out_dir / "evidence.json").write_text(json.dumps(s, indent=2), encoding="utf-8")
        (a.out_dir / "diagrams.md").write_text(render_mermaid(s), encoding="utf-8")
        md = platform_baseline(repo) + "\n" + md
        (a.out_dir / "evidence.md").write_text(md, encoding="utf-8")
        print(f"slice: {s['slice_size']} nodes, {len(s['modules'])} components")
    elif db_path:
        print(f"source: crib code graph ({db_path}) — whole system")
        s = build_slice(Crib(db_path), "", a.hops, a.cap * 2)
        md = render_md(s)
        (a.out_dir / "evidence.json").write_text(json.dumps(s, indent=2), encoding="utf-8")
        (a.out_dir / "diagrams.md").write_text(render_mermaid(s), encoding="utf-8")
        (a.out_dir / "evidence.md").write_text(platform_baseline(repo) + "\n" + md, encoding="utf-8")
    else:
        print("source: repo scan (no .crib code graph found)")
        d = scan_fallback(repo, None, a.since)
        md = platform_baseline(repo) + "\n" + render_scan(d)
        (a.out_dir / "evidence.json").write_text(json.dumps(d, indent=2), encoding="utf-8")
        (a.out_dir / "evidence.md").write_text(md, encoding="utf-8")

    print(f"wrote {a.out_dir}/evidence.md ({len(md)} chars)")
    print("Read it before drafting. Do not draft without it.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
