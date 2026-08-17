#!/usr/bin/env python3
"""Build an editable multi-page C4 .drawio file from a small JSON model.

Hand-writing drawio XML wastes time and produces diagrams nobody can lay out
again later. Describe the model once; get one page per C4 level, laid out
top-down by dependency depth, that opens and edits normally in diagrams.net.

Model format (see --example for a runnable sample):

{
  "system": "Order Platform",
  "pages": [
    {
      "name": "L1 — System Context",
      "nodes": [
        {"id": "shopper", "name": "Shopper", "type": "person",
         "desc": "Places and tracks orders"},
        {"id": "op", "name": "Order Platform", "type": "system",
         "desc": "Accepts and settles orders", "tech": "Java 21 / Spring Boot"},
        {"id": "psp", "name": "Payment Provider", "type": "external",
         "desc": "Card authorisation"}
      ],
      "edges": [
        {"from": "shopper", "to": "op", "label": "Places orders", "tech": "HTTPS"},
        {"from": "op", "to": "psp", "label": "Authorises payment", "tech": "REST"}
      ]
    }
  ]
}

Node types: person, system, external, container, database, queue, component.
Group nodes with "group": "<boundary name>" to draw a dashed boundary around them.

Usage:
    python make_c4_drawio.py model.json -o docs/diagrams/architecture.drawio
    python make_c4_drawio.py --example > model.json
"""

from __future__ import annotations

import argparse
import json
import sys
import textwrap
from collections import defaultdict
from pathlib import Path
from xml.sax.saxutils import escape

# type -> (fill, stroke, font colour, shape hint)
STYLE = {
    "person":    ("#0B5394", "#073763", "#FFFFFF", "person"),
    "system":    ("#1F3864", "#12213C", "#FFFFFF", "box"),
    "external":  ("#6B7280", "#4B5563", "#FFFFFF", "box"),
    "container": ("#2E6DA4", "#1F4E79", "#FFFFFF", "box"),
    "component": ("#4A86C8", "#2E6DA4", "#FFFFFF", "box"),
    "database":  ("#2E6DA4", "#1F4E79", "#FFFFFF", "cylinder"),
    "queue":     ("#2E6DA4", "#1F4E79", "#FFFFFF", "queue"),
}
W, H = 220, 110
GAP_X, GAP_Y = 70, 90
MARGIN = 60


def node_style(kind: str) -> str:
    fill, stroke, font, shape = STYLE.get(kind, STYLE["container"])
    base = (f"rounded=1;arcSize=8;whiteSpace=wrap;html=1;fillColor={fill};"
            f"strokeColor={stroke};fontColor={font};fontSize=12;align=center;"
            f"verticalAlign=middle;spacing=6;")
    if shape == "person":
        return (f"shape=mxgraph.c4.person2;html=1;whiteSpace=wrap;fillColor={fill};"
                f"strokeColor={stroke};fontColor={font};fontSize=12;align=center;"
                f"verticalAlign=top;spacingTop=4;")
    if shape == "cylinder":
        return (f"shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=12;html=1;"
                f"whiteSpace=wrap;fillColor={fill};strokeColor={stroke};"
                f"fontColor={font};fontSize=12;align=center;")
    if shape == "queue":
        return (f"shape=process;whiteSpace=wrap;html=1;fillColor={fill};"
                f"strokeColor={stroke};fontColor={font};fontSize=12;align=center;")
    if kind == "external":
        return base + "dashed=1;dashPattern=6 4;"
    return base


def attr(value: str) -> str:
    """drawio keeps HTML labels as escaped text inside the value attribute.

    Build the label as real HTML, then escape the whole thing exactly once on
    the way in — escaping the pieces first and the whole afterwards produces
    &amp;lt;b&amp;gt; and a label full of visible tags.
    """
    return escape(value, {'"': "&quot;"})


def label(node: dict) -> str:
    """C4 boxes read as: name / [type: tech] / description. Returns raw HTML."""
    parts = [f"<b>{node.get('name', node['id'])}</b>"]
    kind = node.get("type", "container")
    tech = node.get("tech", "")
    tag = {"person": "Person", "system": "Software System",
           "external": "External System", "container": "Container",
           "component": "Component", "database": "Database",
           "queue": "Queue"}.get(kind, kind.title())
    parts.append(f'<font style="font-size:10px">[{tag}'
                 + (f": {tech}" if tech else "") + "]</font>")
    desc = node.get("desc", "")
    if desc:
        parts.append('<font style="font-size:10px">'
                     + "<br/>".join(textwrap.wrap(desc, 34)[:3]) + "</font>")
    return "<br/>".join(parts)


def rank_nodes(nodes: list[dict], edges: list[dict]) -> dict[str, int]:
    """Longest-path layering: sources at the top, so flow reads downward."""
    ids = [n["id"] for n in nodes]
    incoming = defaultdict(list)
    outgoing = defaultdict(list)
    for e in edges:
        if e.get("from") in ids and e.get("to") in ids and e["from"] != e["to"]:
            outgoing[e["from"]].append(e["to"])
            incoming[e["to"]].append(e["from"])

    rank = {i: 0 for i in ids}
    for _ in range(len(ids)):
        changed = False
        for i in ids:
            for src in incoming[i]:
                if rank[src] + 1 > rank[i]:
                    rank[i] = rank[src] + 1
                    changed = True
        if not changed:
            break
    return rank


def layout(nodes: list[dict], edges: list[dict]) -> dict[str, tuple[int, int]]:
    rank = rank_nodes(nodes, edges)
    rows: dict[int, list[str]] = defaultdict(list)
    for n in nodes:
        rows[rank[n["id"]]].append(n["id"])
    widest = max((len(r) for r in rows.values()), default=1)
    total_w = widest * W + (widest - 1) * GAP_X
    pos = {}
    for r in sorted(rows):
        members = rows[r]
        row_w = len(members) * W + (len(members) - 1) * GAP_X
        x0 = MARGIN + (total_w - row_w) // 2
        for i, nid in enumerate(members):
            pos[nid] = (x0 + i * (W + GAP_X), MARGIN + r * (H + GAP_Y))
    return pos


def page_xml(page: dict, page_id: int) -> str:
    nodes = page.get("nodes", [])
    edges = page.get("edges", [])
    pos = layout(nodes, edges)
    cells = []

    # Boundaries first so they sit behind their members.
    groups: dict[str, list[str]] = defaultdict(list)
    for n in nodes:
        if n.get("group"):
            groups[n["group"]].append(n["id"])
    for gi, (name, members) in enumerate(groups.items()):
        pts = [pos[m] for m in members if m in pos]
        if not pts:
            continue
        x0 = min(p[0] for p in pts) - 24
        y0 = min(p[1] for p in pts) - 44
        x1 = max(p[0] for p in pts) + W + 24
        y1 = max(p[1] for p in pts) + H + 24
        cells.append(
            f'<mxCell id="g{page_id}_{gi}" value="{attr(name)}" '
            f'style="rounded=1;arcSize=4;html=1;dashed=1;dashPattern=8 4;'
            f'fillColor=none;strokeColor=#7A8699;fontColor=#5A6472;fontSize=11;'
            f'verticalAlign=top;align=left;spacingLeft=10;spacingTop=4;" '
            f'vertex="1" parent="1">'
            f'<mxGeometry x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" as="geometry"/>'
            f'</mxCell>'
        )

    for n in nodes:
        x, y = pos.get(n["id"], (MARGIN, MARGIN))
        cells.append(
            f'<mxCell id="{attr(str(n["id"]))}" value="{attr(label(n))}" '
            f'style="{node_style(n.get("type", "container"))}" vertex="1" parent="1">'
            f'<mxGeometry x="{x}" y="{y}" width="{W}" height="{H}" as="geometry"/>'
            f'</mxCell>'
        )

    for ei, e in enumerate(edges):
        text = e.get("label", "")
        if e.get("tech"):
            text += f'<br/><font style="font-size:9px">[{e["tech"]}]</font>'
        dashed = ";dashed=1;dashPattern=6 4" if e.get("style") == "async" else ""
        cells.append(
            f'<mxCell id="e{page_id}_{ei}" value="{attr(text)}" '
            f'style="edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;'
            f'strokeColor=#54637A;fontColor=#3A4658;fontSize=10;'
            f'labelBackgroundColor=#FFFFFF{dashed};" edge="1" parent="1" '
            f'source="{escape(str(e["from"]))}" target="{escape(str(e["to"]))}">'
            f'<mxGeometry relative="1" as="geometry"/></mxCell>'
        )

    return (
        f'<diagram id="page{page_id}" name="{escape(page.get("name", f"Page {page_id}"))}">'
        f'<mxGraphModel dx="1100" dy="800" grid="1" gridSize="10" guides="1" '
        f'tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" '
        f'pageWidth="1169" pageHeight="826" math="0" shadow="0">'
        f'<root><mxCell id="0"/><mxCell id="1" parent="0"/>'
        + "".join(cells) +
        '</root></mxGraphModel></diagram>'
    )


EXAMPLE = {
    "system": "Order Platform",
    "pages": [
        {
            "name": "L1 — System Context",
            "nodes": [
                {"id": "shopper", "name": "Shopper", "type": "person",
                 "desc": "Places and tracks orders"},
                {"id": "op", "name": "Order Platform", "type": "system",
                 "desc": "Accepts, validates and settles orders"},
                {"id": "psp", "name": "Payment Provider", "type": "external",
                 "desc": "Card authorisation and capture"},
                {"id": "wms", "name": "Warehouse System", "type": "external",
                 "desc": "Fulfilment and stock levels"},
            ],
            "edges": [
                {"from": "shopper", "to": "op", "label": "Places orders", "tech": "HTTPS"},
                {"from": "op", "to": "psp", "label": "Authorises payment", "tech": "REST"},
                {"from": "op", "to": "wms", "label": "Requests fulfilment",
                 "tech": "Kafka", "style": "async"},
            ],
        },
        {
            "name": "L2 — Container",
            "nodes": [
                {"id": "spa", "name": "Storefront", "type": "container",
                 "tech": "React 18", "desc": "Browser application", "group": "Order Platform"},
                {"id": "api", "name": "Order API", "type": "container",
                 "tech": "Spring Boot 3.2", "desc": "Order lifecycle and validation",
                 "group": "Order Platform"},
                {"id": "db", "name": "Order Store", "type": "database",
                 "tech": "PostgreSQL 16", "desc": "Orders, lines, payment refs",
                 "group": "Order Platform"},
                {"id": "topic", "name": "orders.v1", "type": "queue",
                 "tech": "Kafka", "desc": "Order lifecycle events",
                 "group": "Order Platform"},
            ],
            "edges": [
                {"from": "spa", "to": "api", "label": "Calls", "tech": "JSON/HTTPS"},
                {"from": "api", "to": "db", "label": "Reads / writes", "tech": "JDBC"},
                {"from": "api", "to": "topic", "label": "Publishes events",
                 "tech": "Kafka", "style": "async"},
            ],
        },
    ],
}


def build(model: dict) -> str:
    pages = model.get("pages") or []
    if not pages:
        raise SystemExit("model has no pages")
    return ('<mxfile host="project-doc-set" type="device">'
            + "".join(page_xml(p, i) for i, p in enumerate(pages))
            + "</mxfile>")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("model", type=Path, nargs="?")
    ap.add_argument("-o", "--out", type=Path)
    ap.add_argument("--example", action="store_true",
                    help="print a sample model to stdout and exit")
    a = ap.parse_args()

    if a.example:
        print(json.dumps(EXAMPLE, indent=2))
        return 0
    if not a.model:
        ap.error("model file required (or use --example)")

    model = json.loads(a.model.read_text(encoding="utf-8"))
    xml = build(model)
    if a.out:
        a.out.parent.mkdir(parents=True, exist_ok=True)
        a.out.write_text(xml, encoding="utf-8")
        pages = ", ".join(p.get("name", "?") for p in model["pages"])
        print(f"wrote {a.out} ({len(model['pages'])} page(s): {pages})")
    else:
        print(xml)
    return 0


if __name__ == "__main__":
    sys.exit(main())
