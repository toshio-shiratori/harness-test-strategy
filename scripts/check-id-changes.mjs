// PR の base と head を比べて、生成物のファイルで「既存の ID」が変わった、または消えたものを列挙し、
// PR 本文の `## 既存の ID の変化` の節に、ID ごとの理由があることを検査する(03 §5.1 の例外、実現例は examples/kotlin-spring/docs/03.md の 5.8)。
//
// 列挙するもの(生成物のファイルの変化だけ。新しい ID の行を足す変化は、人がレビューしないので列挙しない):
//   - 既存の ID に結び付く記述が、消えた、または中身が変わったもの
//       US の表の行、UC の節、AC の表の行と、その AC の Gherkin のシナリオ(Examples の行を含む)、C-xx の行
//   - 既存の AC が @pending になったもの(最初から @pending の新しい AC は、新しい ID の行なので列挙しない)
//   - ID に結び付かない Gherkin(Feature、Background、タグの無いシナリオ)の変化。固定の名前 `Gherkin共通` で列挙する
//   - 過去に使った ID が、新しい行として戻ったもの(使い回し)
//   - base にあった生成物のファイルが、head に無いもの(削除・名前の変更)。`生成物ファイル(<名前>)` として列挙する
// 「過去に使った ID」は、base までの git の履歴(TRS の置き場と、移す前の置き場)と、base の Open Issues の反映先にある
// `AC-xx-yy 削除` の記録から集める。履歴を使う理由は、削除の記録が AC にしか無く、記録なしで消した AC も使い回しを検出するため。
// 履歴が浅い(shallow)と使い回しを検出できないので、失敗にする。CI のチェックアウトは fetch-depth: 0 にする。
//
// 使い方:
//   BODY="<PR 本文>" node scripts/check-id-changes.mjs --base <rev> --head <rev>   (変化がなければ本文は見ない)
//   node scripts/check-id-changes.mjs --self-test
// 比べる起点は、base と head の merge-base である(base の先端と比べると、分岐の後に base へ入った変更が、この PR の変化に見える)。
// ローカル(`npm run lint`)と CI で、自己テストを同じコマンドで実行する。
import { execFileSync, spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const SCRIPT = resolve(import.meta.filename);

// 生成物のファイルの置き場。導入先で変えるときは、ここだけ直す
const GENERATED_DIR = "examples/kotlin-spring/docs/trs/generated";
// 一度でも使った ID を履歴から集める置き場。TRS の置き場と、移す前の置き場。`_` で始まるファイル(テンプレート)は、ID の例なので除く
const HISTORY_PATHS = ["examples/kotlin-spring/docs/trs", "docs/testing/trs", ":(exclude,glob)**/_*.md"];
// 削除の記録(Open Issues の反映先の `AC-xx-yy 削除`)がある TRS ファイルの置き場
const TRS_DIR = "examples/kotlin-spring/docs/trs";
const SECTION = "既存の ID の変化";
const GHERKIN_KEY = "Gherkin共通";

const ID_FORM = /^(?:US-\d+|AC-\d{2}-\d{2}|C-\d+)$/;
const KIND_LABEL = {
  removed: "消えた",
  changed: "中身が変わった",
  pending: "@pending になった",
  reused: "使い回し",
  filegone: "ファイルが無くなった、または名前が変わった",
  filenew: "base に生成物のファイルが無く、head に現れた(置き場の移動)",
};

// ---- 生成物のファイルから、ID ごとの記述を取り出す --------------------------------------------------------------

const norm = (line) => {
  const t = line.trim().replace(/\s+/g, " ");
  // 表の行は、セルの前後の空白の違いを変化にしない
  return t.startsWith("|") ? `| ${t.replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim()).join(" | ")} |` : t;
};
const isTag = (l) => /^\s*@/.test(l);
const isBlankish = (l) => l.trim() === "" || /^\s*#/.test(l);
const isScenarioStart = (l) => /^\s*(?:Feature|Background|Rule|Scenario Outline|Scenario Template|Scenario|Example)\s*:/.test(l);

/** Gherkin の本文を、タグ行から始まる(またはキーワード行から始まる)まとまりに分ける */
function gherkinGroups(lines) {
  const groups = [{ head: [], body: [] }];
  let tagged = false; // 直前のタグ行が、いまのまとまりの先頭である(次のキーワード行で、新しいまとまりを作らない)
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (isTag(line)) {
      let j = i;
      const tags = [];
      while (j < lines.length && isTag(lines[j])) tags.push(lines[j++]);
      let k = j;
      while (k < lines.length && isBlankish(lines[k])) k++;
      if (k < lines.length && isScenarioStart(lines[k])) {
        groups.push({ head: tags, body: [] });
        tagged = true;
      } else {
        groups.at(-1).body.push(...tags); // Examples の直前のタグなど。いまのまとまりの続き
      }
      i = j;
    } else {
      if (isScenarioStart(line) && !tagged) groups.push({ head: [], body: [] });
      if (!isBlankish(line)) tagged = false;
      groups.at(-1).body.push(line);
      i++;
    }
  }
  return groups;
}

// Kotlin の検査(RequirementTraceCheck.scan と TrsTables.read)と同じ規則で、行・表・見出しを読む。
// 検査が「ある」とみなす記述の集合と、ここで比べる記述の集合を一致させるため。広く読むと、検査から外れた記述が「変化なし」で素通りする。
const FENCE_OPEN = /^(`{3,}|~{3,})(.*)$/;
const HEADING = /^(#{1,6})\s+(.*)$/;
const cellsOf = (line) => line.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim());
const isSeparator = (cells) => cells.every((c) => c !== "" && /^[-:]+$/.test(c));

/**
 * RequirementTraceCheck.scan の移植。コードブロックと HTML コメントの中は、見出しにも表にもしない。
 * HTML コメントは、`<!--` を含む行から、`-->` を含む行までを、行ごとコメントとみなす(Kotlin と同じ)。
 * 各行に、見出しの深さ・表の通し番号と行の位置・セルと、コメントか・コードブロックの中身か(言語つき)を付ける。
 */
export function scan(text) {
  let fenceChar = " ";
  let fenceLen = 0;
  let fenceLang = "";
  let inComment = false;
  let table = -1;
  let nextTable = 0;
  let row = 0;
  return text.split(/\r\n|\r|\n/).map((raw, i) => {
    const t = raw.trimStart();
    const fenced = fenceLen > 0;
    const mark = inComment ? null : FENCE_OPEN.exec(t);
    const closes = fenced && mark !== null && mark[1][0] === fenceChar && mark[1].length >= fenceLen && mark[2].trim() === "";
    const fence = fenced ? closes : mark !== null;
    const lang = fenceLang;
    if (fence) {
      fenceChar = mark[1][0];
      fenceLen = fenced ? 0 : mark[1].length;
      if (!fenced) fenceLang = mark[2].trim().split(/\s+/)[0].toLowerCase();
    }
    const comment = !fence && !fenced && (inComment || raw.includes("<!--"));
    if (comment) {
      const open = raw.lastIndexOf("<!--");
      const close = raw.lastIndexOf("-->");
      if (open >= 0 || close >= 0) inComment = open > close;
    }
    const plain = fence || fenced || comment;
    const heading = plain ? null : HEADING.exec(t);
    const isRow = !plain && heading === null && t.startsWith("|");
    if (isRow && table < 0) {
      table = nextTable++;
      row = 0;
    } else if (isRow) row++;
    else table = -1;
    return {
      no: i + 1,
      raw,
      level: heading ? heading[1].length : 0,
      title: heading ? heading[2] : "",
      table,
      row,
      cells: isRow ? cellsOf(t) : [],
      comment,
      content: fenced && !fence, // コードブロックの中身
      lang, // 中身のコードブロックの言語(開いた行の情報文字列の先頭)
      fenceStart: fence && !fenced,
      fenceEnd: fence && fenced,
    };
  });
}

// 表の見出し。Kotlin の検査が表として読む見出しと同じ(AcTable.header、ExpectationTable.header、US の表の出典の列)
const AC_HEADER = ["AC ID", "関連 UC", "テストレベル", "検証層", "優先度", "検証する振る舞い", "根拠"];
const EXPECTATION_HEADER = ["制約ID", "条件", "期待結果", "根拠（要件 ID / 設計 ID / OI）"];
const SOURCE_NAME = "出典（要件 ID）";
// `@AC-xx-yy` の後ろに `@pending:` がある行。Kotlin の TestNameCheck.pendingTag と同じ正規表現を、本文全体に当てる
const PENDING_TAG = /@(AC-\d\d-\d\d)\b[^\n]*@pending:/g;

/**
 * 生成物のファイルの本文から、ID ごとの記述(連結した文字列の配列)と、@pending の AC を取り出す。
 * 空白の違いと行の並びは、変化にしない(行は、ID ごとに、現れた順で連結する)。
 * - US: 見出しに `出典（要件 ID）` がある表の行。AC: 見出しが 7 列に完全一致する表の行。C-xx: 見出しが 4 列に完全一致する 5.2 の表の行。
 *   どれも、列数が見出しと同じで、先頭列が ID の形の行だけを読む。HTML コメント・コードブロックの中と、表の外の行は読まない
 * - UC: `UC-<数字>:` で始まる見出しから、次の同じ深さ以上の見出しまで(HTML コメントの行は除く)
 * - AC: 上の表の行に加えて、コードブロック(gherkin)の `@AC-xx-yy` のタグが付いたシナリオ
 * - GHERKIN_KEY: ID に結び付かない Gherkin
 * - @pending: 本文全体から、Kotlin と同じ正規表現で取る(コードブロックの外や HTML コメントの中も含む)
 */
export function extractRecords(text) {
  const parts = new Map();
  const pending = new Set([...text.matchAll(PENDING_TAG)].map((m) => m[1]));
  const add = (key, value) => {
    if (!parts.has(key)) parts.set(key, []);
    parts.get(key).push(value);
  };
  const flush = (lines) => {
    for (const group of gherkinGroups(lines)) {
      const body = [...group.head, ...group.body].map(norm).filter(Boolean);
      if (body.length === 0) continue;
      const ids = group.head.flatMap((l) => [...l.matchAll(/@(AC-\d{2}-\d{2})\b/g)].map((m) => m[1]));
      if (ids.length === 0) add(GHERKIN_KEY, body.join("\n"));
      for (const id of ids) add(id, `scenario: ${body.join("\n")}`);
    }
  };
  const lines = scan(text);

  // 表の行
  const tables = new Map();
  for (const l of lines) {
    if (l.table < 0) continue;
    if (!tables.has(l.table)) tables.set(l.table, { header: l.cells, rows: [] });
    else tables.get(l.table).rows.push(l);
  }
  const sameHeader = (a, b) => a.length === b.length && a.every((c, i) => c === b[i]);
  for (const { header, rows } of tables.values()) {
    const idForm = sameHeader(header, AC_HEADER)
      ? /^AC-\d\d-\d\d$/
      : sameHeader(header, EXPECTATION_HEADER)
        ? /^C-\d\d$/
        : header.includes(SOURCE_NAME)
          ? /^US-\d+$/
          : null;
    if (!idForm) continue;
    for (const l of rows) {
      if (!isSeparator(l.cells) && l.cells.length === header.length && idForm.test(l.cells[0])) add(l.cells[0], norm(l.raw));
    }
  }

  // UC の節
  lines.forEach((l, i) => {
    const m = l.level > 0 ? /^(UC-\d+)\s*[:：]/.exec(l.title) : null;
    if (!m) return;
    add(m[1], norm(l.raw));
    for (const s of lines.slice(i + 1)) {
      if (s.level !== 0 && s.level <= l.level) break;
      if (!s.comment && norm(s.raw)) add(m[1], norm(s.raw));
    }
  });

  // Gherkin(言語が gherkin のコードブロックの中身)
  let gherkin = null;
  for (const l of lines) {
    if (l.fenceStart) gherkin = [];
    else if (l.content && l.lang === "gherkin") gherkin.push(l.raw);
    else if (l.fenceEnd) {
      if (l.lang === "gherkin") flush(gherkin);
      gherkin = null;
    }
  }
  if (gherkin?.length) flush(gherkin); // 閉じていないコードブロックも、最後まで読む
  return { parts, pending };
}

/** 複数のファイルの記録を 1 つにまとめる(ID は、ファイルをまたいで重ねて数える) */
export function mergeRecords(list) {
  const parts = new Map();
  const pending = new Set();
  for (const r of list) {
    for (const [key, values] of r.parts) parts.set(key, [...(parts.get(key) ?? []), ...values]);
    for (const id of r.pending) pending.add(id);
  }
  return { parts, pending };
}

// ---- 比べて、列挙する --------------------------------------------------------------------------------------------

/** base にあって head に無い生成物のファイル(削除・改名)。ファイルごとに、理由の欄が要る */
export function enumerateFiles(baseFiles, headFiles) {
  const found = new Map();
  const name = (f) => `生成物ファイル(${f.split("/").at(-1)})`;
  for (const f of baseFiles) if (!headFiles.includes(f)) found.set(name(f), new Set(["filegone"]));
  // base に 1 つも無く head にある(置き場を移した)ときも、検査の対象が変わるので列挙する
  if (baseFiles.length === 0) for (const f of headFiles) found.set(name(f), new Set(["filenew"]));
  return found;
}

const joined = (record, key) => (record.parts.get(key) ?? []).join("\n");

/**
 * base と head の記録を比べ、理由が要る ID と種類の対応(Map<ID, Set<種類>>)を返す。
 * [used] は、過去に使った ID。base に無く、used にある ID が head に現れたら、使い回しとする。
 */
export function enumerate(base, head, used) {
  const found = new Map();
  const mark = (id, kind) => found.set(id, (found.get(id) ?? new Set()).add(kind));
  for (const id of base.parts.keys()) {
    if (id === GHERKIN_KEY) continue;
    if (!head.parts.has(id)) mark(id, "removed");
    else if (joined(base, id) !== joined(head, id)) mark(id, "changed");
  }
  // ID に結び付かない Gherkin は、足されても変化とする(Background を足すと、すべてのシナリオの前提が変わるため)
  if (joined(base, GHERKIN_KEY) !== joined(head, GHERKIN_KEY)) mark(GHERKIN_KEY, head.parts.has(GHERKIN_KEY) ? "changed" : "removed");
  // 新しい AC の行は、人がレビューしない(03 §5.1)。最初から @pending の新しい AC は、未解決の Blocking な OI に縛られる(03 §3.4.2 の ② ③)ので列挙しない
  for (const id of head.pending) if (base.parts.has(id) && !base.pending.has(id)) mark(id, "pending");
  for (const id of head.parts.keys()) if (id !== GHERKIN_KEY && !base.parts.has(id) && used.has(id)) mark(id, "reused");
  return new Map([...found].sort(([a], [b]) => a.localeCompare(b)));
}

const DEF_ROW = /^\|\s*(US-\d+|AC-\d{2}-\d{2}|C-\d+)\s*\|/;
const DEF_UC = /^#{1,6}\s+(UC-\d+)\s*[:：]/;

/** `git log -p -U0` の出力から、ID を定義した行(表の先頭列、UC の見出し、Gherkin のタグ)の ID を集める。追加の行も削除の行も数える */
export function definedIdsFromDiff(diffText) {
  const ids = new Set();
  for (const raw of diffText.split("\n")) {
    if (!/^[+-]/.test(raw) || raw.startsWith("+++") || raw.startsWith("---")) continue;
    const line = raw.slice(1).trim();
    const m = DEF_ROW.exec(line) ?? DEF_UC.exec(line);
    if (m) ids.add(m[1]);
    if (line.startsWith("@")) for (const t of line.matchAll(/@(AC-\d{2}-\d{2})\b/g)) ids.add(t[1]);
  }
  return ids;
}

/** Open Issues の表の行にある `AC-xx-yy 削除` の記録から、削除した AC を集める */
export function deletedIdsFromTrs(text) {
  const ids = new Set();
  for (const line of text.split(/\r?\n/)) {
    if (!line.trimStart().startsWith("|")) continue;
    for (const m of line.matchAll(/(AC-\d{2}-\d{2})\s*削除/g)) ids.add(m[1]);
  }
  return ids;
}

// ---- PR 本文の理由欄 ---------------------------------------------------------------------------------------------

const PLACEHOLDER = /^<[^>]*>$/;

/**
 * PR 本文の `## 既存の ID の変化` の節から、`- <ID>[, <ID>…]: <理由>` の行を読む。HTML コメントの中は読まない。
 * コロンは半角も全角も可。理由が空、または `<理由>` のような雛形のままの行は、理由なしとして返す。
 * 戻り値は Map<ID, 理由>(理由なしは空文字列)。
 */
export function parseReasons(body) {
  const text = (body ?? "").replace(/<!--[\s\S]*?-->/g, "").replace(/\r\n/g, "\n");
  const reasons = new Map();
  let inSection = false;
  for (const line of text.split("\n")) {
    const heading = /^##\s+(.*?)\s*$/.exec(line);
    if (heading) {
      inSection = heading[1] === SECTION;
      continue;
    }
    if (!inSection) continue;
    const m = /^\s*[-*]\s+([^:：]+?)\s*[:：]\s*(.*?)\s*$/.exec(line);
    if (!m) continue;
    const ids = m[1].split(/\s*[,、，]\s*/).map((s) => s.trim());
    if (!ids.every((id) => ID_FORM.test(id) || /^UC-\d+$/.test(id) || id === GHERKIN_KEY || /^生成物ファイル\(.+\)$/.test(id))) continue;
    const reason = PLACEHOLDER.test(m[2]) ? "" : m[2];
    for (const id of ids) reasons.set(id, reason);
  }
  return reasons;
}

/** 理由が無い ID と、その理由を返す。空なら合格 */
export function missingReasons(found, reasons) {
  return [...found].filter(([id]) => !reasons.get(id)).map(([id, kinds]) => `${id}(${[...kinds].map((k) => KIND_LABEL[k]).join("、")})`);
}

// ---- git から読む -----------------------------------------------------------------------------------------------

function git(repo, args) {
  return execFileSync("git", args, { cwd: repo, encoding: "utf8", maxBuffer: 1 << 30, stdio: ["ignore", "pipe", "pipe"] });
}

function filesAt(repo, rev, dir) {
  return git(repo, ["ls-tree", "-r", "--name-only", rev, "--", dir]).split("\n").filter((f) => f.endsWith(".md"));
}

function recordsAt(repo, rev) {
  const files = filesAt(repo, rev, GENERATED_DIR);
  return { files, records: mergeRecords(files.map((f) => extractRecords(git(repo, ["show", `${rev}:${f}`])))) };
}

/** 失敗の説明(空なら合格)と、標準出力に出す行を返す */
export function run(repo, baseArg, headArg, body) {
  if (git(repo, ["rev-parse", "--is-shallow-repository"]).trim() === "true") {
    return { errors: ["履歴が浅い(shallow)ので、過去に使った ID を調べられない。actions/checkout に fetch-depth: 0 を付ける"], lines: [] };
  }
  const base = git(repo, ["merge-base", baseArg, headArg]).trim();
  const head = git(repo, ["rev-parse", "--verify", `${headArg}^{commit}`]).trim();
  const b = recordsAt(repo, base);
  const h = recordsAt(repo, head);
  if (b.files.length === 0 && h.files.length === 0) {
    return { errors: [`生成物のファイルが、base にも head にも無い(${GENERATED_DIR})。置き場を変えたときは、このスクリプトの GENERATED_DIR を直す`], lines: [] };
  }
  if (h.files.length > 0 && ![...h.records.parts.keys()].some((k) => /^AC-/.test(k))) {
    return { errors: [`head の生成物のファイルから、AC の行を 1 行も読めない(${GENERATED_DIR})。読み取りの空振りは、検査の素通りになるので失敗にする`], lines: [] };
  }
  const used = definedIdsFromDiff(git(repo, ["log", "-p", "-U0", "--no-color", "--no-ext-diff", "--format=", base, "--", ...HISTORY_PATHS]));
  for (const f of filesAt(repo, base, TRS_DIR)) for (const id of deletedIdsFromTrs(git(repo, ["show", `${base}:${f}`]))) used.add(id);
  const found = new Map([...enumerate(b.records, h.records, used), ...enumerateFiles(b.files, h.files)]);
  if (found.size === 0) return { errors: [], lines: ["既存の ID の変化は無い。"] };
  const reasons = parseReasons(body);
  const missing = missingReasons(found, reasons);
  const lines = [...found].map(([id, kinds]) => `${id}: ${[...kinds].map((k) => KIND_LABEL[k]).join("、")}${reasons.get(id) ? ` / 理由: ${reasons.get(id)}` : ""}`);
  if (missing.length === 0) return { errors: [], lines };
  const template = [...found.keys()].map((id) => `- ${id}: <理由>`).join("\n");
  return {
    errors: [
      `既存の ID の変化 ${found.size} 件のうち、PR 本文に理由が無いもの: ${missing.join(" / ")}`,
      `PR 本文に「## ${SECTION}」の節を置き、ID ごとに「- <ID>: <理由>」の行を書く:\n## ${SECTION}\n${template}`,
    ],
    lines,
  };
}

function parseArgs(argv) {
  const value = (name) => {
    const i = argv.indexOf(name);
    return i >= 0 ? argv[i + 1] : undefined;
  };
  return { base: value("--base"), head: value("--head"), repo: value("--repo") ?? root };
}

function main() {
  const { base, head, repo } = parseArgs(process.argv);
  if (!base || !head) {
    console.error("使い方: BODY=<PR 本文> node scripts/check-id-changes.mjs --base <rev> --head <rev> / --self-test");
    process.exit(2);
  }
  const { errors, lines } = run(repo, base, head, process.env.BODY ?? "");
  for (const l of lines) console.log(l);
  for (const e of errors) console.error(`::error::${e}`);
  process.exit(errors.length === 0 ? 0 : 1);
}

// ---- 自己テスト --------------------------------------------------------------------------------------------------

function selfTest() {
  let failures = 0;
  const check = (name, ok, detail = "") => {
    if (!ok) {
      failures++;
      console.error(`NG: ${name}${detail ? ` — ${detail}` : ""}`);
    } else {
      console.log(`OK: ${name}`);
    }
  };
  const showFound = (found) => JSON.stringify([...found].map(([id, kinds]) => [id, [...kinds].sort()]));
  const GRAVE = "```";

  // 生成物のファイルの見本。置換で、行を変える・消す・足す
  const generated = ({ extraAc = "", extraGherkin = "", background = "  Background:\n    Given 前提\n" } = {}) => `# 生成物

### 1. ユーザーストーリー(US)

| ID | ストーリー | 出典（要件 ID） |
|---|---|---|
| US-01 | 登録したい | REQ-01 |

#### UC-01: 登録する

主フロー 1。
出典(要件 ID): REQ-01

#### UC-02: 完了にする

主フロー 2。

#### AC の表

| AC ID | 関連 UC | テストレベル | 検証層 | 優先度 | 検証する振る舞い | 根拠 |
|---|---|---|---|---|---|---|
| AC-09-01 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |
| AC-09-02 | UC-02 | 単体 | 層1 | Must | 振る舞い | C-11 |
| AC-09-03 | UC-01 | 単体 | 層1 | Must | 流れ | C-10 |
| AC-09-04 | UC-01 | 単体 | 層1 | Must | 別の流れ | C-10 |
${extraAc}
${GRAVE}gherkin
Feature: 機能
${background}
  @AC-09-03 @layer:2
  Scenario Outline: 流れ
    Given <x>
    Examples:
      | x |
      | 1 |

  @AC-09-04 @layer:2
  Scenario: 別の流れ
    When 操作する
${extraGherkin}${GRAVE}

#### 5.2 組合せ別の期待結果

| 制約ID | 条件 | 期待結果 | 根拠（要件 ID / 設計 ID / OI） |
|---|---|---|---|
| C-10 | 条件 1 | 結果 1 | REQ-01 |
| C-11 | 条件 2 | 結果 2 | REQ-01 |
`;
  const rec = (text) => extractRecords(text);
  const noUsed = new Set();
  const diff = (a, b, used = noUsed) => enumerate(rec(a), rec(b), used);
  const base = generated();

  // 変化なし・列挙しない変化
  check("変化が無ければ列挙しない", diff(base, base).size === 0);
  check("新しい AC の行を足す変化は列挙しない", diff(base, generated({ extraAc: "| AC-09-05 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |" })).size === 0);
  check(
    "新しいシナリオを足す変化は列挙しない",
    diff(base, generated({ extraGherkin: "\n  @AC-09-05 @layer:2\n  Scenario: 新しい流れ\n    When 操作する\n" })).size === 0,
  );
  check("空白だけの違いは列挙しない", diff(base, base.replace("| AC-09-01 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |", "|  AC-09-01 |  UC-01 |単体|層1|  Must |振る舞い|   C-10 |")).size === 0);
  check(
    "表の行の並べ替えは列挙しない",
    diff(base, base.replace("| AC-09-01 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |\n| AC-09-02 | UC-02 | 単体 | 層1 | Must | 振る舞い | C-11 |", "| AC-09-02 | UC-02 | 単体 | 層1 | Must | 振る舞い | C-11 |\n| AC-09-01 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |")).size === 0,
  );

  // ① 消えた ID
  const removedAc = diff(base, base.replace("| AC-09-02 | UC-02 | 単体 | 層1 | Must | 振る舞い | C-11 |\n", ""));
  check("消えた ID: AC の表の行が消えたら列挙する", showFound(removedAc) === '[["AC-09-02",["removed"]]]', showFound(removedAc));
  check("消えた ID: US の行が消えたら列挙する", showFound(diff(base, base.replace("| US-01 | 登録したい | REQ-01 |\n", ""))) === '[["US-01",["removed"]]]');
  check("消えた ID: UC の節が消えたら列挙する", showFound(diff(base, base.replace(/#### UC-02: 完了にする\n\n主フロー 2。\n\n/, ""))) === '[["UC-02",["removed"]]]');
  check("消えた ID: C-xx の行が消えたら列挙する", showFound(diff(base, base.replace("| C-11 | 条件 2 | 結果 2 | REQ-01 |\n", ""))) === '[["C-11",["removed"]]]');
  const noScenario = diff(base, base.replace(/  @AC-09-04 @layer:2\n {2}Scenario: 別の流れ\n {4}When 操作する\n/, ""));
  check("変わった ID: 表の行とシナリオを持つ AC で、シナリオだけが消えたら、その AC を「中身が変わった」として列挙する", showFound(noScenario) === '[["AC-09-04",["changed"]]]', showFound(noScenario));

  // ② 変わった ID
  check("変わった ID: AC の行の中身が変われば列挙する", showFound(diff(base, base.replace("| AC-09-01 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |", "| AC-09-01 | UC-01 | 単体 | 層2 | Must | 振る舞い | C-10 |"))) === '[["AC-09-01",["changed"]]]');
  check("変わった ID: シナリオの Examples の行が変われば、その AC を列挙する", showFound(diff(base, base.replace("      | 1 |", "      | 2 |"))) === '[["AC-09-03",["changed"]]]');
  check("変わった ID: UC の節の中身が変われば列挙する", showFound(diff(base, base.replace("主フロー 2。", "主フロー 2 を直した。"))) === '[["UC-02",["changed"]]]');
  check("変わった ID: US の行が変われば列挙する", showFound(diff(base, base.replace("登録したい", "登録したい(直した)"))) === '[["US-01",["changed"]]]');
  check("変わった ID: C-xx の行の条件が狭まれば列挙する", showFound(diff(base, base.replace("| C-10 | 条件 1 |", "| C-10 | 条件 1 のうち一部 |"))) === '[["C-10",["changed"]]]');
  check("変わった ID: 既存の AC にシナリオが足されたら列挙する", showFound(diff(base, generated({ extraGherkin: "\n  @AC-09-01 @layer:1\n  Scenario: 足した流れ\n    When 操作する\n" }))) === '[["AC-09-01",["changed"]]]');
  check("変わった ID: コードブロックの中の表は、ID の行に数えない", diff(base, `${base}\n${GRAVE}text\n| AC-09-01 | 別の値 |\n${GRAVE}\n`).size === 0);

  // Kotlin の検査が「ある」とみなす範囲との一致(AC を検査から外しても、変化なしで素通りしない)
  const rowAc1 = "| AC-09-01 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |";
  const rowC10 = "| C-10 | 条件 1 | 結果 1 | REQ-01 |";
  const hiddenAc = diff(base, base.replace(rowAc1, `<!--\n${rowAc1}\n-->`));
  check("範囲(a): AC の行を HTML コメントに入れたら、消えた ID として列挙する(表が割れるので、後ろの行も消えた ID になる)", hiddenAc.get("AC-09-01")?.has("removed") === true, showFound(hiddenAc));
  const hiddenC = diff(base, base.replace(rowC10, `<!-- ${rowC10} -->`));
  check("範囲(a): C-xx の行を HTML コメントに入れたら、消えた ID として列挙する", hiddenC.get("C-10")?.has("removed") === true, showFound(hiddenC));
  const oneRowTable = diff(base, base.replace(`${rowAc1}\n`, "").replace("#### 5.2", `${rowAc1}\n\n#### 5.2`));
  check("範囲(b): AC の行を表の外に出して 1 行の表にしたら、消えた ID として列挙する", showFound(oneRowTable) === '[["AC-09-01",["removed"]]]', showFound(oneRowTable));
  const wrongHeader = diff(base, base.replace("| AC ID | 関連 UC |", "| AC | 関連 UC |"));
  check("範囲(b): AC の表の見出しが 7 列と違えば、表の AC を消えた ID として列挙する", wrongHeader.get("AC-09-01")?.has("removed") === true && wrongHeader.get("AC-09-02")?.has("removed") === true, showFound(wrongHeader));
  const outsidePending = diff(base, `${base}\n@AC-09-01 @layer:1 @pending:OI-001\n`);
  check("範囲(c): コードブロックの外に書いた @pending も、@pending になった ID として列挙する", showFound(outsidePending) === '[["AC-09-01",["pending"]]]', showFound(outsidePending));
  const commentPending = diff(base, `${base}\n<!-- @AC-09-02 @pending:OI-001 -->\n`);
  check("範囲(c): HTML コメントの中の @pending も、Kotlin の検査と同じく列挙する", showFound(commentPending) === '[["AC-09-02",["pending"]]]', showFound(commentPending));
  const hiddenUc = diff(base, base.replace("主フロー 2。", "<!-- 主フロー 2。 -->"));
  check("範囲: UC の節の行を HTML コメントに入れたら、変わった ID として列挙する", showFound(hiddenUc) === '[["UC-02",["changed"]]]', showFound(hiddenUc));
  check("範囲: 生成物のファイルが base と head で同じなら、ファイルの変化は無い", enumerateFiles(["a/x.md"], ["a/x.md"]).size === 0);
  check("ファイルの変化: head に無くなったファイル(削除・名前の変更)を列挙する", showFound(enumerateFiles(["a/x.md", "a/y.md"], ["a/x.md", "a/z.md"])) === '[["生成物ファイル(y.md)",["filegone"]]]');
  check("ファイルの変化: head が 0 件なら、base のすべてのファイルを列挙する", enumerateFiles(["a/x.md", "a/y.md"], []).size === 2);
  check("ファイルの変化: 新しいファイルが増えても列挙しない", enumerateFiles(["a/x.md"], ["a/x.md", "a/new.md"]).size === 0);
  check("ファイルの変化: base に 1 つも無く、head にある(置き場を移した)ときは列挙する", showFound(enumerateFiles([], ["b/x.md"])) === '[["生成物ファイル(x.md)",["filenew"]]]');
  check("読む範囲: 見出しの先頭が ID でない US の表も、出典の列があれば読む(Kotlin の検査と同じ)", diff(base, base.replace("| US-01 | 登録したい | REQ-01 |", "| US-01 | 登録したい(直した) | REQ-01 |").replace("| ID | ストーリー |", "| US ID | ストーリー |")).get("US-01")?.has("changed") === true);
  check("読む範囲: 見出しの先頭が ID でない US の表の行が消えたら、消えた ID として列挙する", showFound(diff(base.replace("| ID | ストーリー |", "| US ID | ストーリー |"), base.replace("| ID | ストーリー |", "| US ID | ストーリー |").replace("| US-01 | 登録したい | REQ-01 |\n", ""))) === '[["US-01",["removed"]]]');
  check(
    "理由欄: 生成物ファイルの行を読む",
    missingReasons(enumerateFiles(["a/x.md"], []), parseReasons(`## ${SECTION}\n\n- 生成物ファイル(x.md): 名前を変えた\n`)).length === 0,
  );

  // ID に結び付かない Gherkin
  check("ID に結び付かない Gherkin: Background が変われば列挙する", showFound(diff(base, base.replace("Given 前提", "Given 別の前提"))) === `[["${GHERKIN_KEY}",["changed"]]]`);
  check("ID に結び付かない Gherkin: Background が足されても列挙する", showFound(diff(generated({ background: "" }), base)) === `[["${GHERKIN_KEY}",["changed"]]]`);
  check("ID に結び付かない Gherkin: Feature の行が変われば列挙する", showFound(diff(base, base.replace("Feature: 機能", "Feature: 別の機能"))) === `[["${GHERKIN_KEY}",["changed"]]]`);

  // ③ @pending になった ID
  const pend = diff(base, base.replace("@AC-09-03 @layer:2", "@AC-09-03 @layer:2 @pending:OI-001"));
  check("@pending になった ID: 既存の AC に @pending が付いたら列挙する", showFound(pend) === '[["AC-09-03",["changed","pending"]]]', showFound(pend));
  const newPending = diff(base, generated({ extraAc: "| AC-09-05 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |", extraGherkin: "\n  @AC-09-05 @layer:1 @pending:OI-002\n  Scenario: 未決\n    When 操作する\n" }));
  check("@pending になった ID: 新しい AC が最初から @pending なら列挙しない(新しい ID の行は人がレビューしない。③ で未解決の Blocking な OI に縛られる)", newPending.size === 0, showFound(newPending));
  check("@pending になった ID: 元から @pending の AC は、@pending を外す向きで変わるだけ", showFound(diff(base.replace("@AC-09-03 @layer:2", "@AC-09-03 @layer:2 @pending:OI-001"), base)) === '[["AC-09-03",["changed"]]]');

  // ④ 使い回しの ID
  const reusedHead = generated({ extraAc: "| AC-09-07 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |" });
  check("使い回しの ID: 過去に使った ID が新しい行として戻れば列挙する", showFound(diff(base, reusedHead, new Set(["AC-09-07"]))) === '[["AC-09-07",["reused"]]]');
  check("使い回しの ID: 過去に使っていない ID は列挙しない", diff(base, reusedHead, new Set(["AC-09-08"])).size === 0);
  check("使い回しの ID: base にある ID は、過去に使っていても使い回しではない", diff(base, base, new Set(["AC-09-01"])).size === 0);
  const history = ["diff --git a/x b/x", "--- a/x", "+++ b/x", "@@ -1,0 +1,6 @@", "+| AC-09-10 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |", "-| C-77 | 条件 | 結果 |", "+#### UC-12: 見出し", "+@AC-09-11 @layer:1 @pending:OI-001", "+本文で AC-09-12 に触れる", "+| 因子 | AC-09-13 | 値 |"].join("\n");
  const fromDiff = definedIdsFromDiff(history);
  check(
    "履歴の差分から、ID を定義した行(表の先頭列・UC の見出し・タグ。追加と削除の両方)だけを集める",
    ["AC-09-10", "C-77", "UC-12", "AC-09-11"].every((id) => fromDiff.has(id)) && !fromDiff.has("AC-09-12") && !fromDiff.has("AC-09-13"),
    [...fromDiff].join(","),
  );
  const records = ["| ID | 区分 | 関連 | 状態 | 反映先 |", "| OI-001 | Blocking | AC-09-20 | Resolved | AC-09-21 削除, AC-09-22 削除 |", "削除の説明の地の文 AC-09-23 削除"].join("\n");
  const deleted = deletedIdsFromTrs(records);
  check("削除の記録(Open Issues の表の行の `AC-xx-yy 削除`)から、削除した AC を集める。地の文は読まない", deleted.has("AC-09-21") && deleted.has("AC-09-22") && !deleted.has("AC-09-23") && !deleted.has("AC-09-20"), [...deleted].join(","));

  // 理由欄
  const found = new Map([["AC-09-01", new Set(["removed"])], ["C-10", new Set(["changed"])]]);
  const section = (lines) => `## 概要\n\n本文\n\n## ${SECTION}\n\n${lines}\n\n## 検証\n\n- [ ] 確認\n`;
  check("理由欄: ID ごとに理由があれば合格する", missingReasons(found, parseReasons(section("- AC-09-01: 重複を整理した\n- C-10: 条件を直した"))).length === 0);
  check("理由欄: 節が無ければ、すべての ID が理由なしになる", missingReasons(found, parseReasons("## 概要\n\n本文")).length === 2);
  check("理由欄: 1 つの ID の理由が無ければ、その ID だけが理由なしになる", missingReasons(found, parseReasons(section("- AC-09-01: 重複を整理した"))).join() === "C-10(中身が変わった)");
  check("理由欄: 理由が空なら理由なしになる", missingReasons(found, parseReasons(section("- AC-09-01:\n- C-10: x"))).join() === "AC-09-01(消えた)");
  check("理由欄: 理由が雛形(<理由>)のままなら理由なしになる", missingReasons(found, parseReasons(section("- AC-09-01: <理由>\n- C-10: x"))).join() === "AC-09-01(消えた)");
  check("理由欄: HTML コメントの中の記入例は読まない", missingReasons(found, parseReasons(`## ${SECTION}\n<!--\n- AC-09-01: 例\n- C-10: 例\n-->\n`)).length === 2);
  check("理由欄: 全角のコロンでも読む", missingReasons(found, parseReasons(section("- AC-09-01:重複を整理した\n- C-10:条件を直した".replaceAll(":", "：")))).length === 0);
  check("理由欄: コンマで区切った複数の ID に、同じ理由を書ける", missingReasons(found, parseReasons(section("- AC-09-01, C-10: まとめて整理した"))).length === 0);
  check("理由欄: ほかの節に書いた行は読まない", missingReasons(found, parseReasons(`## 概要\n\n- AC-09-01: 理由\n- C-10: 理由\n\n## ${SECTION}\n\nなし\n`)).length === 2);
  check("理由欄: ID の形でない行は、理由として読まない", missingReasons(found, parseReasons(section("- 理由: AC-09-01 を整理した\n- C-10: 条件を直した"))).join() === "AC-09-01(消えた)");

  // git を通した検査(履歴の取り出し・merge-base・shallow・削除の記録)
  const tmp = mkdtempSync(join(tmpdir(), "check-id-changes-"));
  try {
    const repo = join(tmp, "repo");
    mkdirSync(repo);
    const g = (...args) => execFileSync("git", ["-c", "user.name=t", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false", ...args], { cwd: repo, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
    const put = (path, text) => {
      mkdirSync(dirname(join(repo, path)), { recursive: true });
      writeFileSync(join(repo, path), text);
    };
    const file = `${GENERATED_DIR}/F-001-x.md`;
    const trsFile = `${TRS_DIR}/F-001-x.md`;
    g("init", "-q");
    g("symbolic-ref", "HEAD", "refs/heads/main");
    const gen1 = generated({ extraAc: "| AC-09-05 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |" });
    put(file, gen1);
    put(trsFile, "# TRS\n\n| ID | 区分 | 関連 | 状態 | 反映先 |\n|---|---|---|---|---|\n| OI-001 | Blocking | AC-09-30 | Resolved | AC-09-30 削除 |\n");
    g("add", "-A");
    g("commit", "-q", "-m", "first");
    put(file, base); // AC-09-05 を消す(履歴にだけ残る)
    g("add", "-A");
    g("commit", "-q", "-m", "delete AC-09-05");
    const mainTip = g("rev-parse", "HEAD");
    g("checkout", "-q", "-b", "pr");
    const exec = (headText, body) => {
      if (headText !== null) {
        put(file, headText);
        g("add", "-A");
        g("commit", "-q", "--allow-empty", "-m", "change");
      }
      const r = spawnSync(process.execPath, [SCRIPT, "--repo", repo, "--base", mainTip, "--head", "pr"], { encoding: "utf8", env: { ...process.env, BODY: body } });
      g("reset", "-q", "--hard", mainTip);
      return r;
    };
    const ok = exec(base, "");
    check("git: 変化が無い PR は成功する", ok.status === 0, ok.stderr);
    const reuse = exec(generated({ extraAc: "| AC-09-05 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-99 |" }), "");
    check("git: 履歴にだけ残る(いまの base に無い)ID が戻ると、理由なしで失敗する", reuse.status === 1 && /AC-09-05\(使い回し/.test(reuse.stderr), reuse.stderr);
    const reuseOk = exec(generated({ extraAc: "| AC-09-05 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-99 |" }), `## ${SECTION}\n\n- AC-09-05: 旧 ID の再利用ではなく、同じ内容を戻した\n`);
    check("git: 理由があれば、使い回しでも成功する", reuseOk.status === 0, reuseOk.stderr);
    const recordOnly = exec(generated({ extraAc: "| AC-09-30 | UC-01 | 単体 | 層1 | Must | 振る舞い | C-10 |" }), "");
    check("git: 削除の記録だけにある AC(履歴に無い)が戻っても、理由なしで失敗する", recordOnly.status === 1 && /AC-09-30\(使い回し/.test(recordOnly.stderr), recordOnly.stderr);
    const removed = exec(base.replace("| AC-09-02 | UC-02 | 単体 | 層1 | Must | 振る舞い | C-11 |\n", ""), "");
    check("git: 消えた AC は、理由なしで失敗する", removed.status === 1 && /AC-09-02\(消えた\)/.test(removed.stderr), removed.stderr);
    const changed = exec(base.replace("| 層1 |", "| 層2 |"), "");
    check("git: 変わった AC は、理由なしで失敗する", changed.status === 1 && /AC-09-01\(中身が変わった\)/.test(changed.stderr), changed.stderr);
    const pending = exec(base.replace("@AC-09-03 @layer:2", "@AC-09-03 @layer:2 @pending:OI-001"), `## ${SECTION}\n\n- AC-09-03: 決定待ち\n`);
    check("git: @pending になった AC は、理由があれば成功する", pending.status === 0, pending.stderr);
    const pendingNo = exec(base.replace("@AC-09-03 @layer:2", "@AC-09-03 @layer:2 @pending:OI-001"), "");
    check("git: @pending になった AC は、理由なしで失敗する", pendingNo.status === 1 && /@pending になった/.test(pendingNo.stderr), pendingNo.stderr);
    // base の先端と比べず、merge-base と比べる(分岐の後に main へ入った変更を、この PR の変化にしない)
    g("checkout", "-q", "main");
    put(file, base.replace("| AC-09-02 | UC-02 | 単体 | 層1 | Must | 振る舞い | C-11 |\n", ""));
    g("add", "-A");
    g("commit", "-q", "-m", "main moves on");
    const movedMain = g("rev-parse", "HEAD");
    g("checkout", "-q", "pr");
    const viaMergeBase = spawnSync(process.execPath, [SCRIPT, "--repo", repo, "--base", movedMain, "--head", "pr"], { encoding: "utf8", env: { ...process.env, BODY: "" } });
    check("git: base が先へ進んでいても、merge-base と比べるので、main 側の変更を列挙しない", viaMergeBase.status === 0, viaMergeBase.stderr);
    // shallow
    const shallow = join(tmp, "shallow");
    execFileSync("git", ["clone", "-q", "--depth", "1", `file://${repo}`, shallow], { stdio: "ignore" });
    const sr = spawnSync(process.execPath, [SCRIPT, "--repo", shallow, "--base", "HEAD", "--head", "HEAD"], { encoding: "utf8", env: { ...process.env, BODY: "" } });
    check("git: 履歴が浅い(shallow)と、失敗する", sr.status === 1 && /shallow/.test(sr.stderr), sr.stderr);
    // 読み取りの空振り
    const empty = join(tmp, "empty");
    mkdirSync(empty);
    const eg = (...args) => execFileSync("git", ["-c", "user.name=t", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false", ...args], { cwd: empty, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
    eg("init", "-q");
    writeFileSync(join(empty, "a.txt"), "x");
    eg("add", "-A");
    eg("commit", "-q", "-m", "a");
    const er = spawnSync(process.execPath, [SCRIPT, "--repo", empty, "--base", "HEAD", "--head", "HEAD"], { encoding: "utf8", env: { ...process.env, BODY: "" } });
    check("git: 生成物のファイルが base にも head にも無いと、失敗する(置き場の変更で素通りしない)", er.status === 1 && /生成物のファイルが/.test(er.stderr), er.stderr);
    put(file, "# 生成物\n\n表なし\n");
    g("add", "-A");
    g("commit", "-q", "-m", "no ac");
    const noAc = spawnSync(process.execPath, [SCRIPT, "--repo", repo, "--base", movedMain, "--head", "pr"], { encoding: "utf8", env: { ...process.env, BODY: "" } });
    check("git: head の生成物のファイルから AC の行を 1 行も読めないと、失敗する", noAc.status === 1 && /AC の行を 1 行も読めない/.test(noAc.stderr), noAc.stderr);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }

  if (failures > 0) {
    console.error(`\n自己テストに ${failures} 件失敗した。`);
    process.exit(1);
  }
  console.log("\n自己テスト: すべて通った。");
}

if (process.argv.includes("--self-test")) selfTest();
else if (resolve(process.argv[1] ?? "") === SCRIPT) main(); // 直接実行のときだけ(読み込んだときは何もしない)
