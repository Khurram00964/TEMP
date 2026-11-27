// ─────────────────────────────────────────────────────────────────────────────
// Color plumbing for the three UI modes (light / dark / gray).
// The pastel "highlighter" families are authored once in their LIGHT form and
// the dark/gray triplets are DERIVED, so a family is defined exactly once.
// Handles hex AND rgb()/rgba() strings (the light panel tints carry alpha so
// the graph-paper grid shows through).
// ─────────────────────────────────────────────────────────────────────────────

export type ThemeName = "light" | "dark" | "gray";

export interface FamColors {
  /** chip / header fill */
  fill: string;
  /** border + arrow colour */
  border: string;
  /** dark accent text (on fills) */
  text: string;
  /** soft panel tint */
  panel: string;
}

interface Rgba {
  r: number;
  g: number;
  b: number;
  a: number;
}

function parseColor(s: string): Rgba {
  const str = s.trim();
  if (str.startsWith("#")) {
    const h = str.slice(1);
    const v = parseInt(h.length === 3 ? h.replace(/./g, "$&$&") : h, 16);
    return { r: (v >> 16) & 255, g: (v >> 8) & 255, b: v & 255, a: 1 };
  }
  const m = str.match(/rgba?\(([^)]+)\)/);
  if (m) {
    const parts = m[1].split(",").map((x) => parseFloat(x.trim()));
    return { r: parts[0] || 0, g: parts[1] || 0, b: parts[2] || 0, a: parts[3] ?? 1 };
  }
  return { r: 0, g: 0, b: 0, a: 1 };
}

function toCss(c: Rgba): string {
  const cl = (n: number) => Math.max(0, Math.min(255, Math.round(n)));
  if (c.a >= 0.999) {
    const h = (n: number) => cl(n).toString(16).padStart(2, "0");
    return `#${h(c.r)}${h(c.g)}${h(c.b)}`;
  }
  return `rgba(${cl(c.r)}, ${cl(c.g)}, ${cl(c.b)}, ${Math.round(c.a * 100) / 100})`;
}

/** linear mix of two colors (t = share of b); alpha interpolates too */
export function mix(a: string, b: string, t: number): string {
  const c1 = parseColor(a);
  const c2 = parseColor(b);
  return toCss({
    r: c1.r + (c2.r - c1.r) * t,
    g: c1.g + (c2.g - c1.g) * t,
    b: c1.b + (c2.b - c1.b) * t,
    a: c1.a + (c2.a - c1.a) * t,
  });
}

function luminance(c: Rgba): number {
  return (0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b) / 255;
}

/** desaturate to a near-gray, keeping the same lightness character + alpha */
function grayify(s: string, keep = 0.06): string {
  const c = parseColor(s);
  const l = 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b;
  return toCss({
    r: l + (c.r - l) * keep,
    g: l + (c.g - l) * keep,
    b: l + (c.b - l) * keep,
    a: c.a,
  });
}

const DARK_BASE = "#12161f";
const DARK_PAPER = "#0e1117";
const LIGHT_TEXT_BASE = "#12202e";

export function darkTriplet(light: FamColors): FamColors {
  return {
    // the pastel fill becomes readable dark text
    text: luminance(parseColor(light.fill)) > 0.5 ? light.fill : mix(light.fill, "#e8eef8", 0.35),
    // the border brightens a touch so wires stay visible on dark paper
    border: mix(light.border, "#e8eef8", 0.14),
    fill: mix(light.border, DARK_BASE, 0.84),
    panel: mix(light.panel, DARK_PAPER, 0.72),
  };
}

export function grayTriplet(light: FamColors): FamColors {
  return {
    text: grayify(mix(light.text, LIGHT_TEXT_BASE, 0.35), 0.04),
    border: grayify(mix(light.border, "#3a4150", 0.3), 0.04),
    fill: grayify(light.fill, 0.05),
    panel: grayify(light.panel, 0.05),
  };
}
