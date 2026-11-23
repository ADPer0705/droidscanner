import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const rootDir = process.cwd();
const siteDir = path.join(rootDir, "site");
const openwikiDir = path.join(rootDir, "openwiki");
const distDir = path.join(rootDir, "dist-site");
const wikiOutDir = path.join(distDir, "wiki");

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

console.log("Preparing dist-site/...");
fs.rmSync(distDir, { recursive: true, force: true });
fs.cpSync(siteDir, distDir, { recursive: true });

console.log("Exporting DroidScanner OpenWiki static visualizer to dist-site/wiki/...");
execFileSync(
  "npx",
  ["--yes", "openwiki@0.9.0", "visualize", openwikiDir, "--export", wikiOutDir],
  { stdio: "inherit" }
);

const wikiIndexPath = path.join(wikiOutDir, "index.html");
let wikiHtml = fs.readFileSync(wikiIndexPath, "utf8");

assert(
  wikiHtml.includes("<title>OpenWiki visualizer</title>"),
  "Expected OpenWiki <title> placeholder in exported wiki/index.html"
);
wikiHtml = wikiHtml.replace(
  "<title>OpenWiki visualizer</title>",
  '<title>DroidScanner Wiki — Interactive Security Architecture & Docs</title>\n<link rel="icon" type="image/png" href="../assets/favicon.png" />'
);

const brandSvgRegex = /<svg class="lc-logo-full"[\s\S]*?<\/svg>/;
assert(
  brandSvgRegex.test(wikiHtml),
  "Expected OpenWiki logo SVG inside topbar"
);
wikiHtml = wikiHtml.replace(
  brandSvgRegex,
  '<img class="ds-wiki-icon" src="../assets/favicon.png" alt="DroidScanner" width="24" height="24" />'
);

const oldTitleTag = '<div class="title">OpenWiki<small id="wiki-name">wiki visualizer</small></div>';
assert(
  wikiHtml.includes(oldTitleTag),
  "Expected #wiki-name title tag in exported wiki/index.html"
);
wikiHtml = wikiHtml.replace(
  oldTitleTag,
  '<div class="title">DroidScanner<small id="wiki-name">OpenWiki · Security Architecture</small></div>'
);

const spacerTag = '<div class="spacer"></div>';
assert(
  wikiHtml.includes(spacerTag),
  "Expected topbar .spacer element in exported wiki/index.html"
);
wikiHtml = wikiHtml.replace(
  spacerTag,
  '<a class="ds-home-link" href="../">&larr; droidscanner.dearanayji.rocks</a>\n  <div class="spacer"></div>'
);

fs.writeFileSync(wikiIndexPath, wikiHtml, "utf8");

// Fix OpenWiki 0.9.0 visualize bug where multi-line YAML folded descriptions (`description: >-`)
// are exported as literal ">-" in graph.json.
function readYamlDescription(mdPath) {
  if (!fs.existsSync(mdPath)) return null;
  const content = fs.readFileSync(mdPath, "utf8");
  const fmMatch = content.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!fmMatch) return null;
  const lines = fmMatch[1].split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^description:\s*(.*)$/);
    if (!m) continue;
    const val = m[1].trim();
    if (val === ">-" || val === ">" || val === "|-" || val === "|") {
      const parts = [];
      for (let j = i + 1; j < lines.length; j++) {
        if (/^\s+/.test(lines[j])) {
          parts.push(lines[j].trim());
        } else {
          break;
        }
      }
      return parts.join(" ").trim();
    }
    return val.replace(/^["']|["']$/g, "").trim();
  }
  return null;
}

const graphJsonPath = path.join(wikiOutDir, "graph.json");
const graph = JSON.parse(fs.readFileSync(graphJsonPath, "utf8"));
if (Array.isArray(graph.nodes)) {
  for (const node of graph.nodes) {
    if (node.description === ">-" || node.description === ">" || node.description === "|-" || node.description === "|") {
      const mdFile = path.join(openwikiDir, `${node.id}.md`);
      const parsed = readYamlDescription(mdFile);
      if (parsed) {
        node.description = parsed;
      }
    }
  }
  fs.writeFileSync(graphJsonPath, JSON.stringify(graph, null, 2), "utf8");
}

// Tailor OpenWiki visual theme to DroidScanner's Obsidian · Signal Emerald · Radar Cyan identity.
const wikiCssPath = path.join(wikiOutDir, "styles.css");
const droidScannerWikiOverrides = `
/* DroidScanner Obsidian-Emerald-Cyan Theme Alignment */
:root {
  --lc-dark: #060a0d;
  --lc-card: #0e161c;
  --lc-surface: #f3f7f6;
  --lc-border-dark: #1c2c36;
  --lc-border: #d2e0dc;
  --lc-muted: #8299a4;
  --lc-body: #c9d8de;
  --lc-white: #f0f7f9;
  --lc-dark-text: #0c1b20;
  --lc-blue: #10b981;
  --lc-blue-hover: #34d399;
  --lc-blue-bg: rgba(16, 185, 129, 0.14);
  --lc-rose: #0e7490;
  --bg: #060a0d;
  --panel: #0e161c;
  --edge: #1c2c36;
  --text: #c9d8de;
  --heading: #f0f7f9;
  --muted: #8299a4;
  --tag-bg: rgba(16, 185, 129, 0.14);
  --tag-text: #34d399;
  --graph-bg: #05080b;
  --node-label: #8299a4;
  --code-bg: #091015;
}

[data-theme="light"] {
  --bg: #f3f7f6;
  --panel: #ffffff;
  --edge: #d2e0dc;
  --text: #334850;
  --heading: #0c1b20;
  --muted: #5a717a;
  --lc-blue: #047857;
  --lc-blue-hover: #059669;
  --lc-rose: #0e7490;
  --tag-bg: rgba(4, 120, 87, 0.11);
  --tag-text: #047857;
  --graph-bg: #eaf1ef;
  --node-label: #334850;
  --code-bg: #eaf1ef;
}

.ds-wiki-icon {
  border-radius: 6px;
  display: block;
}

.ds-home-link {
  display: inline-flex;
  align-items: center;
  padding: 5px 11px;
  border-radius: 7px;
  border: 1px solid var(--edge);
  background: var(--panel);
  color: var(--heading);
  font-size: 12px;
  font-weight: 500;
  text-decoration: none;
  transition: border-color 0.16s ease, color 0.16s ease;
}

.ds-home-link:hover {
  border-color: var(--lc-blue);
  color: var(--lc-blue-hover);
  text-decoration: none;
}

.reader code,
.md code {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.reader pre code,
.md pre code {
  overflow-wrap: normal;
  word-break: normal;
}

@media (max-width: 680px) {
  .ds-home-link {
    display: none;
  }
}
`;
fs.appendFileSync(wikiCssPath, droidScannerWikiOverrides, "utf8");

const requiredOutputs = [
  "index.html",
  "styles.css",
  "app.js",
  "CNAME",
  ".nojekyll",
  "assets/droidscanner-icon.png",
  "assets/droidscanner-icon.svg",
  "assets/favicon.png",
  "wiki/index.html",
  "wiki/graph.json",
  "wiki/styles.css"
];

for (const rel of requiredOutputs) {
  const target = path.join(distDir, rel);
  assert(fs.existsSync(target), `Missing required build artifact: ${rel}`);
}

console.log("Successfully built dist-site/ with DroidScanner landing page + themed OpenWiki docs.");
