"use client";

import { memo } from "react";
import { demoColWidths, famFor } from "@/src/data/tables";
import { useTheme } from "./ThemeContext";
import type { Col, TableDef } from "@/src/data/tables";

// ── demo-value rendering ─────────────────────────────────────────────────────
const TS_COLS = /(_at|timestamp|added_at|queued_at|started_at|ended_at|sent_at|discovered_at|acknowledged_at|fetched_at|expires_at|scheduled_at|actual_at|rated_at|verified_at|downloaded_at|last_watched_at|completed_at|first_watched_at|created_at|updated_at)$/;

function fmtVal(colName: string, v: string | number | null): string {
  if (v === null) return "NULL";
  if (typeof v === "number") {
    if (TS_COLS.test(colName)) {
      const d = new Date(v);
      const pad = (n: number) => String(n).padStart(2, "0");
      return `${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
    }
    return v.toLocaleString("en-US");
  }
  if (colName === "cache_key") return `${v.slice(0, 14)}…`;
  if (colName === "data_json") return "[json…]";
  return v;
}

function isTexty(colName: string, v: string | number | null): boolean {
  return typeof v === "string" && !TS_COLS.test(colName) && (v.length > 14 || /[a-zA-Z]/.test(v));
}

/** the full key story of a column, for the cell tooltip */
function cellTip(c: Col): string {
  const bits: string[] = [];
  if (c.pk) bits.push("PRIMARY KEY");
  if (c.fk) bits.push(`FOREIGN KEY → ${c.fk}`);
  if (c.u && !c.pk) bits.push("UNIQUE");
  if (c.nn && !c.def) bits.push("NOT NULL");
  if (c.def) bits.push(`DEFAULT ${c.def}`);
  if (c.note) bits.push(c.note);
  if (bits.length === 0) bits.push("column");
  return bits.join(" · ");
}

function ColRow({ t, c }: { t: TableDef; c: Col }) {
  const badges: string[] = [];
  if (c.pk) badges.push("PK");
  if (c.fk) badges.push("FK");
  if (c.u && !c.pk) badges.push("U");
  if (c.def) badges.push("DEF");
  return (
    <div
      className={`colrow${c.fk ? " is-fk" : ""}${c.pk ? " is-pk" : ""}`}
      data-cell={`${t.name}.${c.name}`}
      title={cellTip(c)}
    >
      <span className="col-name">{c.name}</span>
      <span className="col-type">{c.type}</span>
      <span className="col-badges">
        {badges.map((b) => (
          <i key={b} className={`cbadge cbadge-${b.toLowerCase()}`}>
            {b}
          </i>
        ))}
      </span>
    </div>
  );
}

function TableCardInner({ t, dim, focused, onFocus }: {
  t: TableDef;
  dim: boolean;
  focused: boolean;
  onFocus: (name: string) => void;
}) {
  const { theme } = useTheme();
  const fam = famFor(t.group, theme);
  const pkCols = t.cols.filter((c) => c.pk).map((c) => c.name);
  const colCount = t.cols.length > 16 ? 2 : 1;
  const colRows = Math.ceil(t.cols.length / colCount);
  const style = {
    left: t.x,
    top: t.y,
    width: t.w,
    ["--fam-border"]: fam.border,
    ["--rows"]: colRows,
  } as React.CSSProperties;
  return (
    <div
      className={`tcard${dim ? " tcard-dim" : ""}${focused ? " tcard-focused" : ""}`}
      data-table={t.name}
      style={style}
      onClick={(ev) => {
        ev.stopPropagation();
        onFocus(t.name);
      }}
    >
      <div className="tcard-header">
        <span className="tcard-name">{t.name}</span>
        <span className="tcard-file">{t.file}</span>
      </div>
      <div className={`tcard-cols${colCount === 2 ? " two" : ""}`}>
        {t.cols.map((c) => (
          <ColRow key={c.name} t={t} c={c} />
        ))}
      </div>
      {t.demo.length > 0 && (
        <div className="tcard-demo">
          <div className="tcard-demo-label">
            demo data · {t.demo.length} rows
            {pkCols.length > 0 ? ` · pk: ${pkCols.join("+")}` : ""}
          </div>
          <div
            className="demo-table"
            style={{
              ["--dc"]: t.demoCols.length,
              gridTemplateColumns: demoColWidths(t)
                .map((w) => `${w}px`)
                .join(" "),
            } as React.CSSProperties}
          >
            {t.demoCols.map((dc) => (
              <span className="dhead" key={`h-${dc}`} title={dc}>
                {dc}
              </span>
            ))}
            {t.demo.map((row, i) =>
              t.demoCols.map((dc) => (
                <span
                  className={`dval${isTexty(dc, row[dc]) ? " dval-text" : ""}${row[dc] === null ? " dval-null" : ""}`}
                  key={`${i}-${dc}`}
                  title={String(row[dc] ?? "NULL")}
                >
                  {fmtVal(dc, row[dc])}
                </span>
              )),
            )}
          </div>
          {t.demoNote && <div className="tcard-demo-note">{t.demoNote}</div>}
        </div>
      )}
    </div>
  );
}

export const TableCard = memo(TableCardInner);
