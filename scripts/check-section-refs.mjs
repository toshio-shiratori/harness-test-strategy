// 02-1 / 02-2 / 02-3 と 03 / 03-1 / 03-2 の節番号参照とリンクを検査する。markdownlint は節番号の誤りを検出できないため、ここで補う。
//   (a) `02-1 §3.3`、`03-1 §2` のような参照の節が、参照先ファイルに実在する。
//       `02-2 §3 / §1.4`、`02-1 §3.2・§3.4` のように接頭辞なしの `§y` が続くときは、直前の接頭辞のファイルの節として検査する。
//       6 ファイル内の接頭辞なしの `§x` は、そのファイル内の節を指す。未知の `02-4`、`03-3` などの接頭辞はエラーにする。
//       03 系の 3 ファイルでは、直前が「テンプレート」の `§x`（並びを含む）を、テンプレート（docs/testing/trs/_template.md）の節として検査する。
//       03 の分割で消えた節（`03 §5.1`、`03 §3.4.3` など）も、実在しない節としてエラーになる。
//   (b) 旧形式が残っていない。分割前の `02 §x`・`02 の §x`・`(02、§x)`・`02-test-architecture`、
//       振り直し前の接頭辞 `02a`・`02b`・`02c`・`03a`・`03b`（ファイル名・文書 ID・`02a §x` などすべて）は、見つけた時点でエラーにする。
//       例外: docs/records/ は、当時の文書を指す履歴なので、旧接頭辞の文字を許し、その節番号を今の文書と照合しない。
//       records でも、リンクのファイルとアンカーの実在（リンク切れ）は検査する。
//   (c) 6 ファイルへのリンクが切れていない。
//       ファイル名が `02-n-….md`・`03-n-….md` なのに docs/testing/ 直下の 6 ファイルのどれでもないもの、アンカーが無いもの、
//       リンクの表示テキストの `02-1 §x`・`03-2 §x` と、リンク先（ファイル・アンカーの節）が食い違うものはエラーにする。
// 検出できないもの: 節は実在するが中身が違う参照（例: 実行境界の説明に Testcontainers の節番号を書く）。レビューで補う。
// `--self-test` は、検査の判定そのものを合成した行で確かめる（lint:refs は自己テストを先に実行する）。
// ローカル (`npm run lint`) と CI で同一のコマンドを使う。
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { basename, dirname, relative, resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const SELF = "scripts/check-section-refs.mjs";
const DOCS = {
  "02-1": "docs/testing/02-1-types-and-pbt.md",
  "02-2": "docs/testing/02-2-execution-and-mutation.md",
  "02-3": "docs/testing/02-3-e2e.md",
  "03": "docs/testing/03-trs-guideline.md",
  "03-1": "docs/testing/03-1-traceability-checks.md",
  "03-2": "docs/testing/03-2-generation-flow.md",
};
const TEMPLATE = "docs/testing/trs/_template.md";
const DOC_PATHS = new Set(Object.values(DOCS));

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

const sections = {}; // 02-1 -> Set("1", "2.1", ...)
const anchors = {}; // 02-1 -> Map(slug -> 節番号 または null)
const templateSections = new Set(); // テンプレートの節番号（"1", "5", "5.2", ...）
for (const [key, path] of [...Object.entries(DOCS), ["template", TEMPLATE]]) {
  sections[key] = key === "template" ? templateSections : new Set();
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
// 任意の md の見出しアンカーの集合（records のリンクの実在検査に使う）
const anchorSetCache = new Map();
const anchorSetOf = (path) => {
  if (!anchorSetCache.has(path)) {
    const set = new Set();
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
      const base = slugOf(h[1]);
      const count = seen.get(base) ?? 0;
      seen.set(base, count + 1);
      set.add(count === 0 ? base : `${base}-${count}`);
    }
    anchorSetCache.set(path, set);
  }
  return anchorSetCache.get(path);
};
const keyOfPath = (p) => Object.keys(DOCS).find((k) => DOCS[k] === p);
const isDoc03 = (key) => key === "03" || /^03-\d+$/.test(key ?? ""); // 03 / 03-1 / 03-2
const isLegacy = (key) => /^(?:02[abc]|03[ab])$/.test(key ?? "");
const familyHint = (key) =>
  key.startsWith("03") ? "03 / 03-1 / 03-2 のどれかにする" : "02-1 / 02-2 / 02-3 のどれかにする";

// 旧形式: `02 §x`、`02 の §x`、空白 2 つ、`(02、§x)` など（02 の直後が英数字・- でなく、短い接続の後に § が来る）
const OLD_REF = /(?<![0-9A-Za-z-])`?02`?[\s、,の]{0,6}§/;
const OLD_FILE = /02-test-architecture/;
// 振り直し前の接頭辞（02a など）。TEST-02a のように - が前にあるものも拾う。日付（2026-02-03）は 02a の形ではないので拾わない
const LEGACY_PREFIX = /(?<![0-9A-Za-z])0(?:2[abc]|3[ab])(?![0-9A-Za-z])/;
// docs/testing/ 直下の 6 ファイルの名前（02-n-….md、03-trs-….md、03-n-….md）。綴りの誤りも拾う。実現例の 02-1.md（スラッグなし）と、日付の `-03-15-…md` は拾わない
const DOC_NAME = /(?<![0-9A-Za-z-])0[23](?:-\d+)?-[A-Za-z][A-Za-z0-9-]*\.md/g;
const TEMPLATE_BEFORE = /(?:テンプレート[（(]?\s?|_template\.md\]\([^)]*\)\s?)$/; // 「テンプレート §5.2」「テンプレート（§4、§5.1）」
// § の参照 1 件。接頭辞（01〜09 に、-n または a-z が続いてもよい）は省略できる
const SECTION_TOKEN = /(?:(?<![0-9A-Za-z-])`?(0\d(?:-\d+|[a-z])?)`?\s?)?§(\d+(?:\.\d+)*)/g;
const CONNECTOR = /^[\s・/、,〜~]{1,4}$/; // 「02-2 §3 / §1.4」「02-1 §3.2・§3.4」のような並び
const LINK = /\[([^\]]*)\]\(([^)#\s]*)(?:#([^)\s]*))?\)/g;
const LABEL_TOKEN = /(?<![0-9A-Za-z-])`?(0[23](?:-\d+)?)`?\s?§(\d+(?:\.\d+)*)/g;

// 1 ファイル（file は root からの相対パス）の本文を検査し、エラーの配列を返す
const checkText = (file, text) => {
  const errors = [];
  const fail = (lineNo, msg) => errors.push(`${file}:${lineNo}: ${msg}`);
  const checkRef = (no, key, num) => {
    if (!(key in DOCS)) {
      fail(no, `未知の接頭辞 ${key} §${num}（${familyHint(key)}）`);
    } else if (!sections[key].has(num)) {
      fail(no, `${key} §${num} は存在しない（${DOCS[key]} に該当する節がない）`);
    }
  };
  const ownKey = keyOfPath(file);
  // docs/records/ は、当時の文書を指す履歴なので、旧接頭辞（02a など）の文字を許す。旧接頭辞の節番号は今の文書と照合しない（下の isLegacy と同じ扱い）。
  // 新しい接頭辞の参照と、リンクのファイル・アンカーの実在は、records でも検査する。
  const isRecord = file.startsWith("docs/records/");
  const isFenceLine = makeFence();
  let inFence = false;
  text.split("\n").forEach((line, i) => {
    const no = i + 1;
    if (isFenceLine(line)) inFence = !inFence;

    // (b) 旧形式
    if (OLD_REF.test(line)) fail(no, "旧形式の参照 `02 §x` が残っている（02-1 / 02-2 / 02-3 のどれかにする）");
    if (OLD_FILE.test(line)) fail(no, "旧ファイル名 `02-test-architecture` が残っている");
    const legacy = line.match(LEGACY_PREFIX);
    if (legacy && !isRecord) {
      fail(no, `旧形式の接頭辞 ${legacy[0]} が残っている（02a→02-1、02b→02-2、02c→02-3、03a→03-1、03b→03-2。節番号も振り直した）`);
    }

    // (a) § の参照。分割の対応表（| [02-1 … の行）は他ファイルの節を指すため、接頭辞なしの § の検査から除く
    const isRelationRow = /^\| \[02-\d /.test(line);
    let prevKey = null; // 直前の § の接頭辞（02・03 系のとき）
    let prevEnd = -1;
    for (const m of line.matchAll(SECTION_TOKEN)) {
      const [, prefix, num] = m;
      let key = prefix;
      if (!key && prevKey !== null && CONNECTOR.test(line.slice(prevEnd, m.index))) key = prevKey; // 並びは直前の接頭辞を引き継ぐ
      // 03 系の中の「テンプレート §x」は、テンプレートの節を指す
      if (!key && ownKey && isDoc03(ownKey) && !inFence && TEMPLATE_BEFORE.test(line.slice(0, m.index))) key = "template";
      if (key === "02" || isLegacy(key)) {
        // 旧形式は (b) で報告済み
      } else if (key === "template") {
        if (!templateSections.has(num)) fail(no, `テンプレートに §${num} は存在しない（${TEMPLATE} に該当する節がない）`);
      } else if (key?.startsWith("02") || key?.startsWith("03")) {
        checkRef(no, key, num);
      } else if (!key && ownKey && !inFence && !isRelationRow) {
        // 6 ファイル内の接頭辞なしの § は、そのファイル内の節を指す
        if (!sections[ownKey].has(num)) {
          const hint = isDoc03(ownKey) ? "03 / 03-1 / 03-2" : "02-1 / 02-2 / 02-3";
          fail(no, `§${num} はこのファイルに存在しない（別ファイルの節なら ${hint} の接頭辞を付ける）`);
        }
      }
      prevKey = key === "template" || isLegacy(key) || key?.startsWith("02") || key?.startsWith("03") ? key : null;
      prevEnd = m.index + m[0].length;
    }

    // (c) ファイル名の綴り
    for (const m of line.matchAll(DOC_NAME)) {
      if (!DOC_PATHS.has(`docs/testing/${m[0]}`)) fail(no, `存在しないファイル名 ${m[0]}`);
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
          // 02-n-….md・03-n-….md という名前なのに、docs/testing/ 直下の 6 ファイルのどれでもない（例: trs/ から ./02-1-… と書いた）
          if (/^0[23](?:-\d+)?-[A-Za-z].*\.md$/.test(basename(target))) {
            fail(no, `リンク先が 02-1 / 02-2 / 02-3 / 03 / 03-1 / 03-2 のどれでもない: ${target}（解決先 ${targetPath}）`);
          }
          // records は旧名（02b-….md、02b.md）へのリンクも含むため、ほかの md へのリンクも、ファイルとアンカーの実在を検査する
          if (isRecord && targetPath.endsWith(".md")) {
            if (!existsSync(resolve(root, targetPath))) {
              fail(no, `リンク先が存在しない: ${target}`);
            } else if (anchor !== undefined && !anchorSetOf(targetPath).has(anchor)) {
              fail(no, `リンクのアンカーが ${basename(targetPath)} に存在しない: #${anchor}`);
            }
          }
          continue;
        }
        if (!existsSync(resolve(root, targetPath))) {
          fail(no, `リンク先が存在しない: ${target}`);
          continue;
        }
        if (anchor === undefined) continue;
        if (!anchors[key].has(anchor)) {
          fail(no, `リンクのアンカーが ${basename(targetPath)} に存在しない: #${anchor}`);
          continue;
        }
        // 表示テキストの `02-1 §x` と、リンク先のファイル・節が一致する
        const anchorSection = anchors[key].get(anchor);
        for (const t of label.matchAll(LABEL_TOKEN)) {
          if (t[1] !== key) {
            fail(no, `リンクの表示テキスト ${t[1]} §${t[2]} と、リンク先のファイル ${basename(targetPath)} が食い違う`);
          } else if (anchorSection !== t[2]) {
            fail(no, `リンクの表示テキスト ${t[1]} §${t[2]} と、アンカー #${anchor}（§${anchorSection ?? "番号なし"}）が食い違う`);
          }
        }
      }
    }
  });
  return errors;
};

// 自己テスト: 合成した本文で、判定が期待どおりに出ることを確かめる
const selfTest = () => {
  const failures = [];
  const anchorOf = (key, num) => [...anchors[key]].find(([, n]) => n === num)?.[0];
  const link02_2 = (label, num) => `[${label}](docs/testing/02-2-execution-and-mutation.md#${anchorOf("02-2", num)})`;
  // [名前, 検査するファイル, 本文, 期待するエラー数, 期待するエラーに含まれる語]
  const cases = [
    ["正しい参照", "README.md", "詳細は 02-1 §3.3.1 と 02-2 §1.2 を参照する。", 0],
    ["03 系の正しい参照", "README.md", "03 §3.4 と 03-1 §2、03-2 §4 を参照する。", 0],
    ["並びは直前の接頭辞を引き継ぐ", "README.md", "02-1 §3.2・§3.4 を参照する。", 0],
    ["並びの 2 件目が存在しない", "README.md", "02-2 §1 / §9 を参照する。", 1, "02-2 §9"],
    ["実在しない節", "README.md", "02-2 §9 を参照する。", 1, "存在しない"],
    ["未知の接頭辞 02-4", "README.md", "02-4 §1 を参照する。", 1, "未知の接頭辞"],
    ["未知の接頭辞 03-3", "README.md", "03-3 §1 を参照する。", 1, "未知の接頭辞"],
    ["旧形式 02a §x", "README.md", "02a §1 を参照する。", 1, "旧形式の接頭辞 02a"],
    ["旧形式 02b §x（バッククォート）", "README.md", "`02b` §1 を参照する。", 1, "旧形式の接頭辞 02b"],
    ["旧形式 03a §x", "README.md", "03a §1 を参照する。", 1, "旧形式の接頭辞 03a"],
    ["旧形式 03b のファイル名", "README.md", "[03b](./03b-generation-flow.md) を参照する。", 1, "旧形式の接頭辞 03b"],
    ["旧形式の文書 ID", "README.md", "文書ID TEST-02a。", 1, "旧形式の接頭辞 02a"],
    ["旧形式 02c.md（実現例のファイル名）", "README.md", "examples/kotlin-spring/docs/02c.md を参照する。", 1, "旧形式の接頭辞 02c"],
    ["records では旧接頭辞が通る", "docs/records/x.md", "当時の 02b §4.1 と 03a §2、`02c-e2e.md` を指す。", 0],
    ["records でも旧形式 02 §x（分割前）は失敗する", "docs/records/x.md", "02 §3 を参照する。", 1, "旧形式の参照"],
    ["records でも新しい接頭辞の存在しない節は失敗する", "docs/records/x.md", "02-2 §9 を参照する。", 1, "存在しない"],
    ["records でもリンク切れ（旧ファイル名）は失敗する", "docs/records/x.md", "[02b §4.1](../testing/02b-execution-and-mutation.md#41-x)", 1, "存在しない"],
    ["records でもリンクのアンカー切れは失敗する", "docs/records/x.md", "[02b](../testing/02-2-execution-and-mutation.md#no-such-anchor)", 1, "アンカーが"],
    ["records の正しいリンクは通る（表示テキストは当時の名前のまま）", "docs/records/x.md", `[02b §4.1](../testing/02-2-execution-and-mutation.md#${anchorOf("02-2", "2.1")})`, 0],
    ["旧形式 02 §x（分割前）", "README.md", "02 §3 を参照する。", 1, "旧形式の参照"],
    ["日付は誤検出しない", "README.md", "2026-02-03 に決めた。2026-03-15-notes.md と 02-03 も同様。", 0],
    ["日付と節の参照", "README.md", "2026-02-03 に 02-1 §1 を見た。", 0],
    ["実現例の 02-1.md は存在しない docs/testing の名前と見なさない", "README.md", "examples/kotlin-spring/docs/02-1.md を参照する。", 0],
    ["存在しないスラッグ付きファイル名", "README.md", "02-1-no-such-file.md を参照する。", 1, "存在しないファイル名"],
    ["03 本体のファイル名の綴りの誤り", "README.md", "03-trs-guidline.md を参照する。", 1, "存在しないファイル名"],
    ["03 本体の正しいファイル名", "README.md", "03-trs-guideline.md を参照する。", 0],
    ["trs/ から 03 本体へ誤ったパスでリンクする", "docs/testing/trs/_template.md", "[03](./03-trs-guideline.md)", 1, "のどれでもない"],
    ["02-2 の中の接頭辞なしの実在しない節", "docs/testing/02-2-execution-and-mutation.md", "§9 を参照する。", 1, "このファイルに存在しない"],
    ["02-2 の中の接頭辞なしの実在する節", "docs/testing/02-2-execution-and-mutation.md", "§3 と §1.2 を参照する。", 0],
    ["02-2 の中の別の文書への接頭辞付きの参照", "docs/testing/02-2-execution-and-mutation.md", "03 §3.4 を参照する。", 0],
    ["リンクのファイルとアンカーと表示テキストが一致", "README.md", link02_2("02-2 §1.2", "1.2"), 0],
    ["リンクの表示テキストの節がアンカーと食い違う", "README.md", link02_2("02-2 §2.1", "1.2"), 1, "食い違う"],
    ["リンクの表示テキストのファイルがリンク先と食い違う", "README.md", link02_2("02-1 §2.2", "1.2"), 1, "食い違う"],
    ["リンクのアンカーが存在しない", "README.md", "[02-2](docs/testing/02-2-execution-and-mutation.md#no-such-anchor)", 1, "アンカーが"],
    ["リンク先が 6 ファイルのどれでもない", "docs/testing/trs/_template.md", "[02-1](./02-1-types-and-pbt.md)", 1, "のどれでもない"],
    ["コードブロックの中の接頭辞なしの § は検査しない", "docs/testing/02-2-execution-and-mutation.md", "```text\n§99\n```", 0],
  ];
  for (const [name, file, text, expected, word] of cases) {
    const errs = checkText(file, text);
    if (errs.length !== expected || (word && !errs.some((e) => e.includes(word)))) {
      failures.push(`${name}: エラー ${errs.length} 件（期待 ${expected} 件${word ? `、「${word}」を含む` : ""}）\n    ${errs.join("\n    ")}`);
    }
  }
  if (failures.length > 0) {
    console.error(`lint:refs の自己テストに失敗しました（${failures.length} 件）\n${failures.join("\n")}`);
    process.exit(1);
  }
  console.log(`lint:refs の自己テスト: ${cases.length} 件 OK`);
};

if (process.argv.includes("--self-test")) {
  selfTest();
  process.exit(0);
}

// 追跡ファイル + 未追跡（.gitignore 対象外）。worktree 内の複製は対象外。
const files = execFileSync("git", ["ls-files", "--cached", "--others", "--exclude-standard", "-z"], {
  cwd: root,
  encoding: "utf8",
})
  .split("\0")
  .filter((f) => f && f !== SELF && !f.startsWith(".claude/worktrees/") && existsSync(resolve(root, f)));

const errors = [];
for (const file of files) {
  let text;
  try {
    text = readFileSync(resolve(root, file), "utf8");
  } catch {
    continue;
  }
  if (text.includes("\0")) continue;
  errors.push(...checkText(file, text));
}

if (errors.length > 0) {
  console.error(errors.join("\n"));
  console.error(`\n節番号参照・リンクの検査で ${errors.length} 件の問題があります`);
  process.exit(1);
}
console.log(
  `節番号参照・リンクの検査: 問題なし（${files.length} ファイル、節 ${Object.entries(sections)
    .filter(([k]) => k !== "template")
    .map(([, s]) => s.size)
    .join(" / ")} 件）`,
);
