import type { Metadata } from "next";
import { Fredoka, Patrick_Hand, JetBrains_Mono } from "next/font/google";
import "./globals.css";

const fredoka = Fredoka({
  subsets: ["latin"],
  weight: ["600", "700"],
  variable: "--font-fredoka",
  display: "swap",
});

const patrick = Patrick_Hand({
  subsets: ["latin"],
  weight: "400",
  variable: "--font-patrick",
  display: "swap",
});

const mono = JetBrains_Mono({
  subsets: ["latin"],
  weight: ["600", "700"],
  variable: "--font-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: "ANI-KUTA — Database Study Sheet",
  description:
    "Interactive ERD canvas of the ANI-KUTA SQLDelight database: schema v7, 27 tables, FK linking, demo data, migration ladder and the issues register.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html
      lang="en"
      data-zcode-design
      className={`${fredoka.variable} ${patrick.variable} ${mono.variable}`}
    >
      <body>{children}</body>
    </html>
  );
}
