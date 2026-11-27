"use client";

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import {
  GROUP_BY_ID,
  PANELS,
  TABLE_BY_NAME,
  WORLD_BOUNDS,
  famFor,
  panelOf,
} from "@/src/data/tables";
import { ROUTED, arrowPath } from "@/src/lib/geometry";
import { useTheme } from "./ThemeContext";
import { TableCard } from "./TableCard";

const MIN_S = 0.08;
const MAX_S = 2.5;
const LIT = "#e11d48";

/** cell → the edges through it + the cell at the other end (built once) */
const CELL_LINKS = (() => {
  const m = new Map<string, { edgeKey: string; peer: string }[]>();
  ROUTED.forEach((re) => {
    const f = re.def.from;
    const t = re.def.to;
    m.set(f, [...(m.get(f) ?? []), { edgeKey: f, peer: t }]);
    m.set(t, [...(m.get(t) ?? []), { edgeKey: f, peer: f }]);
  });
  return m;
})();

export function CanvasSurface({ tall = false }: { tall?: boolean }) {
  const { theme } = useTheme();
  const vpRef = useRef<HTMLDivElement>(null);
  const worldRef = useRef<HTMLDivElement>(null);
  const tf = useRef({ x: 0, y: 0, s: 1 });
  const idle = useRef(0);
  const drag = useRef<{
    id: number;
    lx: number;
    ly: number;
    moved: number;
    hit: string | null;
  } | null>(null);
  const hot = useRef<Element[]>([]);
  const labelRef = useRef<HTMLSpanElement>(null);
  const [focused, setFocused] = useState<string | null>(null);
  const [legendOpen, setLegendOpen] = useState(false);

  // ── transform plumbing ─────────────────────────────────────────────────────
  // Direct style writes; the zoom readout is imperative too, so nothing in the
  // hot path waits on React or rAF.
  const apply = useCallback(() => {
    const w = worldRef.current;
    if (!w) return;
    const { x, y, s } = tf.current;
    w.style.transform = `translate(${x}px, ${y}px) scale(${s})`;
    if (labelRef.current) labelRef.current.textContent = `${Math.round(s * 100)}%`;
  }, []);

  /** promote to a composited layer ONLY while a gesture is alive — smooth
   *  pan/zoom, then a demote 240ms after the last event re-rasterises the
   *  final scale so text stays crisp. */
  const bump = useCallback(() => {
    const w = worldRef.current;
    if (!w) return;
    w.classList.add("composited");
    window.clearTimeout(idle.current);
    idle.current = window.setTimeout(() => w.classList.remove("composited"), 240);
  }, []);

  const fit = useCallback(() => {
    const vp = vpRef.current;
    if (!vp) return;
    const r = vp.getBoundingClientRect();
    const s = Math.min((r.width / WORLD_BOUNDS.w) * 0.985, (r.height / WORLD_BOUNDS.h) * 0.985);
    tf.current = {
      s,
      x: (r.width - WORLD_BOUNDS.w * s) / 2,
      y: (r.height - WORLD_BOUNDS.h * s) / 2,
    };
    apply();
  }, [apply]);

  const zoomAt = useCallback(
    (clientX: number, clientY: number, factor: number) => {
      const vp = vpRef.current;
      if (!vp) return;
      const r = vp.getBoundingClientRect();
      const cx = clientX - r.left;
      const cy = clientY - r.top;
      const { x, y, s } = tf.current;
      const s2 = Math.min(MAX_S, Math.max(MIN_S, s * factor));
      const k = s2 / s;
      tf.current = { s: s2, x: cx - (cx - x) * k, y: cy - (cy - y) * k };
      bump();
      apply();
    },
    [apply, bump],
  );

  // fit once on mount
  useLayoutEffect(() => {
    fit();
  }, [fit]);

  // wheel = zoom around the cursor (deltaMode-normalized)
  useEffect(() => {
    const vp = vpRef.current;
    if (!vp) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      let dy = e.deltaY;
      if (e.deltaMode === 1) dy *= 33;
      else if (e.deltaMode === 2) dy *= 300;
      zoomAt(e.clientX, e.clientY, Math.exp(-dy * 0.0016));
    };
    vp.addEventListener("wheel", onWheel, { passive: false });
    return () => vp.removeEventListener("wheel", onWheel);
  }, [zoomAt]);

  // Escape clears the spotlight
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setFocused(null);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  // ── cell-level trace: hover a column → its wire + the peer cell light up ───
  const clearHot = useCallback(() => {
    hot.current.forEach((el) => el.classList.remove("cell-hot", "cell-peer"));
    hot.current = [];
    vpRef.current?.querySelectorAll(".edge-hot").forEach((el) => el.classList.remove("edge-hot"));
  }, []);

  useEffect(() => {
    const vp = vpRef.current;
    if (!vp) return;
    const onOver = (e: MouseEvent) => {
      if (drag.current) return;
      const el = (e.target as Element).closest("[data-cell]");
      const key = el?.getAttribute("data-cell");
      if (!el || !key) {
        if (hot.current.length) clearHot();
        return;
      }
      if (el.classList.contains("cell-hot")) return;
      clearHot();
      el.classList.add("cell-hot");
      hot.current.push(el);
      for (const link of CELL_LINKS.get(key) ?? []) {
        vp.querySelector(`g[data-edge="${CSS.escape(link.edgeKey)}"]`)?.classList.add("edge-hot");
        const peer = vp.querySelector(`[data-cell="${CSS.escape(link.peer)}"]`);
        peer?.classList.add("cell-peer");
        if (peer) hot.current.push(peer);
      }
    };
    const onOut = (e: MouseEvent) => {
      const to = e.relatedTarget as Element | null;
      if (!to || !to.closest?.("[data-cell]")) clearHot();
    };
    vp.addEventListener("mouseover", onOver);
    vp.addEventListener("mouseout", onOut);
    return () => {
      vp.removeEventListener("mouseover", onOver);
      vp.removeEventListener("mouseout", onOut);
    };
  }, [clearHot]);

  // ── left-drag pan (pointer capture; sub-5px movement = a click/spotlight) ──
  const onPointerDown = (e: React.PointerEvent) => {
    if (e.button !== 0) return;
    try {
      vpRef.current?.setPointerCapture(e.pointerId);
    } catch {
      /* pointer capture unavailable for this id — pan still works */
    }
    const el = (e.target as Element).closest("[data-table]");
    drag.current = {
      id: e.pointerId,
      lx: e.clientX,
      ly: e.clientY,
      moved: 0,
      hit: el?.getAttribute("data-table") ?? null,
    };
  };
  const onPointerMove = (e: React.PointerEvent) => {
    const d = drag.current;
    if (!d || d.id !== e.pointerId) return;
    const dx = e.clientX - d.lx;
    const dy = e.clientY - d.ly;
    if (dx === 0 && dy === 0) return;
    d.lx = e.clientX;
    d.ly = e.clientY;
    d.moved += Math.abs(dx) + Math.abs(dy);
    tf.current.x += dx;
    tf.current.y += dy;
    bump();
    apply();
  };
  const onPointerUp = (e: React.PointerEvent) => {
    const d = drag.current;
    if (!d || d.id !== e.pointerId) return;
    drag.current = null;
    if (d.moved < 5) {
      setFocused((prev) => (d.hit && d.hit !== prev ? d.hit : null));
    }
  };

  const resetZoom = () => {
    const vp = vpRef.current;
    if (!vp) return;
    const r = vp.getBoundingClientRect();
    zoomAt(r.left + r.width / 2, r.top + r.height / 2, 1 / tf.current.s);
  };

  return (
    <div className="canvas-wrap">
      <div
        ref={vpRef}
        className={`canvas-viewport${tall ? " tall" : ""}${focused ? " is-focusing" : ""}`}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      >
        <div
          ref={worldRef}
          className="canvas-world"
          style={{ width: WORLD_BOUNDS.w, height: WORLD_BOUNDS.h }}
        >
          {/* category panels (tinted, labelled) — the grouping layer */}
          {PANELS.map((p) => {
            const g = GROUP_BY_ID[p.id];
            const fam = famFor(p.id, theme);
            return (
              <div
                key={p.id}
                className={`panel${p.standalone ? " panel-standalone" : ""}`}
                style={{
                  left: p.x,
                  top: p.y,
                  width: p.w,
                  height: p.h,
                  background: fam.panel,
                  borderColor: fam.border,
                }}
              >
                <div className="panel-head" style={{ color: fam.text }}>
                  <span className="panel-title">{g.label}</span>
                  <span className="panel-count">
                    {p.members.length} table{p.members.length > 1 ? "s" : ""}
                  </span>
                </div>
              </div>
            );
          })}

          {/* relationship layer (above the panels, below the cards) */}
          <svg
            className="edge-svg"
            width={WORLD_BOUNDS.w}
            height={WORLD_BOUNDS.h}
            aria-hidden="true"
          >
            {ROUTED.map((re) => {
              const lit = focused !== null && (re.fromTable === focused || re.toTable === focused);
              const stroke = lit ? LIT : famFor(panelOf(re.fromTable).id, theme).text;
              const cls = [`edge-${re.def.kind}`, focused ? (lit ? "edge-lit" : "edge-dim") : ""]
                .filter(Boolean)
                .join(" ");
              const pts = re.pts;
              return (
                <g key={re.id} className={cls} data-edge={re.def.from}>
                  {/* the port dot marks the exact cell the wire leaves from */}
                  <circle className="edge-port" cx={pts[0][0]} cy={pts[0][1]} r={3.4} fill={stroke} />
                  <polyline
                    points={pts.map((p) => `${p[0]},${p[1]}`).join(" ")}
                    stroke={stroke}
                    vectorEffect="non-scaling-stroke"
                  />
                  <path
                    className="edge-arrow"
                    d={arrowPath(re.dir, re.hx, re.hy)}
                    fill={stroke}
                    stroke={stroke}
                    vectorEffect="non-scaling-stroke"
                  />
                  <title>{`${re.def.label} — ${re.def.from} → ${re.def.to}`}</title>
                </g>
              );
            })}
          </svg>

          {/* the 27 table cards */}
          {Object.values(TABLE_BY_NAME).map((t) => (
            <TableCard
              key={t.name}
              t={t}
              dim={focused !== null && focused !== t.name}
              focused={focused === t.name}
              onFocus={(n) => setFocused((prev) => (prev === n ? null : n))}
            />
          ))}
        </div>

        {/* legend (sticky note) */}
        <aside className={`legend${legendOpen ? "" : " legend-closed"}`}>
          <button
            className="legend-toggle"
            onClick={() => setLegendOpen((v) => !v)}
            aria-expanded={legendOpen}
          >
            {legendOpen ? "fold the legend" : "legend"}
          </button>
          {legendOpen && (
            <div className="legend-body">
              <div className="legend-title">legend</div>
              <div className="legend-row">
                <svg width="46" height="10">
                  <line x1="1" y1="5" x2="45" y2="5" stroke="currentColor" strokeWidth="2.4" />
                </svg>
                <span>
                  enforced <b>FK</b> (foreign_keys = ON)
                </span>
              </div>
              <div className="legend-row">
                <svg width="46" height="10">
                  <line
                    x1="1"
                    y1="5"
                    x2="45"
                    y2="5"
                    stroke="currentColor"
                    strokeWidth="2.4"
                    strokeDasharray="7 5"
                  />
                </svg>
                <span>
                  logical <b>main_id</b> ref — no FK (D-862 survival doctrine)
                </span>
              </div>
              <div className="legend-row">
                <svg width="46" height="10">
                  <line
                    x1="1"
                    y1="5"
                    x2="45"
                    y2="5"
                    stroke="currentColor"
                    strokeWidth="2.4"
                    strokeDasharray="2 4"
                  />
                </svg>
                <span>6.sqm seed provenance (one-time)</span>
              </div>
              <div className="legend-row legend-panels-note">
                <span>
                  every wire wears its <b>source panel&apos;s colour</b>; hover any column cell to
                  trace it to the cell it connects to
                </span>
              </div>
              <div className="legend-row legend-badges">
                <i className="cbadge cbadge-pk">PK</i>
                <i className="cbadge cbadge-fk">FK</i>
                <i className="cbadge cbadge-u">U</i>
                <i className="cbadge cbadge-def">DEF</i>
                <span className="legend-badge-note">primary · foreign · unique · default</span>
              </div>
              <div className="legend-row legend-panels-note">
                <span>
                  a tinted panel = one category / linked cluster · the <b>grey dashed</b> panel =
                  tables with no relationship at all
                </span>
              </div>
              <div className="legend-fams">
                {(Object.keys(GROUP_BY_ID) as (keyof typeof GROUP_BY_ID)[]).map((id) => {
                  const fam = famFor(id, theme);
                  return (
                    <span className="legend-fam" key={id}>
                      <i style={{ background: fam.fill, borderColor: fam.border }} />
                      {GROUP_BY_ID[id].label}
                    </span>
                  );
                })}
              </div>
            </div>
          )}
        </aside>

        {/* controls */}
        <div className="canvas-controls">
          <button title="zoom out" onClick={() => zoomAtCenter(1 / 1.3)}>
            −
          </button>
          <span className="zoom-label" ref={labelRef}>
            100%
          </span>
          <button title="zoom in" onClick={() => zoomAtCenter(1.3)}>
            +
          </button>
          <button title="fit the whole sheet" onClick={fit}>
            fit
          </button>
          <button title="reset to 100%" onClick={resetZoom}>
            1:1
          </button>
        </div>
      </div>
    </div>
  );

  function zoomAtCenter(factor: number) {
    const vp = vpRef.current;
    if (!vp) return;
    const r = vp.getBoundingClientRect();
    zoomAt(r.left + r.width / 2, r.top + r.height / 2, factor);
  }
}
