// lint-mermaid.mjs と measure-mermaid.mjs が共有する定義。
// 走査の範囲、図の拾い方(mmdc と同じ正規表現)、閾値、SVG の大きさの読み方を一箇所に置き、2 つのスクリプトがずれないようにする。
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";

export const root = resolve(import.meta.dirname, "..");

// GitHub の本文の幅(約 900px)。これを超える図は縮小されて文字が読みにくくなる
export const BODY_WIDTH = 900;

const IGNORED = new Set(["node_modules", ".git", ".obsidian"]);

// リポジトリの全 .md を走査する(node_modules、.git、.obsidian、.claude/worktrees は除く)
export function* walk(dir = root) {
  for (const name of readdirSync(dir)) {
    if (IGNORED.has(name)) continue;
    const path = join(dir, name);
    // worktree 内の複製は対象外。追跡ファイルは各チェックアウトで検査される
    if (relative(root, path) === join(".claude", "worktrees")) continue;
    if (statSync(path).isDirectory()) yield* walk(path);
    else if (name.endsWith(".md")) yield path;
  }
}

// mermaid ブロックの開始行(1 始まり)。mmdc が図を拾う正規表現(``` と ::: の囲み、行頭の空白を許す)に揃え、
// mmdc が出力する図の番号(out-N.svg)と同じ順に並べる
const BLOCK = /^[^\S\n]*[`:]{3}mermaid[^\S\n]*\r?$/gm;
export const blockLines = (file) => {
  const text = readFileSync(file, "utf8");
  return [...text.matchAll(BLOCK)].map((m) => text.slice(0, m.index).split("\n").length);
};

// 図の大きさは svg 要素の viewBox ("minX minY width height") から読む。読めなければ null
export const svgSize = (svg) => {
  const box = /<svg[^>]*\sviewBox="([^"]+)"/.exec(svg)?.[1].trim().split(/\s+/).map(Number);
  if (!box || box.length !== 4 || box.some(Number.isNaN)) return null;
  return { width: Math.round(box[2]), height: Math.round(box[3]) };
};
