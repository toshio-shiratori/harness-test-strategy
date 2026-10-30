// Markdown 内の ```mermaid ブロックを mermaid-cli (mmdc) で描画し、図ごとの幅・高さ・縦横比を測る。
// 構文エラーと、幅が閾値を超える図(失敗扱い)の検出は lint-mermaid.mjs の役割で、こちらは幅・高さ・縦横比の数値を見るのに使う。
// 閾値(900px)、走査の範囲、図の拾い方、viewBox の読み方は mermaid-common.mjs で lint-mermaid.mjs と共有する。
// 使い方: node scripts/measure-mermaid.mjs [--png] [<file.md> ...]
//   引数を省略するとリポジトリの全 .md を対象にする(lint-mermaid.mjs と同じ範囲。node_modules、.git、.obsidian、.claude/worktrees は除く)。
//   --png を付けると PNG も出してパスを表示する。この一時ディレクトリは消さない(Read で画像を見るため)。
//   PNG は mmdc の既定の表示幅(800px)に収まる大きさで出るので、幅の広い図は縮小された見え方になる。
// 描画結果はすべて OS の一時ディレクトリに出す。リポジトリ内には何も書かない。
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative, resolve } from "node:path";
import { BODY_WIDTH, blockLines, root, svgSize, walk } from "./mermaid-common.mjs";

const argv = process.argv.slice(2);
const png = argv.includes("--png");
const unknown = argv.filter((a) => a.startsWith("-") && a !== "--png");
if (unknown.length > 0) {
  console.error(`未知のオプション: ${unknown.join(" ")}\n使い方: node scripts/measure-mermaid.mjs [--png] [<file.md> ...]`);
  process.exit(2);
}

const given = argv.filter((a) => !a.startsWith("-")).map((a) => resolve(process.env.INIT_CWD ?? process.cwd(), a)); // npm run は cwd をリポジトリに変えるので、呼び出し元の cwd を基準にする
const missing = given.filter((f) => !existsSync(f));
if (missing.length > 0) {
  console.error(`ファイルが見つかりません: ${missing.join(" ")}`);
  process.exit(2);
}
const files = given.length > 0 ? given : [...walk(root)];

const targets = files.map((file) => ({ file, lines: blockLines(file) })).filter((t) => t.lines.length > 0);
if (targets.length === 0) {
  console.log("mermaid ブロックを含む Markdown はありません");
  process.exit(0);
}

const mmdc = join(root, "node_modules", ".bin", "mmdc");
const puppeteer = join(root, "puppeteer-config.json");
const out = mkdtempSync(join(tmpdir(), "mermaid-measure-"));
let failed = 0;
let over = 0;
try {
  console.log("file:line | block | 幅(px) | 高さ(px) | 縦横比(幅/高さ) | 本文幅での倍率 | 判定");
  targets.forEach(({ file, lines }, n) => {
    const rel = relative(root, file);
    const dir = join(out, String(n));
    mkdirSync(dir);
    const rows = [];
    try {
      // Markdown 内の全 mermaid ブロックを SVG 化する(出力は <dir>/out-<番号>.svg)
      execFileSync(mmdc, ["-i", file, "-o", join(dir, "out.md"), "-e", "svg", "-p", puppeteer, "-q"], {
        stdio: ["ignore", "pipe", "pipe"],
        encoding: "utf8",
      });
      if (png) {
        execFileSync(mmdc, ["-i", file, "-o", join(dir, "out.md"), "-e", "png", "-p", puppeteer, "-q"], {
          stdio: ["ignore", "pipe", "pipe"],
          encoding: "utf8",
        });
      }
      // ブロック数と描画された図の数がずれると行番号を対応づけられないので、そのファイルは失敗にする
      const rendered = readdirSync(dir).filter((f) => /^out-\d+\.svg$/.test(f)).length;
      if (rendered !== lines.length) {
        throw new Error(`ブロック数(${lines.length})と描画された図の数(${rendered})が一致しません`);
      }
      lines.forEach((line, i) => {
        const svg = readFileSync(join(dir, `out-${i + 1}.svg`), "utf8");
        const size = svgSize(svg);
        if (!size) throw new Error(`${rel}:${line} viewBox を読み取れません`);
        rows.push({ line, i, ...size });
      });
    } catch (e) {
      failed++;
      console.error(`FAIL ${rel}\n${e.stderr || e.stdout || e.message}`);
      return;
    }
    for (const { line, i, width, height } of rows) {
      const scale = Math.min(1, BODY_WIDTH / width);
      const mark = width > BODY_WIDTH ? `!! 幅が ${BODY_WIDTH}px を超える(${Math.round(scale * 100)}% に縮小される)` : "ok";
      if (width > BODY_WIDTH) over++;
      console.log(`${rel}:${line} | ${i + 1} | ${width} | ${height} | ${(width / height).toFixed(2)} | ${scale.toFixed(2)} | ${mark}`);
      if (png) console.log(`  png: ${join(dir, `out-${i + 1}.png`)}`);
    }
  });
} finally {
  // --png のときは画像を見るために残す。付けないときだけ後始末する
  if (!png) rmSync(out, { recursive: true, force: true });
}
console.log(`幅が ${BODY_WIDTH}px を超える図: ${over} 枚`);
if (png) console.log(`PNG の出力先(自動では消えない): ${out}`);
if (failed > 0) {
  console.error(`${failed} 件の描画・計測に失敗しました`);
  process.exit(1);
}
