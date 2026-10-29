// docs/testing/ が言語中立で自己完結していること（そのまま別のリポジトリへコピーできること）を検査する。
// 対象: docs/testing/ 配下の全 .md（trs/_template.md と templates/ を含む）。
//   (1) docs/testing/ の外へのリンクがない。相対パスで外を指すものに加え、外部 URL（https: / http: / mailto: など）も違反にする。
//       同一ファイル内のアンカーは対象外。docs/testing/ の中を指すリンクは、リンク先のファイルが実在することも確かめる。
//       インラインリンク・自動リンク・参照形式の定義・本文中の URL を、ファイル全体から取り出して検査する
//       （複数行のリンクテキスト、入れ子の角括弧、次の行にある参照定義、引用や箇条書きの中も含む）。
//   (2) コードブロックの言語が許可リストにある。プログラミング言語のコード例は置かない。
//       言語を指定できない字下げコードブロック（リスト外の 4 個以上の字下げ）も違反にする。
//   (3) このリポジトリ固有の語（パス・サンプルの ID・検査クラス名・ワークフロー名・PR/Issue 番号）がない。
// 検出できないもの: 一覧にない固有の語、言語固有の記述を地の文で書いたもの。レビュー（spec-reviewer）で補う。
// ツール名（Kotest、PIT など）は、括弧の例示として書くのを許す。ここでは禁止しない。
// `--self-test` は、各規則が違反で失敗し、許される書き方で通ることを、スクリプト内の反例で確かめる。
// ローカル (`npm run lint`) と CI で同一のコマンドを使う。
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const TARGET_DIR = "docs/testing";

// (2) 許可するコードブロックの言語
const ALLOWED_LANGS = new Set(["gherkin", "mermaid", "markdown", "text"]);

// (3) このリポジトリ固有の語。`pattern` は行に対して検査する（大文字小文字は区別する）
const FORBIDDEN_TERMS = [
  { pattern: /(?<![\w-])src\//, label: "src/（このリポジトリのソースのパス）" },
  { pattern: /gradlew/, label: "gradlew（このリポジトリのビルドコマンド）" },
  { pattern: /(?<![\w-])examples\//, label: "examples/（このリポジトリの実装例のパス）" },
  { pattern: /docs\/records/, label: "docs/records（このリポジトリの記録のパス）" },
  { pattern: /(?<![A-Za-z])ORD-0\d/, label: "ORD-0（サンプル TRS の ID）" },
  { pattern: /(?<![A-Za-z])PAY-0\d/, label: "PAY-0（サンプル TRS の ID）" },
  { pattern: /TraceabilityTest/, label: "TraceabilityTest（このリポジトリの検査クラス）" },
  { pattern: /ExecutionBoundaryTest/, label: "ExecutionBoundaryTest（このリポジトリの検査クラス）" },
  { pattern: /TestProtectionTest/, label: "TestProtectionTest（このリポジトリの検査クラス）" },
  { pattern: /DomainArchitectureTest/, label: "DomainArchitectureTest（このリポジトリの検査クラス）" },
  { pattern: /OrderFormE2eTest/, label: "OrderFormE2eTest（このリポジトリのテストクラス）" },
  { pattern: /OrderControllerHttpTest/, label: "OrderControllerHttpTest（このリポジトリのテストクラス）" },
  { pattern: /PaymentState/, label: "PaymentState（サンプルの型名）" },
  { pattern: /kotlin-samples\.yml/, label: "kotlin-samples.yml（このリポジトリのワークフロー）" },
  { pattern: /nightly-mutation\.yml/, label: "nightly-mutation.yml（このリポジトリのワークフロー）" },
  { pattern: /pr-bug-fix-check\.yml/, label: "pr-bug-fix-check.yml（このリポジトリのワークフロー）" },
  { pattern: /check-bug-fix-pr\.sh/, label: "check-bug-fix-pr.sh（このリポジトリのスクリプト）" },
  { pattern: /(?<![\w-])\.github\//, label: ".github/（このリポジトリのパス）" },
  { pattern: /(?<![\w-])scripts\//, label: "scripts/（このリポジトリのスクリプトのパス）" },
  { pattern: /harness-test-strategy|toshio-shiratori/, label: "このリポジトリの名前・所有者（URL を含む）" },
  { pattern: /(?<![A-Za-z])GH-\d+/, label: "GH-数字（このリポジトリの Issue 番号）" },
];
// このリポジトリの PR / Issue 番号（`#123`）。除くもの: リンクのアンカー（`](#anchor)`）、見出しの `#`、
// 文字参照（`&#123;`、Mermaid の `#35;`）、16 進の色（Mermaid の `fill:#333`）
const ISSUE_REF = /(?<![\w&#/:])#\d+(?![\w;-])/;

// 行頭の引用記号（`>`、入れ子を含む）を取り除く。引用の中でもフェンスやリンクは有効なため
const stripQuote = (line) => line.replace(/^(?: {0,3}>[ \t]?)+/, "");
// フェンスの開始・終了は、字下げが 0〜3 個の空白のものだけ（4 個以上は字下げコードブロックで、フェンスではない）
const FENCE = /^ {0,3}(`{3,}|~{3,})[ \t]*([^\s`]*)/;
// 字下げが 4 個以上（またはタブ）で、フェンスのように見える行
const INDENTED_FENCE = /^(?: {4,}|\t)[ \t]*(`{3,}|~{3,})[ \t]*([^\s`]*)/;
const INDENTED = /^(?: {4,}|\t)/;
const LIST_MARKER = /^ {0,3}(?:[-*+]|\d+[.)])[ \t]/;

// リンクの取り出し: インラインリンクの宛先、自動リンク、本文中の URL、参照形式の定義（宛先が次の行でもよい）
const INLINE_DEST = /\]\(\s*(?:<([^>\n]*)>|([^\s)]*))/g;
const AUTOLINK = /<([a-z][a-z0-9+.-]*:[^\s<>]*)>/gi;
const BARE_URL = /https?:\/\/[^\s)<>\]]+/gi;
const DEFINITION = /^[ \t]*(?:(?:[-*+]|\d+[.)])[ \t]+)*\[[^\]\n]+\]:[ \t]*\n?[ \t]*<?([^\s>]+)/gm;

/**
 * 1 つの Markdown を検査し、違反を { line, rule, message } の配列で返す。
 * @param {string} file リポジトリルートからの相対パス（例: docs/testing/trs/_template.md）
 * @param {string} text 本文
 * @param {(path: string) => boolean} [exists] リンク先の実在を確かめる関数。省略するとリンク先の実在は確かめない
 */
export const checkMarkdown = (file, text, exists) => {
  const violations = [];
  const seen = new Set();
  const add = (line, rule, message) => {
    const key = `${line}|${rule}|${message}`;
    if (seen.has(key)) return;
    seen.add(key);
    violations.push({ line, rule, message });
  };
  const langList = [...ALLOWED_LANGS].join(" / ");

  // リンク検査用の本文。コードブロック（フェンス・字下げ）の行は空にし、インラインコードは取り除く。行番号を保つため行数は変えない
  const scrubbed = [];
  let marker = null; // 開いているフェンスの記号
  let inList = false;
  let prevBlank = true;
  let inIndentedCode = false;

  text.split("\n").forEach((raw, i) => {
    const no = i + 1;
    const line = stripQuote(raw);
    const blank = line.trim() === "";

    // (3) 固有の語は、コードブロックの中・インラインコードの中も含め、元の行に対して検査する
    for (const { pattern, label } of FORBIDDEN_TERMS) {
      if (pattern.test(raw)) add(no, "repo-term", `このリポジトリ固有の語: ${label}`);
    }
    // 見出しの `#`、リンク先（アンカーを含む）は番号の参照ではない
    const forIssue = raw.replace(/^\s{0,3}#{1,6}(?=\s)/, "").replace(/\]\([^)]*\)/g, "]()");
    if (ISSUE_REF.test(forIssue)) add(no, "repo-term", "このリポジトリの PR / Issue 番号（#数字）");

    // フェンスの内側: 内容はリンク検査から外し、閉じる行を探す
    const f = line.match(FENCE);
    if (marker !== null) {
      scrubbed.push("");
      if (f && f[1][0] === marker[0] && f[1].length >= marker.length && f[2] === "") marker = null;
      prevBlank = blank;
      return;
    }
    // フェンスの開始（字下げ 0〜3 個）
    if (f) {
      marker = f[1];
      const lang = f[2];
      if (!ALLOWED_LANGS.has(lang)) {
        add(
          no,
          "code-lang",
          lang === "" ? `コードブロックに言語がない（許可: ${langList}）` : `許可されていないコードブロックの言語 ${lang}（許可: ${langList}）`,
        );
      }
      scrubbed.push("");
      inIndentedCode = false;
      prevBlank = blank;
      return;
    }

    // リストの中かどうか（リストの中の字下げは字下げコードブロックではなく、項目の続き）
    if (!blank) {
      if (LIST_MARKER.test(line)) inList = true;
      else if (!/^[ \t]/.test(line) && prevBlank) inList = false;
    }

    if (!blank && INDENTED.test(line)) {
      if (inList) {
        // リストの中の字下げ 4 個以上のフェンス風の行は、フェンスの開閉とは見なさない（内容を隠さない）。許可外の言語の宣言だけ報告する
        const fenceLike = line.match(INDENTED_FENCE);
        if (fenceLike && fenceLike[2] !== "" && !ALLOWED_LANGS.has(fenceLike[2])) {
          add(no, "code-lang", `許可されていないコードブロックの言語 ${fenceLike[2]}（許可: ${langList}）`);
        }
      } else if (inIndentedCode || prevBlank) {
        // リスト外の字下げコードブロック: 言語を指定できないため違反。先頭行で 1 回だけ報告し、内容はリンク検査から外す
        if (!inIndentedCode) {
          add(no, "code-lang", `字下げによるコードブロック（言語を指定できない。許可: ${langList} のフェンスを使う）`);
        }
        inIndentedCode = true;
        scrubbed.push("");
        prevBlank = false;
        return;
      }
    } else if (!blank) {
      inIndentedCode = false;
    }
    scrubbed.push(line.replace(/`+[^`]*`+/g, "``"));
    prevBlank = blank;
  });

  // (1) リンクは本文全体から取り出す（複数行のリンクテキスト、入れ子の角括弧、次の行にある参照定義を含む）
  const body = scrubbed.join("\n");
  const lineOf = (index) => body.slice(0, index).split("\n").length;
  const targets = []; // { line, target }
  for (const m of body.matchAll(INLINE_DEST)) targets.push({ line: lineOf(m.index), target: m[1] ?? m[2] });
  for (const m of body.matchAll(AUTOLINK)) targets.push({ line: lineOf(m.index), target: m[1] });
  for (const m of body.matchAll(BARE_URL)) targets.push({ line: lineOf(m.index), target: m[0] });
  for (const m of body.matchAll(DEFINITION)) targets.push({ line: lineOf(m.index), target: m[1] });

  for (const { line, target } of targets) {
    if (target === "" || target.startsWith("#")) continue;
    if (/^[a-z][a-z0-9+.-]*:/i.test(target)) {
      add(line, "link-outside", `docs/testing/ の外へのリンク（外部 URL を含む）: ${target}`);
      continue;
    }
    const pathPart = target.split("#")[0].split("?")[0];
    const resolved = target.startsWith("/") ? target.slice(1) : join(dirname(file), pathPart).split("\\").join("/");
    if (target.startsWith("/") || resolved === ".." || resolved.startsWith("../") || !(resolved + "/").startsWith(TARGET_DIR + "/")) {
      add(line, "link-outside", `${TARGET_DIR}/ の外を指す相対リンク: ${target}`);
    } else if (exists && pathPart !== "" && !exists(resolved)) {
      add(line, "link-missing", `リンク先が存在しない: ${target}`);
    }
  }
  return violations.sort((a, b) => a.line - b.line);
};

const listMarkdown = (dir) =>
  readdirSync(resolve(root, dir), { withFileTypes: true }).flatMap((e) => {
    const p = `${dir}/${e.name}`;
    if (e.isDirectory()) return e.name === "node_modules" ? [] : listMarkdown(p);
    return e.name.endsWith(".md") ? [p] : [];
  });

// --self-test: 反例（違反を含む Markdown）で各規則が失敗し、許される書き方では通ることを確かめる
const F = "docs/testing/a.md";
const term = (name, text) => ({ name: `違反: ${name}`, file: F, text, expect: ["repo-term"] });
const SELF_TESTS = [
  // 通ること
  { name: "許可: 本文と見出し", file: F, text: "# 1. 見出し\n\n## 2.1 節\n\n本文。\n", expect: [] },
  {
    name: "許可: ツール名の括弧の例示（言語名・ライブラリ名を含む）",
    file: F,
    text: "PBT のライブラリ(Kotest、jqwik、fast-check)と、ミューテーションのツール(PIT、Stryker)を使う。\n",
    expect: [],
  },
  {
    name: "許可: 許可リストの言語のコードブロック",
    file: F,
    text: "```gherkin\nGiven x\n```\n\n```mermaid\nflowchart LR\n  A --> B\n```\n\n```markdown\n- [ ] x\n```\n\n```text\nfoo\n```\n",
    expect: [],
  },
  {
    name: "許可: 同一ファイルのアンカー・docs/testing/ 内の相対リンク",
    file: "docs/testing/trs/_template.md",
    text: "[a](#section) [c](../01-test-policy.md#foo) [d](../templates/bug-report.md)\n",
    expect: [],
  },
  { name: "許可: 見出しとアンカーの #、文字参照", file: F, text: "# 見出し\n\n## 1.2 節\n\n[x](#12-節) &#35; と &#123;\n", expect: [] },
  {
    name: "許可: Mermaid の色（fill:#333）と文字参照（#35;）",
    file: F,
    text: '```mermaid\nflowchart LR\n  A["x#35;y"]\n  style A fill:#333,stroke:#000\n```\n',
    expect: [],
  },
  { name: "許可: 一般の語（src を含む別の語、example/）", file: F, text: "resource/ と example/ と ORD や PAY の接頭辞。\n", expect: [] },
  {
    name: "許可: コードブロックの中の相対リンクの書式（検査しない）",
    file: F,
    text: "```markdown\n[x](../../outside.md)\n```\n",
    expect: [],
  },
  { name: "許可: 参照形式のリンク定義が内を指す", file: F, text: "[ref]: ./01-test-policy.md#foo\n", expect: [] },
  { name: "許可: 引用の中の本文と内側のリンク", file: F, text: "> 注意\n> [x](./01-test-policy.md)\n", expect: [] },
  { name: "許可: 箇条書きの続きの字下げ（字下げコードブロックではない）", file: F, text: "- 項目\n\n    続きの段落。\n\n    - 入れ子の項目\n", expect: [] },
  { name: "許可: 段落の続きの字下げ（空行なし）", file: F, text: "段落の1行目\n    続き\n", expect: [] },
  { name: "許可: 箇条書きの中の 3 個字下げのフェンス", file: F, text: "1. 手順\n\n   ```text\n   foo\n   ```\n", expect: [] },
  { name: "許可: 箇条書きの中の 4 個字下げの許可言語のフェンス風の行", file: F, text: "- 項目\n\n    ```text\n    foo\n    ```\n", expect: [] },
  // 失敗すること: リンク
  { name: "違反: docs/testing/ の外へのリンク（../）", file: F, text: "[x](../../README.md)\n", expect: ["link-outside"] },
  { name: "違反: trs/ から docs/testing/ の外へのリンク", file: "docs/testing/trs/t.md", text: "[x](../../../README.md#a)\n", expect: ["link-outside"] },
  { name: "違反: 絶対パスのリンク", file: F, text: "[x](/docs/records/01.md)\n", expect: ["link-outside", "repo-term"] },
  { name: "違反: 実在しないリンク先", file: F, text: "[x](./missing.md)\n", expect: ["link-missing"], exists: () => false },
  { name: "違反: 外部 URL（https）へのリンク", file: F, text: "[x](https://example.com/x#1)\n", expect: ["link-outside"] },
  { name: "違反: 外部 URL（http）へのリンク", file: F, text: "[x](http://example.com)\n", expect: ["link-outside"] },
  { name: "違反: mailto へのリンク", file: F, text: "[e](mailto:a@example.com)\n", expect: ["link-outside"] },
  { name: "違反: 自動リンク", file: F, text: "<https://example.com>\n", expect: ["link-outside"] },
  { name: "違反: 本文中の URL", file: F, text: "詳しくは https://example.com を参照。\n", expect: ["link-outside"] },
  { name: "違反: 参照形式のリンク定義が外を指す", file: F, text: "[ref]: ../../README.md\n", expect: ["link-outside"] },
  { name: "違反: 参照形式のリンク定義が外部 URL", file: F, text: "[ref]: https://example.com\n", expect: ["link-outside"] },
  { name: "違反: 参照定義の宛先が次の行", file: F, text: "[r]:\n  ../../README.md\n", expect: ["link-outside"] },
  { name: "違反: 箇条書きの中の参照定義", file: F, text: "- [r]: ../../README.md\n", expect: ["link-outside"] },
  { name: "違反: 入れ子の角括弧のリンクテキスト", file: F, text: "[a [b] c](../../README.md)\n", expect: ["link-outside"] },
  { name: "違反: 複数行のリンクテキスト", file: F, text: "[x\ny](../../README.md)\n", expect: ["link-outside"] },
  { name: "違反: 引用の中のリンク", file: F, text: "> [x](../../README.md)\n", expect: ["link-outside"] },
  // 失敗すること: コードブロック
  { name: "違反: kotlin のコードブロック", file: F, text: "```kotlin\nval x = 1\n```\n", expect: ["code-lang"] },
  { name: "違反: bash のコードブロック", file: F, text: "```bash\nls\n```\n", expect: ["code-lang"] },
  { name: "違反: 言語のないコードブロック", file: F, text: "```\nfoo\n```\n", expect: ["code-lang"] },
  { name: "違反: ~~~ のコードブロック", file: F, text: "~~~yaml\na: 1\n~~~\n", expect: ["code-lang"] },
  { name: "違反: 引用の中の kotlin のコードブロック", file: F, text: "> ```kotlin\n> val x = 1\n> ```\n", expect: ["code-lang"] },
  {
    name: "違反: 4 個字下げのフェンス風の行の後のリンク（フェンスとして扱って隠さない）",
    file: F,
    text: "段落\n\n    ```text\n[x](../../README.md)\n",
    expect: ["code-lang", "link-outside"],
  },
  { name: "違反: リスト外の字下げコードブロック", file: F, text: "段落\n\n    val x = 1\n", expect: ["code-lang"] },
  { name: "違反: 字下げコードブロックの先頭がフェンス風の kotlin", file: F, text: "段落\n\n    ```kotlin\n    val x = 1\n    ```\n", expect: ["code-lang"] },
  { name: "違反: 箇条書きの中の 4 個字下げの kotlin のフェンス", file: F, text: "- 項目\n\n    ```kotlin\n    val x = 1\n    ```\n", expect: ["code-lang"] },
  // 失敗すること: このリポジトリ固有の語
  term("src/", "ソースは `src/main` にある。\n"),
  term("./src/（先頭が . や / のパス）", "./src/test を参照。\n"),
  term("途中に src/ を含むパス", "app/src/main を参照。\n"),
  { name: "違反: このリポジトリの URL", file: F, text: "ここは https://github.com/toshio-shiratori/harness-test-strategy です。\n", expect: ["repo-term", "link-outside"] },
  term("gradlew", "`./gradlew test` で実行する。\n"),
  term("examples/", "examples/kotlin-spring を参照。\n"),
  term("docs/records", "docs/records を参照。\n"),
  term("ORD-0", "実例は ORD-001 を参照。\n"),
  term("PAY-0", "実例は PAY-002 を参照。\n"),
  term("TraceabilityTest", "TraceabilityTest が検査する。\n"),
  term("ExecutionBoundaryTest", "ExecutionBoundaryTest が検査する。\n"),
  term("TestProtectionTest", "TestProtectionTest が検査する。\n"),
  term("DomainArchitectureTest", "DomainArchitectureTest が検査する。\n"),
  term("OrderFormE2eTest", "OrderFormE2eTest を参照。\n"),
  term("OrderControllerHttpTest", "OrderControllerHttpTest を参照。\n"),
  term("PaymentState", "PaymentState を参照。\n"),
  term("kotlin-samples.yml", "kotlin-samples.yml のジョブ。\n"),
  term("nightly-mutation.yml", "nightly-mutation.yml のジョブ。\n"),
  term("pr-bug-fix-check.yml", "pr-bug-fix-check.yml のジョブ。\n"),
  term("check-bug-fix-pr.sh", "check-bug-fix-pr.sh を実行する。\n"),
  term(".github/ のパス", "`.github/workflows/` に置く。\n"),
  term("scripts/ のパス", "`scripts/check.sh` を実行する。\n"),
  term("Issue 番号", "詳細は #123 を参照。\n"),
  term("括弧内の PR 番号", "修正した (#45)。\n"),
  term("GH-数字", "GH-12 を参照。\n"),
  term("コードブロックの中の固有の語", "```text\n./gradlew test\n```\n"),
];

const selfTest = () => {
  let failed = 0;
  for (const c of SELF_TESTS) {
    const got = [...new Set(checkMarkdown(c.file, c.text, c.exists).map((v) => v.rule))].sort();
    const want = [...new Set(c.expect)].sort();
    if (got.join(",") !== want.join(",")) {
      failed += 1;
      console.error(`自己テスト失敗: ${c.name}（期待 [${want}]、実際 [${got}]）`);
    }
  }
  const violationCases = SELF_TESTS.filter((c) => c.expect.length > 0).length;
  if (failed > 0) {
    console.error(`\nlint:portable の自己テストで ${failed} 件が期待と食い違いました`);
    process.exit(1);
  }
  console.log(`lint:portable の自己テスト: 問題なし（${SELF_TESTS.length} ケース、うち違反の検出 ${violationCases} ケース）`);
};

if (process.argv.includes("--self-test")) {
  selfTest();
} else {
  const files = listMarkdown(TARGET_DIR).sort();
  const exists = (p) => existsSync(resolve(root, p));
  const errors = [];
  for (const file of files) {
    const text = readFileSync(resolve(root, file), "utf8");
    for (const v of checkMarkdown(relative(root, resolve(root, file)).split("\\").join("/"), text, exists)) {
      errors.push(`${file}:${v.line}: [${v.rule}] ${v.message}`);
    }
  }
  if (errors.length > 0) {
    console.error(errors.join("\n"));
    console.error(`\n${TARGET_DIR}/ の言語中立・自己完結の検査で ${errors.length} 件の問題があります`);
    process.exit(1);
  }
  console.log(`言語中立・自己完結の検査: 問題なし（${files.length} ファイル）`);
}
