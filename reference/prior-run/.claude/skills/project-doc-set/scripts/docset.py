#!/usr/bin/env python3
"""Produce the document artifacts: diagrams, then Word.

    python docset.py build docs/*.md --out-dir docs/dist --verify
    python docset.py render docs/*.md --check      # pre-flight diagrams

Markdown stays the single source of truth. Mermaid renders to images for Word
(mermaid-cli when available, a built-in Graphviz/matplotlib renderer otherwise),
pandoc applies the Word template, table grids are repaired, and --verify
rasterises the first pages so the result can actually be looked at.
"""
from __future__ import annotations

import argparse
import re
import json
import re
import shutil
import subprocess
import zipfile
import sys
import textwrap
from pathlib import Path

FENCE_RE = re.compile(
    r"^(?P<indent>[ \t]*)```(?P<info>mermaid[^\n]*)\n(?P<body>.*?)^(?P=indent)```[ \t]*$",
    re.DOTALL | re.MULTILINE,
)
CAPTION_RE = re.compile(r"^\s*%%\s*caption:\s*(.+?)\s*$", re.MULTILINE | re.IGNORECASE)


# --------------------------------------------------------------------------
# helpers
# --------------------------------------------------------------------------
def diagram_kind(code: str) -> str:
    """First meaningful line decides the Mermaid diagram type."""
    for line in code.splitlines():
        s = line.strip()
        if not s or s.startswith("%%"):
            continue
        head = s.split()[0].lower().rstrip(":")
        for kind in (
            "sequencediagram", "erdiagram", "classdiagram", "statediagram-v2",
            "statediagram", "flowchart", "graph", "c4context", "c4container",
            "c4component", "journey", "gantt", "pie", "mindmap", "timeline",
        ):
            if head.startswith(kind):
                return kind
        return head
    return "unknown"


def esc(label: str) -> str:
    return label.replace("\\", "\\\\").replace('"', '\\"')


def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def rm(p: Path) -> None:
    """Best-effort delete. Some mounted output dirs allow create but reject
    unlink with EPERM; losing a temp file there must never fail a build."""
    try:
        p.unlink(missing_ok=True)
    except OSError:
        pass


# --------------------------------------------------------------------------
# external renderers
# --------------------------------------------------------------------------
def render_with_mmdc(code: str, out_png: Path) -> bool:
    exe = shutil.which("mmdc")
    cmds = []
    if exe:
        cmds.append([exe])
    if shutil.which("npx"):
        cmds.append(["npx", "-y", "@mermaid-js/mermaid-cli"])
    if not cmds:
        return False

    src = out_png.with_suffix(".mmd")
    src.write_text(code, encoding="utf-8")
    for base in cmds:
        try:
            r = run(base + ["-i", str(src), "-o", str(out_png),
                            "-b", "white", "-s", "2"], timeout=120)
            if r.returncode == 0 and out_png.exists() and out_png.stat().st_size > 0:
                return True
        except (subprocess.TimeoutExpired, OSError):
            continue
    return False


# --------------------------------------------------------------------------
# offline renderer: graph-shaped diagrams via graphviz
# --------------------------------------------------------------------------
NODE_SHAPES = {
    "[": ("box", "]"), "(": ("box", ")"), "([": ("stadium", "])"),
    "((": ("circle", "))"), "{": ("diamond", "}"), "[[": ("box3d", "]]"),
    "[(": ("cylinder", ")]"), "{{": ("hexagon", "}}"), "[/": ("parallelogram", "/]"),
    "[\\": ("parallelogram", "\\]"), ">": ("cds", "]"),
}
# A node expression is parsed only after arrows have been carved out of the
# line. Doing it the other way round lets the '>' shape delimiter swallow the
# '>' of an arrow, which silently drops nodes and edges.
NODE_DEF_RE = re.compile(
    r'^(?P<id>[A-Za-z0-9_.\-]+)\s*(?P<open>\(\(|\(\[|\[\[|\[\(|\{\{|\[/|\[\\|\[|\(|\{|>)'
    r'(?P<label>.*?)'   # lazy: the closing delimiter must win the tail, so
                        # `DB[(Postgres)]` yields "Postgres", not "Postgres)"
    r'(?P<close>\)\)|\]\)|\]\]|\)\]|\}\}|/\]|\\\]|\]|\)|\})$'
)
# Longest / most specific arrow forms first so '-->' never eats part of '-.->'.
ARROW_RE = re.compile(
    r'(?P<arrow>'
    r'--\s*(?P<mid>[^-|>][^|>]*?)\s*-{1,2}>'   # A -- label --> B
    r'|<-{2,}>|-\.-+>|-\.-+|={2,}>|={2,}|-{2,}>|-{2,}|~~~|<-{2,}'
    r')'
    r'\s*(?:\|(?P<lbl>[^|]*)\|)?\s*'
)

GV_HEADER = (
    'graph [rankdir={rankdir}, splines=ortho, nodesep=0.45, ranksep=0.55, '
    'fontname="Helvetica", bgcolor="white", pad=0.3];\n'
    'node [fontname="Helvetica", fontsize=11, style="filled,rounded", '
    'fillcolor="#EEF3FB", color="#4A6FA5", penwidth=1.2];\n'
    'edge [fontname="Helvetica", fontsize=9, color="#54637A"];\n'
)


def flowchart_to_dot(code: str) -> str | None:
    lines = [l for l in code.splitlines() if l.strip() and not l.strip().startswith("%%")]
    if not lines:
        return None
    header = lines[0].strip()
    m = re.match(r'(?:flowchart|graph)\s+(LR|RL|TD|TB|BT)?', header, re.I)
    rankdir = (m.group(1) or "TB").upper() if m else "TB"
    if rankdir == "TD":
        rankdir = "TB"

    labels: dict[str, tuple[str, str]] = {}
    edges: list[tuple[str, str, str, str]] = []
    clusters: list[tuple[str, list[str]]] = []
    stack: list[tuple[str, list[str]]] = []
    seen: list[str] = []

    def note(nid: str):
        if nid not in seen:
            seen.append(nid)
        if stack:
            stack[-1][1].append(nid)

    for raw in lines[1:]:
        line = raw.strip()
        if line.lower().startswith("subgraph"):
            title = line[len("subgraph"):].strip().strip('"')
            title = re.sub(r'^[A-Za-z0-9_.\-]+\s*\[(.*)\]$', r'\1', title).strip('"')
            stack.append((title or "group", []))
            continue
        if line.lower() == "end":
            if stack:
                clusters.append(stack.pop())
            continue
        if line.startswith(("style ", "classDef ", "class ", "linkStyle ", "click ")):
            continue

        # Split the line into node expressions separated by arrows, so a chain
        # like `A --> B -.->|evt| C` yields three nodes and two edges.
        segments, arrows_found, pos = [], [], 0
        for am in ARROW_RE.finditer(line):
            segments.append(line[pos:am.start()])
            arrows_found.append((am.group("arrow"), am.group("mid"), am.group("lbl")))
            pos = am.end()
        segments.append(line[pos:])

        def resolve(expr: str) -> str | None:
            expr = expr.strip().rstrip(";")
            if not expr:
                return None
            nd = NODE_DEF_RE.match(expr)
            if nd:
                shape = NODE_SHAPES.get(nd.group("open"), ("box", ""))[0]
                labels[nd.group("id")] = (nd.group("label").strip().strip('"'), shape)
                note(nd.group("id"))
                return nd.group("id")
            if re.fullmatch(r'[A-Za-z0-9_.\-]+', expr):
                note(expr)
                return expr
            return None

        ids = [resolve(s) for s in segments]
        for k, (arrow, mid, lbl) in enumerate(arrows_found):
            src, dst = ids[k], ids[k + 1]
            if src and dst:
                edges.append((src, dst, (lbl or mid or "").strip(), arrow))

    if not seen:
        return None

    out = ["digraph G {", GV_HEADER.format(rankdir=rankdir)]
    grouped = {n for _, members in clusters for n in members}
    for i, (title, members) in enumerate(clusters):
        out.append(f'subgraph cluster_{i} {{')
        out.append(f'  label="{esc(title)}"; style="rounded,dashed"; '
                   f'color="#9AA9BF"; fontsize=10; fontcolor="#54637A";')
        for n in members:
            lbl, shape = labels.get(n, (n, "box"))
            out.append(f'  "{n}" [label="{esc(lbl or n)}", shape={shape}];')
        out.append("}")
    for n in seen:
        if n in grouped:
            continue
        lbl, shape = labels.get(n, (n, "box"))
        out.append(f'"{n}" [label="{esc(lbl or n)}", shape={shape}];')
    for src, dst, lbl, arrow in edges:
        attrs = []
        if lbl:
            attrs.append(f'label="{esc(lbl)}"')
        if arrow.startswith("-.") or arrow.startswith("~"):
            attrs.append('style=dashed')
        if arrow.startswith("="):
            attrs.append('penwidth=2.0')
        if not arrow.endswith(">"):
            attrs.append('dir=none')
        out.append(f'"{src}" -> "{dst}" [{", ".join(attrs)}];')
    out.append("}")
    return "\n".join(out)


ER_CARD = {"||": "1", "|o": "0..1", "o|": "0..1", "}o": "0..*", "o{": "0..*",
           "}|": "1..*", "|{": "1..*", "{|": "1..*", "{o": "0..*"}
ER_REL_RE = re.compile(
    r'^(?P<a>[A-Za-z0-9_\-]+)\s+(?P<lc>\|\||\|o|\}o|\}\||o\||o\{)'
    r'(?P<line>--|\.\.)'
    r'(?P<rc>\|\||o\||o\{|\|\{|\{o|\|o)\s+(?P<b>[A-Za-z0-9_\-]+)\s*:\s*(?P<lbl>.+)$'
)


def er_to_dot(code: str) -> str | None:
    entities: dict[str, list[str]] = {}
    rels: list[tuple[str, str, str, str, str]] = []
    current = None
    for raw in code.splitlines():
        line = raw.strip()
        if not line or line.startswith("%%") or line.lower().startswith("erdiagram"):
            continue
        if line.endswith("{") and "--" not in line:
            current = line[:-1].strip()
            entities.setdefault(current, [])
            continue
        if line == "}":
            current = None
            continue
        if current is not None:
            parts = line.split()
            if len(parts) >= 2:
                entities[current].append(f"{parts[1]} : {parts[0]}")
            continue
        m = ER_REL_RE.match(line)
        if m:
            a, b = m.group("a"), m.group("b")
            entities.setdefault(a, []); entities.setdefault(b, [])
            rels.append((a, b, ER_CARD.get(m.group("lc"), ""),
                         ER_CARD.get(m.group("rc")[::-1], ER_CARD.get(m.group("rc"), "")),
                         m.group("lbl").strip('"')))
    if not entities:
        return None

    out = ["digraph ER {",
           'graph [rankdir=LR, splines=spline, nodesep=0.6, ranksep=0.9, bgcolor="white", pad=0.3];',
           'node [shape=plaintext, fontname="Helvetica", fontsize=10];',
           'edge [fontname="Helvetica", fontsize=9, color="#54637A"];']
    for name, attrs in entities.items():
        rows = "".join(
            f'<TR><TD ALIGN="LEFT" PORT="{i}">{a}</TD></TR>'
            for i, a in enumerate(attrs)
        )
        out.append(
            f'"{name}" [label=<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="4">'
            f'<TR><TD BGCOLOR="#4A6FA5"><FONT COLOR="white"><B>{name}</B></FONT></TD></TR>'
            f'{rows}</TABLE>>];'
        )
    for a, b, ca, cb, lbl in rels:
        out.append(f'"{a}" -> "{b}" [dir=none, label="{esc(lbl)}", '
                   f'taillabel="{ca}", headlabel="{cb}", labeldistance=1.8, fontsize=8];')
    out.append("}")
    return "\n".join(out)


def class_to_dot(code: str) -> str | None:
    members: dict[str, list[str]] = {}
    rels: list[tuple[str, str, str, str]] = []
    current = None
    for raw in code.splitlines():
        line = raw.strip()
        if not line or line.startswith("%%") or line.lower().startswith("classdiagram"):
            continue
        if line == "}":
            current = None
            continue
        m = re.match(r'^class\s+([A-Za-z0-9_]+)\s*\{?$', line)
        if m:
            current = m.group(1)
            members.setdefault(current, [])
            continue
        m = re.match(r'^([A-Za-z0-9_]+)\s*:\s*(.+)$', line)
        if m and current is None and m.group(1) in members:
            members[m.group(1)].append(m.group(2))
            continue
        m = re.match(r'^([A-Za-z0-9_]+)\s*(<\|--|\*--|o--|-->|--\>|\.\.\>|<\|\.\.|--)\s*'
                     r'([A-Za-z0-9_]+)\s*(?::\s*(.*))?$', line)
        if m:
            a, arrow, b, lbl = m.group(1), m.group(2), m.group(3), (m.group(4) or "")
            members.setdefault(a, []); members.setdefault(b, [])
            rels.append((a, b, arrow, lbl.strip()))
            continue
        if current is not None:
            members[current].append(line)
    if not members:
        return None

    out = ["digraph C {",
           'graph [rankdir=BT, splines=ortho, nodesep=0.5, ranksep=0.7, bgcolor="white", pad=0.3];',
           'node [shape=plaintext, fontname="Helvetica", fontsize=10];',
           'edge [fontname="Helvetica", fontsize=9, color="#54637A"];']
    for name, mem in members.items():
        rows = "".join(f'<TR><TD ALIGN="LEFT">{m}</TD></TR>' for m in mem)
        out.append(
            f'"{name}" [label=<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="4">'
            f'<TR><TD BGCOLOR="#EEF3FB"><B>{name}</B></TD></TR>{rows}</TABLE>>];'
        )
    for a, b, arrow, lbl in rels:
        attrs = ['label="%s"' % esc(lbl)] if lbl else []
        if arrow == "<|--":
            attrs += ['arrowhead=empty', 'dir=back']
        elif arrow == "*--":
            attrs += ['arrowhead=diamond']
        elif arrow == "o--":
            attrs += ['arrowhead=odiamond']
        elif arrow in ("..>", "<|.."):
            attrs += ['style=dashed']
        elif arrow == "--":
            attrs += ['dir=none']
        out.append(f'"{a}" -> "{b}" [{", ".join(attrs)}];')
    out.append("}")
    return "\n".join(out)


def state_to_dot(code: str) -> str | None:
    edges, nodes = [], []
    for raw in code.splitlines():
        line = raw.strip()
        if not line or line.startswith("%%") or line.lower().startswith("statediagram"):
            continue
        if line.startswith("direction"):
            continue
        m = re.match(r'^(\[\*\]|[A-Za-z0-9_]+)\s*-->\s*(\[\*\]|[A-Za-z0-9_]+)\s*(?::\s*(.*))?$', line)
        if m:
            a, b, lbl = m.group(1), m.group(2), (m.group(3) or "").strip()
            a = "__start" if a == "[*]" else a
            b = "__end" if b == "[*]" else b
            for n in (a, b):
                if n not in nodes:
                    nodes.append(n)
            edges.append((a, b, lbl))
    if not edges:
        return None
    out = ["digraph S {",
           'graph [rankdir=TB, splines=spline, nodesep=0.45, ranksep=0.6, bgcolor="white", pad=0.3];',
           'node [fontname="Helvetica", fontsize=11, shape=box, style="filled,rounded", '
           'fillcolor="#EEF3FB", color="#4A6FA5"];',
           'edge [fontname="Helvetica", fontsize=9, color="#54637A"];']
    for n in nodes:
        if n == "__start":
            out.append('"__start" [shape=circle, label="", width=0.22, fillcolor="#4A6FA5", color="#4A6FA5"];')
        elif n == "__end":
            out.append('"__end" [shape=doublecircle, label="", width=0.22, fillcolor="#4A6FA5", color="#4A6FA5"];')
        else:
            out.append(f'"{n}" [label="{esc(n)}"];')
    for a, b, lbl in edges:
        out.append(f'"{a}" -> "{b}"' + (f' [label="{esc(lbl)}"];' if lbl else ";"))
    out.append("}")
    return "\n".join(out)


def _png_aspect(p: Path) -> float:
    """Width/height from the PNG header, without pulling in an image library."""
    try:
        b = p.read_bytes()[:33]
        if b[:8] != b"\x89PNG\r\n\x1a\n":
            return 1.0
        w = int.from_bytes(b[16:20], "big")
        h = int.from_bytes(b[20:24], "big")
        return w / h if h else 1.0
    except OSError:
        return 1.0


def dot_to_png(dot_src: str, out_png: Path) -> bool:
    if not shutil.which("dot"):
        return False
    def once(src: str, target: Path) -> bool:
        try:
            r = run(["dot", "-Tpng", "-Gdpi=170", "-o", str(target)], input=src, timeout=90)
            return r.returncode == 0 and target.exists() and target.stat().st_size > 0
        except (subprocess.TimeoutExpired, OSError):
            return False

    if not once(dot_src, out_png):
        return False

    # A page is about 4:3. Anything past 3:1 either way is illegible once scaled
    # into a document, so try the perpendicular layout and keep the better one.
    aspect = _png_aspect(out_png)
    if 0.33 <= aspect <= 3.0 or "rankdir=" not in dot_src:
        return True
    flip = {"LR": "TB", "RL": "TB", "TB": "LR", "BT": "LR"}
    m = re.search(r"rankdir=(\w+)", dot_src)
    if not m or m.group(1) not in flip:
        return True
    alt_src = dot_src.replace(f"rankdir={m.group(1)}", f"rankdir={flip[m.group(1)]}", 1)
    alt = out_png.with_suffix(".alt.png")
    if once(alt_src, alt):
        if abs(_png_aspect(alt) - 1.33) < abs(aspect - 1.33):
            alt.replace(out_png)
        else:
            rm(alt)
    return True


# --------------------------------------------------------------------------
# offline renderer: sequence diagrams via matplotlib
# --------------------------------------------------------------------------
def render_sequence(code: str, out_png: Path) -> bool:
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib.patches import FancyBboxPatch, Rectangle
    except ImportError:
        return False

    actors: list[str] = []
    alias: dict[str, str] = {}
    events: list[dict] = []
    depth = 0

    def actor(name: str) -> str:
        name = name.strip()
        if name and name not in actors:
            actors.append(name)
        return name

    for raw in code.splitlines():
        line = raw.strip()
        if not line or line.startswith("%%") or line.lower().startswith("sequencediagram"):
            continue
        m = re.match(r'^(?:participant|actor)\s+([^\s]+)(?:\s+as\s+(.+))?$', line, re.I)
        if m:
            key = m.group(1).strip()
            alias[key] = (m.group(2) or key).strip()
            actor(key)
            continue
        m = re.match(r'^(alt|opt|loop|par|critical|rect)\b\s*(.*)$', line, re.I)
        if m:
            events.append({"t": "block", "kind": m.group(1).lower(),
                           "label": m.group(2).strip(), "depth": depth})
            depth += 1
            continue
        if re.match(r'^(else|and)\b', line, re.I):
            events.append({"t": "block", "kind": "else",
                           "label": line.split(None, 1)[1].strip() if " " in line else "",
                           "depth": max(depth - 1, 0)})
            continue
        if line.lower() == "end":
            depth = max(depth - 1, 0)
            events.append({"t": "end", "depth": depth})
            continue
        m = re.match(r'^(?:note|Note)\s+(over|left of|right of)\s+([^:]+):\s*(.*)$', line)
        if m:
            targets = [actor(a) for a in m.group(2).split(",")]
            events.append({"t": "note", "actors": targets, "label": m.group(3).strip(),
                           "depth": depth})
            continue
        if re.match(r'^(activate|deactivate|autonumber|box|end box)\b', line, re.I):
            continue
        m = re.match(r'^([^\s>-]+)\s*(-?->>?|-->>|->>|-->|-\)|--\))\s*([^:]+):\s*(.*)$', line)
        if m:
            a, arrow, b, msg = actor(m.group(1)), m.group(2), actor(m.group(3)), m.group(4)
            events.append({"t": "msg", "a": a, "b": b, "arrow": arrow,
                           "label": msg.strip(), "depth": depth})
    if not actors or not any(e["t"] == "msg" for e in events):
        return False

    n = len(actors)
    rows = [e for e in events if e["t"] in ("msg", "note", "block")]
    col_w = max(2.2, min(3.4, 22.0 / max(n, 1)))
    width = max(7.0, n * col_w)
    height = max(3.4, 1.7 + len(rows) * 0.62)
    fig, ax = plt.subplots(figsize=(width, height), dpi=170)
    ax.set_xlim(0, width); ax.set_ylim(0, height); ax.axis("off")

    xs = [(i + 0.5) * (width / n) for i in range(n)]
    head_y = height - 0.45
    for i, a in enumerate(actors):
        label = alias.get(a, a)
        wrapped = "\n".join(textwrap.wrap(label, 18)) or label
        ax.add_patch(FancyBboxPatch(
            (xs[i] - col_w * 0.36, head_y - 0.24), col_w * 0.72, 0.48,
            boxstyle="round,pad=0.02,rounding_size=0.06",
            linewidth=1.2, edgecolor="#4A6FA5", facecolor="#EEF3FB"))
        ax.text(xs[i], head_y, wrapped, ha="center", va="center", fontsize=9,
                color="#1F2D3D", zorder=5)
        ax.plot([xs[i], xs[i]], [0.35, head_y - 0.26], color="#B8C4D4",
                linestyle=(0, (4, 3)), linewidth=1.0, zorder=1)

    y = head_y - 0.85
    open_blocks: list[tuple[float, str, str]] = []
    for e in events:
        if e["t"] == "msg":
            i, j = actors.index(e["a"]), actors.index(e["b"])
            x1, x2 = xs[i], xs[j]
            dashed = e["arrow"].startswith("--")
            style = "->" if ">" in e["arrow"] else "-"
            if i == j:
                ax.annotate("", xy=(x1 + 0.42, y - 0.16), xytext=(x1, y),
                            arrowprops=dict(arrowstyle="-", color="#54637A", linewidth=1.1,
                                            connectionstyle="arc3,rad=-1.4",
                                            linestyle="--" if dashed else "-"))
                ax.text(x1 + 0.5, y - 0.05, e["label"], fontsize=8, va="center",
                        ha="left", color="#1F2D3D")
            else:
                ax.annotate("", xy=(x2, y), xytext=(x1, y),
                            arrowprops=dict(arrowstyle=style, color="#54637A", linewidth=1.2,
                                            linestyle="--" if dashed else "-",
                                            shrinkA=0, shrinkB=0))
                lbl = "\n".join(textwrap.wrap(e["label"], max(18, int(abs(x2 - x1) * 7))))
                ax.text((x1 + x2) / 2, y + 0.08, lbl, fontsize=8, ha="center",
                        va="bottom", color="#1F2D3D",
                        bbox=dict(boxstyle="round,pad=0.18", facecolor="white",
                                  edgecolor="none", alpha=0.95))
            y -= 0.62
        elif e["t"] == "note":
            idxs = [actors.index(a) for a in e["actors"] if a in actors] or [0]
            x1, x2 = min(xs[i] for i in idxs), max(xs[i] for i in idxs)
            pad = 0.55
            ax.add_patch(Rectangle((x1 - pad, y - 0.2), (x2 - x1) + 2 * pad, 0.42,
                                   linewidth=1.0, edgecolor="#C9A227",
                                   facecolor="#FDF6DD", zorder=3))
            ax.text((x1 + x2) / 2, y, e["label"], fontsize=8, ha="center", va="center",
                    color="#5C4813", zorder=4)
            y -= 0.62
        elif e["t"] == "block":
            open_blocks.append((y + 0.28, e["kind"], e["label"]))
            ax.text(0.12 + e["depth"] * 0.12, y + 0.3,
                    f'{e["kind"]}{" " + e["label"] if e["label"] else ""}',
                    fontsize=8, style="italic", color="#7A5C9E", va="bottom")
            y -= 0.34
        elif e["t"] == "end" and open_blocks:
            top, _, _ = open_blocks.pop()
            ax.add_patch(Rectangle((0.08, y + 0.1), width - 0.16, top - y - 0.1,
                                   linewidth=1.0, edgecolor="#C9B6DD",
                                   facecolor="none", zorder=0))
            y -= 0.18

    fig.tight_layout(pad=0.4)
    fig.savefig(out_png, dpi=170, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    return out_png.exists() and out_png.stat().st_size > 0


# --------------------------------------------------------------------------
# dispatch
# --------------------------------------------------------------------------
OFFLINE = {
    "flowchart": flowchart_to_dot, "graph": flowchart_to_dot,
    "c4context": flowchart_to_dot, "c4container": flowchart_to_dot,
    "c4component": flowchart_to_dot,
    "erdiagram": er_to_dot, "classdiagram": class_to_dot,
    "statediagram": state_to_dot, "statediagram-v2": state_to_dot,
}


def render_block(code: str, kind: str, out_png: Path, prefer_offline: bool = False) -> str:
    """Return the renderer that succeeded: mmdc | offline | none."""
    if not prefer_offline and render_with_mmdc(code, out_png):
        return "mmdc"
    if kind == "sequencediagram":
        if render_sequence(code, out_png):
            return "offline"
    fn = OFFLINE.get(kind)
    if fn:
        try:
            dot_src = fn(code)
        except Exception:
            dot_src = None
        if dot_src and dot_to_png(dot_src, out_png):
            return "offline"
    return "none"


def process(md_path: Path, out_md: Path | None, img_dir: Path | None,
            check: bool = False, prefer_offline: bool = False) -> int:
    text = md_path.read_text(encoding="utf-8")
    # Authors reliably put the caption on the line *above* the fence rather than
    # inside the block. Both readings of "start each block with a caption" are
    # reasonable, so accept either and fold it in — left outside, it renders as a
    # stray "%% caption: ..." paragraph in the Word file.
    text = re.sub(r"^[ \t]*(%%[ \t]*caption:[^\n]*)\n([ \t]*```mermaid[^\n]*\n)",
                  r"\2\1\n", text, flags=re.MULTILINE)
    blocks = list(FENCE_RE.finditer(text))
    if not blocks:
        if out_md:
            out_md.parent.mkdir(parents=True, exist_ok=True)
            out_md.write_text(text, encoding="utf-8")
        print(f"{md_path.name}: no mermaid blocks")
        return 0

    if img_dir:
        img_dir.mkdir(parents=True, exist_ok=True)
    stem = re.sub(r"[^A-Za-z0-9_-]", "-", md_path.stem)
    pieces, last, failures = [], 0, 0

    for i, m in enumerate(blocks, start=1):
        code = m.group("body")
        kind = diagram_kind(code)
        cap_m = CAPTION_RE.search(code)
        caption = cap_m.group(1) if cap_m else f"Figure {i}"
        png = (img_dir or Path(".")) / f"{stem}-fig{i:02d}.png"

        if check:
            probe = png.parent / f".probe-{stem}-{i}.png"
            probe.parent.mkdir(parents=True, exist_ok=True)
            used = render_block(code, kind, probe, prefer_offline)
            rm(probe)
            rm(probe.with_suffix(".mmd"))
            status = {"mmdc": "OK (mermaid-cli)", "offline": "OK (offline renderer)",
                      "none": "NOT RENDERABLE -> will ship as code block"}[used]
            print(f"  fig{i:02d} [{kind}] {status}")
            failures += used == "none"
            continue

        used = render_block(code, kind, png, prefer_offline)
        rm(png.with_suffix(".mmd"))
        pieces.append(text[last:m.start()])
        if used == "none":
            failures += 1
            print(f"  ! fig{i:02d} [{kind}] could not be rendered; kept as source block",
                  file=sys.stderr)
            pieces.append(f"```\n{code.rstrip()}\n```\n\n*{caption} (diagram source — "
                          f"install mermaid-cli to render this one as an image)*\n")
        else:
            try:
                rel = png.resolve().relative_to(out_md.resolve().parent) if out_md else png
            except ValueError:
                rel = png.resolve()
            # A lone image in its own paragraph becomes a pandoc implicit figure,
            # and the alt text becomes the styled caption — so don't add a
            # second caption line here or every figure gets labelled twice.
            pieces.append(f"![{caption}]({rel})\n")
        last = m.end()

    if check:
        return failures

    pieces.append(text[last:])
    out_md = out_md or md_path.with_suffix(".pandoc.md")
    out_md.parent.mkdir(parents=True, exist_ok=True)
    out_md.write_text("".join(pieces), encoding="utf-8")
    print(f"{md_path.name}: {len(blocks) - failures}/{len(blocks)} diagrams rendered -> {out_md}")
    return failures




def ensure_toc_updates(path: Path) -> None:
    """Make Word populate the table of contents on open.

    Pandoc writes a TOC field but no cached entries and does not carry the
    reference doc's settings, so every document opens with an empty
    "Table of Contents" until someone knows to press F9. Setting updateFields
    on the *output* is what actually fixes it.
    """
    import zipfile, tempfile, os, re as _re
    try:
        zin = zipfile.ZipFile(path)
        names = zin.namelist()
        if "word/settings.xml" not in names:
            return
        xml = zin.read("word/settings.xml").decode("utf-8")
        if "updateFields" in xml:
            return
        xml = _re.sub(r"(<w:settings[^>]*>)", r'\1<w:updateFields w:val="true"/>', xml, count=1)
        fd, tmp = tempfile.mkstemp(suffix=".docx", dir=str(path.parent))
        os.close(fd)
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                zout.writestr(item, xml.encode("utf-8") if item.filename == "word/settings.xml"
                              else zin.read(item.filename))
        zin.close()
        os.replace(tmp, path)
    except (OSError, KeyError, zipfile.BadZipFile):
        pass


def normalize_tables(path: Path, content_width_twips: int = 9360) -> None:
    """Give every table an explicit column grid.

    Pandoc emits zero-width grid columns for plain pipe tables, which Word
    tolerates but LibreOffice and Google Docs render as a collapsed mess. Fixing
    it here keeps the authored Markdown clean of width hints.
    """
    try:
        from docx import Document
        from docx.oxml import OxmlElement
        from docx.oxml.ns import qn
        from docx.shared import Twips
    except ImportError:
        return

    doc = Document(str(path))
    changed = False
    for table in doc.tables:
        tbl = table._tbl
        ncols = max((len(r.cells) for r in table.rows), default=0)
        if not ncols:
            continue
        grid = tbl.find(qn("w:tblGrid"))
        cols = grid.findall(qn("w:gridCol")) if grid is not None else []
        if cols and all(int(c.get(qn("w:w")) or 0) > 0 for c in cols):
            continue
        if grid is None:
            grid = OxmlElement("w:tblGrid")
            tbl.insert(1, grid)
        for c in cols:
            grid.remove(c)
        width = content_width_twips // ncols
        for _ in range(ncols):
            gc = OxmlElement("w:gridCol")
            gc.set(qn("w:w"), str(width))
            grid.append(gc)
        for row in table.rows:
            for cell in row.cells:
                cell.width = Twips(width)
        changed = True
    if changed:
        doc.save(str(path))


def build_one(md: Path, out_dir: Path, reference: Path | None, toc: bool,
              offline: bool, strict: bool) -> tuple[Path | None, int]:
    build = out_dir / ".build"
    img_dir = build / "img"
    pandoc_md = build / f"{md.stem}.pandoc.md"
    failures = process(md, pandoc_md, img_dir, check=False, prefer_offline=offline)
    if failures and strict:
        print(f"  {md.name}: {failures} diagram(s) unrendered and --strict-diagrams is set",
              file=sys.stderr)
        return None, failures

    out_docx = out_dir / f"{md.stem}.docx"
    cmd = ["pandoc", str(pandoc_md), "-o", str(out_docx),
           "--from", "markdown+pipe_tables+backtick_code_blocks+yaml_metadata_block",
           "--resource-path", f"{pandoc_md.parent}:{md.parent}",
           "--highlight-style", "tango"]
    if toc:
        cmd += ["--toc", "--toc-depth=3"]
    if reference and reference.exists():
        cmd += ["--reference-doc", str(reference)]
    r = run(cmd)
    if r.returncode != 0:
        print(f"  pandoc failed on {md.name}:\n{r.stderr}", file=sys.stderr)
        return None, failures
    normalize_tables(out_docx)
    ensure_toc_updates(out_docx)
    return out_docx, failures


def verify(docx: Path, out_dir: Path) -> list[Path]:
    """Rasterise page 1-2 so the document can be eyeballed, not just trusted."""
    soffice = shutil.which("soffice") or shutil.which("libreoffice")
    if not soffice:
        return []
    proof = out_dir / "_proof"
    proof.mkdir(parents=True, exist_ok=True)
    r = run([soffice, "--headless", "--convert-to", "pdf", "--outdir", str(proof), str(docx)],
            timeout=240)
    pdf = proof / f"{docx.stem}.pdf"
    if r.returncode != 0 or not pdf.exists():
        return []
    if shutil.which("pdftoppm"):
        run(["pdftoppm", "-jpeg", "-r", "90", "-l", "2", str(pdf), str(proof / docx.stem)])
    return sorted(proof.glob(f"{docx.stem}*.jpg"))




HERE = Path(__file__).resolve().parent
DEFAULT_REFERENCE = HERE.parent / "assets" / "reference.docx"


def cmd_build(argv) -> int:
    ap = argparse.ArgumentParser(prog="docset.py build",
                                 description="Markdown + Mermaid -> styled Word documents")
    ap.add_argument("markdown", type=Path, nargs="+")
    ap.add_argument("--out-dir", type=Path, default=Path("dist"))
    ap.add_argument("--reference", type=Path, default=DEFAULT_REFERENCE)
    ap.add_argument("--no-toc", action="store_true")
    ap.add_argument("--offline", action="store_true", help="skip mermaid-cli even if present")
    ap.add_argument("--strict-diagrams", action="store_true")
    ap.add_argument("--verify", action="store_true",
                    help="also emit page images so the output can be inspected")
    ap.add_argument("--evidence-dir", type=Path,
                    help="where evidence.py wrote its output; the gate needs it")
    ap.add_argument("--no-gate", action="store_true",
                    help="skip the quality gate (you are choosing to ship unchecked)")
    a = ap.parse_args(argv)

    if not shutil.which("pandoc"):
        raise SystemExit("pandoc not found on PATH")
    a.out_dir.mkdir(parents=True, exist_ok=True)

    built, bad = [], 0
    for md in a.markdown:
        if not md.exists():
            print(f"  missing: {md}", file=sys.stderr)
            bad += 1
            continue
        docx, failures = build_one(md, a.out_dir, a.reference, not a.no_toc,
                                   a.offline, a.strict_diagrams)
        if docx is None:
            bad += 1
            continue
        built.append(docx)
        print(f"built {docx}" + (f"  ({failures} diagram(s) kept as source)" if failures else ""))
        if a.verify:
            pages = verify(docx, a.out_dir)
            print(f"  proof pages: {', '.join(p.name for p in pages) if pages else 'skipped'}")
    print(f"\n{len(built)} document(s) built into {a.out_dir}")
    if not a.no_gate:
        pairs = {md: a.out_dir / f"{md.stem}.docx" for md in a.markdown if md.exists()}
        problems = verify_docs([m for m in a.markdown if m.exists()], a.evidence_dir, pairs)
        if problems:
            print(f"\nquality gate FAILED — {len(problems)} problem(s):", file=sys.stderr)
            for p in problems:
                print(f"  - {p}", file=sys.stderr)
            print("\nThese are what a reviewer notices first. Fix them, then rebuild.",
                  file=sys.stderr)
            return 1
        print("quality gate passed")
    return 1 if bad else 0


def cmd_render(argv) -> int:
    ap = argparse.ArgumentParser(prog="docset.py render",
                                 description="Mermaid blocks -> images + a pandoc-ready copy")
    ap.add_argument("markdown", type=Path, nargs="+")
    ap.add_argument("--out-md", type=Path)
    ap.add_argument("--img-dir", type=Path, default=Path("build/img"))
    ap.add_argument("--check", action="store_true", help="report renderability, write nothing")
    ap.add_argument("--offline", action="store_true")
    a = ap.parse_args(argv)
    if a.out_md and len(a.markdown) > 1:
        ap.error("--out-md works with a single input file")
    total = 0
    for md in a.markdown:
        out = a.out_md or (a.img_dir.parent / f"{md.stem}.pandoc.md")
        total += process(md, None if a.check else out, a.img_dir, a.check, a.offline)
    return 1 if (a.check and total) else 0




# ---------------------------------------------------------------------------
# Quality gate
# ---------------------------------------------------------------------------
GROUNDING_MIN = {"hld": 12, "lld": 10, "overview": 5, "user-guide": 3, "default": 5}


def _identifiers(evidence_json: Path) -> set[str]:
    """Concrete things the code actually contains: class names, route paths,
    table names, file paths. A document that mentions none of them was written
    about an imagined system."""
    try:
        d = json.loads(evidence_json.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return set()
    out: set[str] = set()

    def take(v):
        if isinstance(v, str) and len(v) > 3:
            out.add(v)
    for key in ("symbols", "roots", "entry_points", "tables", "http_calls"):
        for n in d.get(key, []) or []:
            if isinstance(n, dict):
                take(n.get("qualifiedName")); take(n.get("name")); take(n.get("routePath"))
                f = n.get("file")
                if isinstance(f, str):
                    out.add(Path(f).stem)
    for key in ("entrypoint", "data", "integration", "config"):   # scan-mode shape
        for line in d.get(key, []) or []:
            if isinstance(line, str):
                head = line.split("  ", 1)
                if len(head) == 2:
                    take(head[1].strip())
                out.add(Path(head[0].split(":")[0]).stem)
    return {o for o in out if len(o) > 3}


def _doc_kind(name: str) -> str:
    n = name.lower()
    for k in ("hld", "lld", "overview", "user-guide"):
        if k in n:
            return k
    return "default"


def verify_docs(md_files: list[Path], evidence_dir: Path | None,
                built: dict[Path, Path]) -> list[str]:
    """Return a list of failures. Empty means the set is fit to hand over."""
    problems: list[str] = []

    ev_md = (evidence_dir / "evidence.md") if evidence_dir else None
    if not ev_md or not ev_md.exists():
        problems.append(
            "no evidence file — run evidence.py before drafting. Documents written "
            "without it describe a system nobody built.")
        idents: set[str] = set()
    else:
        idents = _identifiers(evidence_dir / "evidence.json")

    for md in md_files:
        text = md.read_text(encoding="utf-8", errors="ignore")
        name = md.name

        # 1. grounded in real code?
        if idents:
            hits = {i for i in idents if i in text}
            need = GROUNDING_MIN.get(_doc_kind(name), GROUNDING_MIN["default"])
            if len(hits) < need:
                problems.append(
                    f"{name}: cites only {len(hits)} real identifiers from the evidence "
                    f"(needs {need}). It is describing the system in the abstract.")

        # 2. every diagram must survive into the Word file
        want = len(re.findall(r"^```mermaid", text, re.M))
        docx = built.get(md)
        if want and docx and docx.exists():
            try:
                got = len([n for n in zipfile.ZipFile(docx).namelist()
                           if n.startswith("word/media/")])
            except (OSError, zipfile.BadZipFile):
                got = 0
            if got < want:
                problems.append(
                    f"{name}: {want} diagram(s) authored, {got} image(s) in the .docx — "
                    f"{want - got} silently degraded to a code block.")

        # 3. prose, not a bullet dump
        bullets = len(re.findall(r"^\s*[-*+] ", text, re.M))
        prose = len([ln for ln in text.splitlines()
                     if ln.strip() and not re.match(r"^\s*([-*+#>|`]|\d+\.)", ln)])
        if bullets > 25 and prose and bullets > prose * 2:
            problems.append(
                f"{name}: {bullets} bullets against {prose} prose lines — reads as a dump. "
                f"Reasoning belongs in prose; only enumerable facts belong in lists.")

        # 3b. mermaid directives must never survive as visible text
        if re.search(r"^\s*%%\s*caption:", text, re.M):
            fenced = re.findall(r"```mermaid.*?```", text, re.S)
            stray = re.findall(r"^\s*%%\s*caption:[^\n]*", text, re.M)
            inside = sum(c.count("%% caption:") for c in fenced)
            if len(stray) > inside:
                problems.append(
                    f"{name}: {len(stray) - inside} caption line(s) sit outside the mermaid "
                    f"fence and will print as literal '%% caption:' text.")

        # 3c. diagram quality, not just diagram existence
        problems.extend(lint_mermaid(text, name, _doc_kind(name)))

        # 4. heading hierarchy actually starts at the top
        if not re.search(r"^# ", text, re.M) and not re.search(r"^title:", text, re.M):
            problems.append(f"{name}: no H1 or title — the document starts mid-hierarchy.")

        # 5. an HLD must name the platform it sits on
        if _doc_kind(name) == "hld" and ev_md and ev_md.exists():
            base = re.search(r"## Platform baseline(.*?)(?:\n# |\Z)",
                             ev_md.read_text(encoding="utf-8", errors="ignore"), re.S)
            if base:
                facts = set(re.findall(r"`([^`]+)`|\*\*([\w /]+):\*\*", base.group(1)))
                names = {a or b for a, b in facts}
                tokens = {t for n in names for t in re.split(r"[/\\ ,]", str(n))
                          if len(t) > 3 and not t.endswith(".kts")}
                if tokens and not any(t.lower() in text.lower() for t in tokens):
                    problems.append(
                        f"{name}: names none of the platform baseline facts (build system, "
                        f"frameworks, database, migrations). The design is described with no "
                        f"ground under it.")

    # 4b. does the set cover what the code actually contains?
    problems.extend(coverage_gap(
        "\n".join(m.read_text(encoding="utf-8", errors="ignore") for m in md_files),
        evidence_dir))

    # 5. the Word theme applied at all
    for md, docx in built.items():
        if not docx or not docx.exists():
            continue
        try:
            sx = zipfile.ZipFile(docx).read("word/styles.xml").decode("utf-8", "ignore")
        except (OSError, KeyError, zipfile.BadZipFile):
            continue
        m = re.search(r'w:styleId="Heading1".{0,500}?</w:style>', sx, re.S)
        if m and "w:color" not in m.group(0):
            problems.append(
                f"{docx.name}: Heading 1 is unstyled — the Word template did not apply. "
                f"Check --reference points at assets/reference.docx.")
        break
    return problems




GENERIC_STORES = ("relational db", "database", "db", "datastore", "data store",
                  "sql db", "storage", "cache", "queue", "external system", "backend")


def lint_mermaid(text: str, name: str, kind: str) -> list[str]:
    """Diagram defects a reader notices immediately.

    A diagram is not judged by whether it renders. It is judged by whether
    someone can trace a path through it. These are the failures that make a
    generated diagram look generated.
    """
    out: list[str] = []
    for i, block in enumerate(re.findall(r"```mermaid\n(.*?)```", text, re.S), start=1):
        head = next((l.strip() for l in block.splitlines()
                     if l.strip() and not l.strip().startswith("%%")), "")
        if not head.lower().startswith(("flowchart", "graph")):
            continue

        body = [l for l in block.splitlines()
                if l.strip() and not l.strip().startswith(("%%", "subgraph", "end",
                                                           "style", "classDef", "class ",
                                                           "linkStyle"))][1:]
        declared, connected = set(), set()
        for line in body:
            for m in re.finditer(r"\b([A-Za-z][\w]*)\s*[\[\(\{>]", line):
                declared.add(m.group(1))
            if re.search(r"-{2,}>|-\.->|={2,}>|-{2,}(?!>)", line):
                for m in re.finditer(r"\b([A-Za-z][\w]*)\b", line):
                    connected.add(m.group(1))
        orphans = {d for d in declared if d not in connected}
        if orphans:
            out.append(f"{name} figure {i}: {len(orphans)} node(s) with no edges "
                       f"({', '.join(sorted(orphans)[:4])}) — a box nothing connects to "
                       f"is either missing a relationship or does not belong.")

        if len(declared) > 14:
            out.append(f"{name} figure {i}: {len(declared)} nodes — past ~12 a reader stops "
                       f"tracing paths. Split it by concern.")

        labels = re.findall(r"[\[\(\{]\"?([^\]\)\}\"]{2,60})", block)
        for lab in labels:
            if lab.strip().lower() in GENERIC_STORES:
                out.append(f'{name} figure {i}: "{lab.strip()}" is a placeholder, not a '
                           f"component. Name the actual store, schema or subsystem.")
                break

        # container-level diagrams should carry technology, C4-style
        if kind == "hld" and i <= 3 and len(declared) >= 5:
            if not re.search(r"[\[\(]\"?[^\]\)]*[\[\(]?(?:v?\d+\.\d+|Javalin|Spring|React|"
                             r"Postgres|MySQL|Kafka|Redis|Hibernate)", block, re.I):
                out.append(f"{name} figure {i}: no technology or version on any box. A "
                           f"container view without them cannot be reviewed for fit.")
    return out


def coverage_gap(text_all: str, evidence_dir: Path | None) -> list[str]:
    """Is the document set actually covering what the code contains?"""
    if not evidence_dir or not (evidence_dir / "evidence.json").exists():
        return []
    try:
        d = json.loads((evidence_dir / "evidence.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []
    out = []
    routes = [h.get("routePath") for h in (d.get("http_calls") or []) if h.get("routePath")]
    if routes:
        hit = [r for r in routes if r in text_all]
        if len(hit) < len(routes) * 0.5:
            missing = [r for r in routes if r not in text_all][:5]
            out.append(f"the set documents {len(hit)} of {len(routes)} HTTP routes found in "
                       f"the code. Missing e.g. {', '.join(missing)}")
    comps = [m for m, _ in (d.get("modules") or [])][:15]
    if comps:
        hit = [c for c in comps if c in text_all]
        if len(hit) < len(comps) * 0.5:
            out.append(f"the set names {len(hit)} of {len(comps)} components the evidence "
                       f"found ({', '.join(c for c in comps if c not in text_all)[:80]}...)")
    return out


def cmd_verify(argv) -> int:
    ap = argparse.ArgumentParser(prog="docset.py verify",
                                 description="Fail a document set that is not fit to hand over")
    ap.add_argument("markdown", type=Path, nargs="+")
    ap.add_argument("--evidence-dir", type=Path)
    ap.add_argument("--dist", type=Path, default=Path("dist"))
    a = ap.parse_args(argv)
    built = {md: a.dist / f"{md.stem}.docx" for md in a.markdown}
    problems = verify_docs(list(a.markdown), a.evidence_dir, built)
    if not problems:
        print(f"gate passed: {len(a.markdown)} document(s) fit to hand over")
        return 0
    print(f"gate FAILED — {len(problems)} problem(s):\n", file=sys.stderr)
    for p in problems:
        print(f"  - {p}", file=sys.stderr)
    print("\nFix these before delivering. They are the defects reviewers notice first.",
          file=sys.stderr)
    return 1


COMMANDS = {"build": cmd_build, "render": cmd_render, "verify": cmd_verify}


def main() -> int:
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        print(__doc__)
        print("commands:\n  build   Markdown -> .docx (use --verify and look at the pages)"
              "\n  render  Mermaid -> images only (--check to pre-flight)")
        return 0 if len(sys.argv) < 2 else 1
    return COMMANDS[sys.argv[1]](sys.argv[2:])


if __name__ == "__main__":
    sys.exit(main())
