// ─────────────────────────────────────────────────────────────────────────────
// The orthogonal edge router — everything is DERIVED from the panel layout:
//
//   * level-2 dependents climb straight up into the corridor, then into
//     main_entry's bottom edge (the hub fan)
//   * level-3/4 dependents run through the gutter above their own level into an
//     outer trunk lane (the canvas margins), climb, then join the same corridor
//   * lookup tables drop into the spine panel's left padding lane and enter
//     main_entry's LEFT edge (where the FK columns sit)
//   * content_details enters the RIGHT edge (it is main_entry's neighbour)
//   * in-panel peers (content_genre→genre, library_item→category, the seed
//     edges) are straight horizontals between neighbouring cards
//
// Every hub spoke is mapped to a unique fan slot ordered by source x, so the
// bundle converges without crossings.
// ─────────────────────────────────────────────────────────────────────────────

import { EDGES, CARD_HEADER_H, COL_ROW_H, CARD_PAD_X, type EdgeDef } from "@/src/data/edges";
import {
  CORRIDOR_Y,
  PANEL_BY_ID,
  TABLE_BY_NAME,
  TRUNK_LEFT,
  TRUNK_RIGHT,
  columnsRows,
  estimateTableHeight,
  panelOf,
  type TableDef,
} from "@/src/data/tables";

export type Dir = "r" | "l" | "u" | "d";

export interface RoutedEdge {
  id: string;
  def: EdgeDef;
  fromTable: string;
  toTable: string;
  pts: [number, number][];
  hx: number;
  hy: number;
  dir: Dir;
}

const HUB = "main_entry";

function bottomOf(t: TableDef): number {
  return t.y + estimateTableHeight(t);
}

/** where a column sits inside its card — 2-column cards split at the midpoint */
function colAnchor(tableName: string, colName: string) {
  const t = TABLE_BY_NAME[tableName];
  const idx = Math.max(0, t.cols.findIndex((c) => c.name === colName));
  const cols = columnsRows(t.cols.length);
  const rows = Math.ceil(t.cols.length / cols);
  const row = cols === 1 ? idx : idx % rows;
  const sub = cols === 1 ? 0 : Math.floor(idx / rows);
  return {
    y: t.y + CARD_HEADER_H + row * COL_ROW_H + COL_ROW_H / 2,
    sub,
    /** x for a top/bottom exit near that column */
    exitX: t.x + (sub === 0 ? CARD_PAD_X : t.w / 2 + 18),
  };
}

// ── hub fan: 18 spokes, ordered by source x so nothing crosses ───────────────
const hubEdges = EDGES.filter((e) =>
  e.route === "hub2" || e.route === "hubDeep" || e.route === "hub1b",
);
const hub = TABLE_BY_NAME[HUB];
const hubBottomY = bottomOf(hub);
const fanOrder = [...hubEdges].sort((a, b) => {
  const ax = colAnchor(a.from.split(".")[0], a.from.split(".")[1]).exitX;
  const bx = colAnchor(b.from.split(".")[0], b.from.split(".")[1]).exitX;
  return ax - bx;
});
const fanSlot = new Map<string, number>(fanOrder.map((e, i) => [e.from, i]));
const fanCount = hubEdges.length;

function fanX(slot: number): number {
  const a = hub.x + 26;
  const b = hub.x + hub.w - 26;
  return fanCount === 1 ? (a + b) / 2 : a + ((b - a) * slot) / (fanCount - 1);
}

/** the gutter row a deep panel uses before climbing its lane */
const DEEP_ROW = new Map<string, number>();
{
  const deepPanels = ["downloads", "updates", "notifications", "ratings", "playback", "standalone"] as const;
  deepPanels.forEach((id, i) => {
    const p = PANEL_BY_ID[id];
    // a row in the gutter directly above that panel
    DEEP_ROW.set(id, p.y - 56 - i * 3);
  });
}

// ── riser lanes for the deep spokes ─────────────────────────────────────────
// Level-3 spokes climb the INTERIOR gutters between the level-2 panels (they
// line up with the visible gaps); level-4 spokes use the outer margins, the
// only lanes that clear every band.
const L2_ORDERED = (["genre", "library", "watch", "tracking", "caches"] as const)
  .map((id) => PANEL_BY_ID[id])
  .sort((a, b) => a.x - b.x);

function laneFor(exitX: number, level: number): number {
  const cands: number[] = [TRUNK_LEFT, TRUNK_RIGHT];
  if (level === 3) {
    for (let i = 0; i < L2_ORDERED.length - 1; i++) {
      cands.push((L2_ORDERED[i].x + L2_ORDERED[i].w + L2_ORDERED[i + 1].x) / 2);
    }
  }
  return cands.reduce(
    (best, c) => (Math.abs(c - exitX) < Math.abs(best - exitX) ? c : best),
    cands[0],
  );
}

const LANE = new Map<string, number>();
{
  const members = new Map<number, string[]>();
  for (const e of EDGES) {
    if (e.route !== "hubDeep") continue;
    const [t, c] = e.from.split(".");
    const x = colAnchor(t, c).exitX;
    const cand = laneFor(x, panelOf(t).level);
    const key = Math.round(cand);
    members.set(key, [...(members.get(key) ?? []), e.from]);
    LANE.set(e.from, cand);
  }
  // bundle the spokes that share a lane into parallel strands
  for (const [key, list] of members) {
    list.forEach((from, i) => {
      LANE.set(from, (LANE.get(from) ?? key) + (i - (list.length - 1) / 2) * 9);
    });
  }
}

// ── routing ──────────────────────────────────────────────────────────────────
function route(e: EdgeDef, routed: RoutedEdge[]): RoutedEdge {
  const [fromT, fromC] = e.from.split(".");
  const [toT, toC] = e.to.split(".");
  const ft = TABLE_BY_NAME[fromT];
  const tt = TABLE_BY_NAME[toT];
  const from = colAnchor(fromT, fromC);
  const to = colAnchor(toT, toC);
  const nudge = e.nudge ?? 0;
  let pts: [number, number][];
  let dir: Dir = "r";

  const slot = fanSlot.get(e.from) ?? 0;
  const corridorY = CORRIDOR_Y + slot * 9;
  const fx = fanX(slot);

  switch (e.route) {
    case "hub2": {
      // card top → straight up → corridor → hub bottom
      pts = [
        [from.exitX, ft.y],
        [from.exitX, corridorY],
        [fx, corridorY],
        [fx, hubBottomY],
      ];
      dir = "u";
      break;
    }
    case "hub1b": {
      // a spine sibling ABOVE the corridor: descend out of its bottom instead
      pts = [
        [from.exitX, bottomOf(ft)],
        [from.exitX, corridorY],
        [fx, corridorY],
        [fx, hubBottomY],
      ];
      dir = "u";
      break;
    }
    case "hubDeep": {
      // card top → its level gutter → riser lane → corridor → hub bottom
      const panel = panelOf(fromT);
      const gy = DEEP_ROW.get(panel.id) ?? panel.y - 40;
      const laneX = LANE.get(e.from) ?? TRUNK_LEFT;
      pts = [
        [from.exitX, ft.y],
        [from.exitX, gy],
        [laneX, gy],
        [laneX, corridorY],
        [fx, corridorY],
        [fx, hubBottomY],
      ];
      dir = "u";
      break;
    }
    case "lookup": {
      // out of the lookup card's bottom → the spine panel's left padding lane →
      // into main_entry's LEFT edge at the FK row (each edge gets its own
      // lane x and row y so the pair never overlaps)
      const spine = PANEL_BY_ID.spine;
      const lane = spine.x + 5 + (nudge > 0 ? 7 : 0);
      const laneY = spine.y - 46 + nudge * 1.5;
      pts = [
        [from.exitX, bottomOf(ft)],
        [from.exitX, laneY],
        [lane, laneY],
        [lane, to.y],
        [tt.x, to.y],
      ];
      dir = "r";
      break;
    }
    case "hubAdjacent": {
      pts = [
        [ft.x, from.y],
        [tt.x + tt.w, to.y],
      ];
      dir = "l";
      break;
    }
    case "adjacentL": {
      pts = [
        [ft.x, from.y],
        [tt.x + tt.w, from.y],
      ];
      dir = "l";
      break;
    }
    case "seedAdjacent": {
      pts = [
        [ft.x + ft.w, from.y],
        [tt.x, to.y],
      ];
      dir = "r";
      break;
    }
    case "seedLong": {
      // climb from the source panel to content_identity's row, enter its right edge
      pts = [
        [from.exitX, ft.y],
        [from.exitX, to.y],
        [tt.x + tt.w, to.y],
      ];
      dir = "l";
      break;
    }
  }

  void routed;
  return {
    id: e.from,
    def: e,
    fromTable: fromT,
    toTable: toT,
    pts,
    hx: pts[pts.length - 1][0],
    hy: pts[pts.length - 1][1],
    dir,
  };
}

const acc: RoutedEdge[] = [];
export const ROUTED: RoutedEdge[] = EDGES.map((e) => route(e, acc));

export function arrowPath(dir: Dir, x: number, y: number): string {
  switch (dir) {
    case "r":
      return `M ${x} ${y} l -9 -4.5 l 0 9 Z`;
    case "l":
      return `M ${x} ${y} l 9 -4.5 l 0 9 Z`;
    case "u":
      return `M ${x} ${y} l -4.5 9 l 9 0 Z`;
    case "d":
      return `M ${x} ${y} l -4.5 -9 l 9 0 Z`;
  }
}

