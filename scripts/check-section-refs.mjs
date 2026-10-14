// 02a / 02b / 02c の節番号参照とリンクを検査する。markdownlint は節番号の誤りを検出できないため、ここで補う。
//   (a) `02a §2.3` のような参照の節が、参照先ファイルに実在する。
//       `02b §6 / §1.4`、`02a §2.2・§2.4` のように接頭辞なしの `§y` が続くときは、直前の接頭辞のファイルの節として検査する。
//       3 ファイル内の接頭辞なしの `§x` は、そのファイル内の節を指す。未知の `02d` などの接頭辞はエラーにする。
//   (b) 分割前の旧形式（`02 §x`、`02 の §x`、`(02、§x)`、`02-test-architecture`）が残っていない。
//   (c) 3 ファイルへのリンクが切れていない。
//       ファイル名が `02x-….md` なのに docs/testing/ 直下の 3 ファイルのどれでもないもの、アンカーが無いもの、
//       リンクの表示テキストの `02a §x` と、リンク先（ファイル・アンカーの節）が食い違うものはエラーにする。
// 検出できないもの: 節は実在するが中身が違う参照（例: 実行境界の説明に Testcontainers の節番号を書く）。レビューで補う。
// ローカル (`npm run lint`) と CI で同一のコマンドを使う。
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { basename, dirname, relative, resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const SELF = "scripts/check-section-refs.mjs";
const DOCS = {
  "02a": "docs/testing/02a-types-and-pbt.md",
  "02b": "docs/testing/02b-execution-and-mutation.md",
  "02c": "docs/testing/02c-e2e.md",
};
const DOC_PATHS = new Set(Object.values(DOCS));

// 追跡ファイル + 未追跡（.gitignore 対象外）。worktree 内の複製は対象外。
const files = execFileSync("git", ["ls-files", "--cached", "--others", "--exclude-standard", "-z"], {
  cwd: root,
  encoding: "utf8",
})
  .split("\0")
  .filter((f) => f && f !== SELF && !f.startsWith(".claude/worktrees/") && existsSync(resolve(root, f)));

// フェンス付きコードブロックの開閉（``` と ~~~、字下げ付き）を追跡する
const makeFence = () => {
  let marker = null;
  return (line) => {
    const m = line.match(/^\s*(`{3,}|~{3,})/);
    if (m) {
      if (marker === null) {
        marker = m[1];
        return true;
      }
      if (m[1][0] === marker[0] && m[1].length >= marker.length) {
        marker = null;
        return true;
      }
    }
    return false;
  };
};

// GitHub の見出しアンカーの規則（小文字化し、文字・数字・_・-・空白以外を除き、空白を - にする）
const slugOf = (heading) =>
  heading
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/[`*]/g, "")
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{M}\p{N}_\- ]/gu, "")
    .replace(/ /g, "-");

const sections = {}; // 02a -> Set("0", "1", "1.1", ...)
const anchors = {}; // 02a -> Map(slug -> 節番号 または null)
for (const [key, path] of Object.entries(DOCS)) {
  sections[key] = new Set();
  anchors[key] = new Map();
  const seen = new Map();
  const isFenceLine = makeFence();
  let inFence = false;
  for (const line of readFileSync(resolve(root, path), "utf8").split("\n")) {
    if (isFenceLine(line)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;
    const h = line.match(/^#{1,6}\s+(.*?)\s*#*$/);
    if (!h) continue;
    const n = h[1].match(/^(\d+(?:\.\d+)*)\.?(?:\s|$)/);
    if (n) sections[key].add(n[1]);
    const base = slugOf(h[1]);
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    anchors[key].set(count === 0 ? base : `${base}-${count}`, n ? n[1] : null);
  }
}
const keyOfPath = (p) => Object.keys(DOCS).find((k) => DOCS[k] === p);

const errors = [];
const fail = (file, lineNo, msg) => errors.push(`${file}:${lineNo}: ${msg}`);

// 旧形式: `02 §x`、`02 の §x`、空白 2 つ、`(02、§x)` など（02 の直後が a-z でなく、短い接続の後に § が来る）
const OLD_REF = /(?<![0-9A-Za-z-])`?02`?[\s、,の]{0,6}§/;
const OLD_FILE = /02-test-architecture/;
const DOC_NAME = /02[a-z]-[A-Za-z0-9-]*\.md/g;
// § の参照 1 件。接頭辞（01〜09 + 任意の a-z）は省略できる
const SECTION_TOKEN = /(?:(?<![0-9A-Za-z-])`?(0\d[a-z]?)`?\s?)?§(\d+(?:\.\d+)*)/g;
const CONNECTOR = /^[\s・/、,〜~]{1,4}$/; // 「02b §6 / §1.4」「02a §2.2・§2.4」のような並び
const LINK = /\[([^\]]*)\]\(([^)#\s]*)(?:#([^)\s]*))?\)/g;

const checkRef = (file, no, key, num) => {
  if (!(key in DOCS)) {
    fail(file, no, `未知の接頭辞 ${key} §${num}（02a / 02b / 02c のどれかにする）`);
  } else if (!sections[key].has(num)) {
    fail(file, no, `${key} §${num} は存在しない（${DOCS[key]} に該当する節がない）`);
  }
};

for (const file of files) {
  let text;
  try {
    text = readFileSync(resolve(root, file), "utf8");
  } catch {
    continue;
  }
  if (text.includes("\0")) continue;
  const ownKey = keyOfPath(file);
  const isFenceLine = makeFence();
  let inFence = false;
  text.split("\n").forEach((line, i) => {
    const no = i + 1;
    if (isFenceLine(line)) inFence = !inFence;

    // (b) 旧形式
    if (OLD_REF.test(line)) fail(file, no, "旧形式の参照 `02 §x` が残っている（02a / 02b / 02c のどれかにする）");
    if (OLD_FILE.test(line)) fail(file, no, "旧ファイル名 `02-test-architecture` が残っている");

    // (a) § の参照。分割の対応表（| [02a … の行）は他ファイルの節を指すため、接頭辞なしの § の検査から除く
    const isRelationRow = /^\| \[02[abc] /.test(line);
    let prevKey = null; // 直前の § の接頭辞（02 系のとき）
    let prevEnd = -1;
    for (const m of line.matchAll(SECTION_TOKEN)) {
      const [, prefix, num] = m;
      let key = prefix;
      if (!key && prevKey !== null && CONNECTOR.test(line.slice(prevEnd, m.index))) key = prevKey; // 並びは直前の接頭辞を引き継ぐ
      if (key === "02") continue; // 旧形式は (b) で報告済み
      if (key?.startsWith("02")) {
        checkRef(file, no, key, num);
      } else if (!key && ownKey && !inFence && !isRelationRow) {
        // 3 ファイル内の接頭辞なしの § は、そのファイル内の節を指す
        if (!sections[ownKey].has(num)) {
          fail(file, no, `§${num} はこのファイルに存在しない（別ファイルの節なら 02a / 02b / 02c の接頭辞を付ける）`);
        }
      }
      prevKey = key?.startsWith("02") ? key : null;
      prevEnd = m.index + m[0].length;
    }

    // (c) ファイル名の綴り
    for (const m of line.matchAll(DOC_NAME)) {
      if (!DOC_PATHS.has(`docs/testing/${m[0]}`)) fail(file, no, `存在しないファイル名 ${m[0]}`);
    }

    // (c) リンク（ファイル・アンカー・表示テキスト）
    if (file.endsWith(".md")) {
      for (const m of line.matchAll(LINK)) {
        const [, label, target, anchor] = m;
        let targetPath;
        if (target === "") targetPath = file;
        else if (/^[a-z][a-z0-9+.-]*:/i.test(target)) continue;
        else targetPath = relative(root, resolve(root, dirname(file), target)).split("\\").join("/");
        const key = keyOfPath(targetPath);
        if (!key) {
          // 02x-….md という名前なのに、docs/testing/ 直下の 3 ファイルのどれでもない（例: trs/ から ./02a-… と書いた）
          if (/^02[a-z]-.*\.md$/.test(basename(target))) {
            fail(file, no, `リンク先が 02a / 02b / 02c のどれでもない: ${target}（解決先 ${targetPath}）`);
          }
          continue;
        }
        if (!existsSync(resolve(root, targetPath))) {
          fail(file, no, `リンク先が存在しない: ${target}`);
          continue;
        }
        if (anchor === undefined) continue;
        if (!anchors[key].has(anchor)) {
          fail(file, no, `リンクのアンカーが ${basename(targetPath)} に存在しない: #${anchor}`);
          continue;
        }
        // 表示テキストの `02a §x` と、リンク先のファイル・節が一致する
        const anchorSection = anchors[key].get(anchor);
        for (const t of label.matchAll(/(?<![0-9A-Za-z-])`?(02[a-z])`?\s?§(\d+(?:\.\d+)*)/g)) {
          if (t[1] !== key) {
            fail(file, no, `リンクの表示テキスト ${t[1]} §${t[2]} と、リンク先のファイル ${basename(targetPath)} が食い違う`);
          } else if (anchorSection !== t[2]) {
            fail(file, no, `リンクの表示テキスト ${t[1]} §${t[2]} と、アンカー #${anchor}（§${anchorSection ?? "番号なし"}）が食い違う`);
          }
        }
      }
    }
  });
}

if (errors.length > 0) {
  console.error(errors.join("\n"));
  console.error(`\n節番号参照・リンクの検査で ${errors.length} 件の問題があります`);
  process.exit(1);
}
console.log(
  `節番号参照・リンクの検査: 問題なし（${files.length} ファイル、節 ${Object.values(sections)
    .map((s) => s.size)
    .join(" / ")} 件）`,
);
