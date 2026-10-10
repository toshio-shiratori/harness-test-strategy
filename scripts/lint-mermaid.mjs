// Markdown 内の ```mermaid ブロックを mermaid-cli (mmdc) で実際にレンダリングし、構文エラーを検出する。
// ローカル (`npm run lint`) と CI で同一のコマンドを使う。
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, readdirSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative, resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const IGNORED = new Set(["node_modules", ".git", ".obsidian"]);

function* walk(dir) {
  for (const name of readdirSync(dir)) {
    if (IGNORED.has(name)) continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* walk(path);
    else if (name.endsWith(".md")) yield path;
  }
}

const targets = [...walk(root)].filter((f) => /^```mermaid\s*$/m.test(readFileSync(f, "utf8")));
if (targets.length === 0) {
  console.log("mermaid ブロックを含む Markdown はありません");
  process.exit(0);
}

const mmdc = join(root, "node_modules", ".bin", "mmdc");
const out = mkdtempSync(join(tmpdir(), "mermaid-lint-"));
let failed = 0;
try {
  for (const file of targets) {
    const rel = relative(root, file);
    try {
      // Markdown 内の全 mermaid ブロックを SVG 化する。構文エラーがあると非 0 で終了する。
      execFileSync(
        mmdc,
        ["-i", file, "-o", join(out, "out.md"), "-e", "svg", "-p", join(root, "puppeteer-config.json"), "-q"],
        { stdio: ["ignore", "pipe", "pipe"], encoding: "utf8" },
      );
      console.log(`ok   ${rel}`);
    } catch (e) {
      failed++;
      console.error(`FAIL ${rel}\n${e.stderr || e.stdout || e.message}`);
    }
  }
} finally {
  rmSync(out, { recursive: true, force: true });
}
if (failed > 0) {
  console.error(`${failed} 件のファイルで Mermaid のエラーがあります`);
  process.exit(1);
}
