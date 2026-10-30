// Markdown 内の ```mermaid ブロックを mermaid-cli (mmdc) で描画し、図ごとの幅・高さ・縦横比を測る。
// 構文エラーの検出は lint-mermaid.mjs の役割で、こちらは「描画したときの見え方」の確認に使う。
// 使い方: node scripts/measure-mermaid.mjs [--png] [<file.md> ...]
//   引数を省略すると docs/testing/ 配下の全 .md を対象にする。
//   --png を付けると PNG も出してパスを表示する。この一時ディレクトリは消さない(Read で画像を見るため)。
//   PNG は mmdc の既定の表示幅(800px)に収まる大きさで出るので、幅の広い図は縮小された見え方になる。
// 描画結果はすべて OS の一時ディレクトリに出す。リポジトリ内には何も書かない。
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative, resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
// GitHub の本文の幅(約 900px)。これを超える図は縮小されて文字が読みにくくなる
const BODY_WIDTH = 900;

const argv = process.argv.slice(2);
const png = argv.includes("--png");
const unknown = argv.filter((a) => a.startsWith("-") && a !== "--png");
if (unknown.length > 0) {
  console.error(`未知のオプション: ${unknown.join(" ")}\n使い方: node scripts/measure-mermaid.mjs [--png] [<file.md> ...]`);
  process.exit(2);
}

function* walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* walk(path);
    else if (name.endsWith(".md")) yield path;
  }
}

const given = argv.filter((a) => !a.startsWith("-")).map((a) => resolve(process.env.INIT_CWD ?? process.cwd(), a)); // npm run は cwd をリポジトリに変えるので、呼び出し元の cwd を基準にする
const missing = given.filter((f) => !existsSync(f));
if (missing.length > 0) {
  console.error(`ファイルが見つかりません: ${missing.join(" ")}`);
  process.exit(2);
}
const files = given.length > 0 ? given : [...walk(join(root, "docs", "testing"))];

// mermaid ブロックの開始行(1 始まり)。mmdc が図を拾う正規表現(``` と ::: の囲み、行頭の空白を許す)に揃え、
// mmdc が出力する図の番号と同じ順に並べる
const BLOCK = /^[^\S\n]*[`:]{3}mermaid[^\S\n]*\r?$/gm;
const blockLines = (file) => {
  const text = readFileSync(file, "utf8");
  return [...text.matchAll(BLOCK)].map((m) => text.slice(0, m.index).split("\n").length);
};

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
        // 図の大きさは svg 要素の viewBox ("minX minY width height") から読む
        const box = /<svg[^>]*\sviewBox="([^"]+)"/.exec(svg)?.[1].trim().split(/\s+/).map(Number);
        if (!box || box.length !== 4 || box.some(Number.isNaN)) throw new Error(`${rel}:${line} viewBox を読み取れません`);
        rows.push({ line, i, width: Math.round(box[2]), height: Math.round(box[3]) });
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
