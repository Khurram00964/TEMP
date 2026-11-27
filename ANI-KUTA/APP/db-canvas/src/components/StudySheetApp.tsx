"use client";

import { useState } from "react";
import { CanvasSurface } from "./CanvasSurface";
import { DocsSection } from "./DocsSection";
import { ThemeSwitch } from "./ThemeContext";

type View = "canvas" | "docs";

export function StudySheetApp() {
  const [collapsed, setCollapsed] = useState(false);
  const [view, setView] = useState<View>("canvas");

  return (
    <main className={collapsed ? "app collapsed" : "app"}>
      {/* ── the header: full title block when open, a slim bar when collapsed ── */}
      {collapsed ? (
        <div className="topbar">
          <span className="topbar-title">ANI-KUTA — Database Study Sheet</span>
          <span className="topbar-meta">schema v7 · 27 tables · 25 links</span>
          <div className="topbar-actions">
            <ViewTabs view={view} setView={setView} />
            <ThemeSwitch compact />
            <button
              className="collapse-btn"
              onClick={() => setCollapsed(false)}
              title="expand the header"
              aria-expanded={false}
            >
              <Chevron down />
            </button>
          </div>
        </div>
      ) : (
        <div className="sheet sheet-header-zone">
          <div className="sheet-header">
            <div className="title-block">
              <h1>ANI-KUTA — the Database Study Sheet</h1>
              <p className="title-sub">
                an interactive ERD canvas + annotated notes on the SQLDelight schema
              </p>
            </div>
            <div className="header-side">
              <div className="meta-pill">
                schema <b>v7</b> · <b>27</b> tables · <b>25</b> links · SQLDelight <b>2.0.2</b>
              </div>
              <div className="header-actions">
                <ViewTabs view={view} setView={setView} />
                <ThemeSwitch />
                <button
                  className="collapse-btn"
                  onClick={() => setCollapsed(true)}
                  title="collapse the header for more canvas"
                  aria-expanded
                >
                  <Chevron />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── the two views ── */}
      {view === "canvas" ? (
        <CanvasSurface tall={collapsed} />
      ) : (
        <div className="docs-view">
          <DocsSection />
        </div>
      )}
    </main>
  );
}

function ViewTabs({ view, setView }: { view: View; setView: (v: View) => void }) {
  return (
    <div className="view-tabs" role="tablist" aria-label="views">
      <button
        role="tab"
        aria-selected={view === "canvas"}
        className={`view-tab${view === "canvas" ? " active" : ""}`}
        onClick={() => setView("canvas")}
      >
        Canvas
      </button>
      <button
        role="tab"
        aria-selected={view === "docs"}
        className={`view-tab${view === "docs" ? " active" : ""}`}
        onClick={() => setView("docs")}
      >
        Documentation
      </button>
    </div>
  );
}

function Chevron({ down = false }: { down?: boolean }) {
  const d = down ? "M3 6 L8 11 L13 6" : "M3 10 L8 5 L13 10";
  return (
    <svg width="14" height="14" viewBox="0 0 16 16" aria-hidden="true">
      <path
        d={d}
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
