"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import type { ThemeName } from "@/src/data/colors";

const KEY = "anikuta-db-canvas-theme";

interface ThemeCtx {
  theme: ThemeName;
  setTheme: (t: ThemeName) => void;
}

const Ctx = createContext<ThemeCtx>({ theme: "light", setTheme: () => {} });

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<ThemeName>("light");

  useEffect(() => {
    const saved = window.localStorage.getItem(KEY) as ThemeName | null;
    if (saved === "dark" || saved === "gray" || saved === "light") setThemeState(saved);
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem(KEY, theme);
  }, [theme]);

  const setTheme = useCallback((t: ThemeName) => setThemeState(t), []);
  return <Ctx.Provider value={{ theme, setTheme }}>{children}</Ctx.Provider>;
}

export const useTheme = () => useContext(Ctx);

const MODES: { id: ThemeName; label: string }[] = [
  { id: "light", label: "Light" },
  { id: "dark", label: "Dark" },
  { id: "gray", label: "Gray" },
];

/** the three-mode switcher (text pills — no emoji icons per the design system) */
export function ThemeSwitch({ compact = false }: { compact?: boolean }) {
  const { theme, setTheme } = useTheme();
  return (
    <div className={`theme-switch${compact ? " compact" : ""}`} role="group" aria-label="UI mode">
      {MODES.map((m) => (
        <button
          key={m.id}
          className={`theme-btn${theme === m.id ? " active" : ""}`}
          onClick={() => setTheme(m.id)}
          aria-pressed={theme === m.id}
          title={`${m.label} mode`}
        >
          {m.label}
        </button>
      ))}
    </div>
  );
}
