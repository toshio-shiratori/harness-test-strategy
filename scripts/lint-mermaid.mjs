// Markdown 内の ```mermaid ブロックを mermaid-cli (mmdc) で実際にレンダリングし、次の 2 つを検出して失敗にする。
//   1. 構文エラー(mmdc が非 0 で終了する)
//   2. 描画した SVG の幅(viewBox)が BODY_WIDTH(900px)を超える図。GitHub の本文の幅に収まらず、縮小されて文字が読めなくなる
// 高さは検査しない(縦に長い図は等倍で表示され、文字は読める)。
// ローカル (`npm run lint`) と CI で同一のコマンドを使う。閾値と viewBox の読み方は measure-mermaid.mjs と共有する(mermaid-common.mjs)。
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative } from "node:path";
import { BODY_WIDTH, blockLines, root, svgSize, walk } from "./mermaid-common.mjs";

const targets = [...walk(root)].map((file) => ({ file, lines: blockLines(file) })).filter((t) => t.lines.length > 0);
if (targets.length === 0) {
  console.log("mermaid ブロックを含む Markdown はありません");
  process.exit(0);
}

const mmdc = join(root, "node_modules", ".bin", "mmdc");
const out = mkdtempSync(join(tmpdir(), "mermaid-lint-"));
let failed = 0;
let tooWide = 0;
try {
  targets.forEach(({ file, lines }, n) => {
    const rel = relative(root, file);
    const dir = join(out, String(n));
    mkdirSync(dir);
    const problems = [];
    try {
      // Markdown 内の全 mermaid ブロックを SVG 化する(出力は <dir>/out-<番号>.svg)。構文エラーがあると非 0 で終了する。
      execFileSync(mmdc, ["-i", file, "-o", join(dir, "out.md"), "-e", "svg", "-p", join(root, "puppeteer-config.json"), "-q"], {
        stdio: ["ignore", "pipe", "pipe"],
        encoding: "utf8",
      });
      // ブロック数と描画された図の数がずれると行番号を対応づけられないので、そのファイルは失敗にする
      const rendered = readdirSync(dir).filter((f) => /^out-\d+\.svg$/.test(f)).length;
      if (rendered !== lines.length) {
        throw new Error(`ブロック数(${lines.length})と描画された図の数(${rendered})が一致しません`);
      }
      lines.forEach((line, i) => {
        const size = svgSize(readFileSync(join(dir, `out-${i + 1}.svg`), "utf8"));
        if (!size) throw new Error(`${rel}:${line} viewBox を読み取れません`);
        if (size.width > BODY_WIDTH) problems.push(`${rel}:${line} 幅 ${size.width}px が閾値 ${BODY_WIDTH}px を超えています`);
      });
    } catch (e) {
      failed++;
      console.error(`FAIL ${rel}\n${e.stderr || e.stdout || e.message}`);
      return;
    }
    if (problems.length === 0) {
      console.log(`ok   ${rel}`);
    } else {
      failed++;
      tooWide += problems.length;
      console.error(`FAIL ${rel}\n${problems.map((p) => `  ${p}`).join("\n")}`);
    }
  });
} finally {
  rmSync(out, { recursive: true, force: true });
}
if (failed > 0) {
  const wide = tooWide > 0 ? `(うち幅が ${BODY_WIDTH}px を超える図 ${tooWide} 枚。TB にする、段に分ける、ノードを減らすなどで幅に収める。数値は npm run measure:mermaid -- <md> で見られる)` : "";
  console.error(`${failed} 件のファイルで Mermaid のエラーがあります${wide}`);
  process.exit(1);
}
