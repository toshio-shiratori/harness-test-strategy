// 04 §3・§5 の運用の指標のうち、機械で集められるものを集め、週次の Issue の本文を作る（起票もできる）。
// 判断はしない。人が見るのは、このスクリプトが作った Issue だけにする（実現例は examples/kotlin-spring/docs/04.md §5）。
//
//   node scripts/weekly-ops-report.mjs                    本文を標準出力に出す（起票しない。ローカルの確認用）
//   node scripts/weekly-ops-report.mjs --create           同じ週の Issue が無ければ起票する（ラベルが無ければ止まる）
//   node scripts/weekly-ops-report.mjs --self-test        集計と本文の生成を、スクリプト内の固定データで確かめる（GitHub に接続しない）
//   --repo <owner/name>   対象のリポジトリ。省略時は環境変数 GITHUB_REPOSITORY、無ければ gh が解決したもの
//   --now <ISO 日時>      「今」を差し替える（週の境界の確認用）
//   --out <ファイル>      本文をファイルにも書く（--create のとき、標準出力には状況だけを出すため）
//
// 週は JST の ISO 週で、対象は「直近の完了した週」（月曜 0 時 JST から 7 日）。同じ週の Issue は題（週の鍵）で判定し、重複して作らない。
// データ源は GitHub の REST API（gh api）とチェックアウトしたソースだけで、依存パッケージは無い。
// データ源が 1 つ取れなくても、その節に「取得できず」と書いて続ける。
import { execFile, execFileSync } from "node:child_process";
import { mkdtempSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { promisify } from "node:util";

const ROOT = resolve(import.meta.dirname, "..");
const DAY = 24 * 3600 * 1000;
const JST = 9 * 3600 * 1000;
const LABEL = "ops:weekly";
const TITLE_PREFIX = "週次運用レポート";
const NIGHTLY = { file: "nightly-mutation.yml", name: "Nightly Mutation" };
const CI = { file: "kotlin-samples.yml", name: "Kotlin Samples" };
const CHECK_WORKFLOW = "PR Bug Fix Check";
const E2E_DIR = "examples/kotlin-spring/src/e2eTest/kotlin";
// 04 §4.1 の例（直近 30 日で flaky 判定が 3 回以上）。標準の例の値で、見直しの候補を示すだけ
const FLAKY_EXAMPLE_LIMIT = 3;
// PBT を含むジョブ。失敗した後の再試行で成功しても、flaky ではなく反例の可能性があるので、候補と分ける
const PBT_JOBS = new Set(["test", "pbt"]);
// Mutation Score が閾値にどこまで近づいたら要確認とするか（ポイント）。標準から決まらない仮の線
const NEAR_THRESHOLD_POINTS = 5;

// ---------- 時刻 ----------

const pad = (n) => String(n).padStart(2, "0");
const jst = (ms) => new Date(ms + JST);
const ymd = (ms) => `${jst(ms).getUTCFullYear()}-${pad(jst(ms).getUTCMonth() + 1)}-${pad(jst(ms).getUTCDate())}`;
const md = (ms) => `${pad(jst(ms).getUTCMonth() + 1)}-${pad(jst(ms).getUTCDate())}`;
const hm = (ms) => `${pad(jst(ms).getUTCHours())}:${pad(jst(ms).getUTCMinutes())}`;
const iso = (ms) => new Date(ms).toISOString().replace(/\.\d{3}Z$/, "Z");

// JST の暦日が属する ISO 週（木曜を含む年が週の年）
export function isoWeekKey(ms) {
  const d = new Date(Date.UTC(jst(ms).getUTCFullYear(), jst(ms).getUTCMonth(), jst(ms).getUTCDate()));
  d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
  const year = d.getUTCFullYear();
  const week = Math.ceil(((d - Date.UTC(year, 0, 1)) / DAY + 1) / 7);
  return `${year}-W${pad(week)}`;
}

// 直近の完了した週（月曜 0 時 JST から 7 日）と、直近 30 日の窓
export function reportWindow(nowMs) {
  const midnight = Math.floor((nowMs + JST) / DAY) * DAY - JST;
  const dow = jst(midnight).getUTCDay() || 7;
  const end = midnight - (dow - 1) * DAY;
  const start = end - 7 * DAY;
  return {
    start,
    end,
    key: isoWeekKey(start),
    days30Start: end - 30 * DAY,
    // 窓の終わりの日（実行した月曜）がその月の最初の月曜（1〜7 日）なら、月次の項目を足す
    monthlyDue: jst(end).getUTCDate() <= 7,
  };
}

// ---------- 集計（純粋関数） ----------

const inWindow = (ms, from, to) => ms >= from && ms < to;
const median = (xs) => {
  if (xs.length === 0) return null;
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};
const fmtMin = (sec) => (sec === null ? "-" : `${Math.floor(sec / 60)}分${pad(Math.round(sec % 60))}秒`);

// 夜間ジョブのログ（pitest）から、全体とドメイン層だけの Mutation Score を取る。
// `##[group]Run ./gradlew pitest ...` の段ごとに、最後の `Generated N mutations Killed M (P%)` を採る。
export function parseMutationLog(text) {
  const out = { full: null, domain: null };
  let current = null;
  for (const line of text.split("\n")) {
    const g = line.match(/##\[group\]Run (.*)$/);
    if (g) {
      const cmd = g[1];
      if (!cmd.includes("./gradlew pitest")) current = null;
      else {
        const threshold = cmd.match(/-Ppitest\.threshold=(\d+)/);
        current = { kind: cmd.includes("domainOnly=true") ? "domain" : "full", threshold: threshold ? Number(threshold[1]) : null };
      }
      continue;
    }
    const m = line.match(/Generated (\d+) mutations Killed (\d+) \((\d+)%\)/);
    if (m && current) {
      const total = Number(m[1]);
      const killed = Number(m[2]);
      out[current.kind] = { total, killed, pct: Number(m[3]), threshold: current.threshold };
    }
  }
  return out;
}

// 最新の試行で成功したジョブのうち、前の試行で同名のジョブが失敗していたものを flaky の候補とする。
// ただし PBT を含むジョブ（PBT_JOBS）は、反例の可能性があるため候補と分け（pbtSuspects）、候補にも分母にも数えない。
// 再実行の回数は、実行ごとの (試行回数 - 1) の合計。分母は最新の試行の、成功・失敗で終わった PBT 以外のジョブの数
export function analyzeFlaky(runs) {
  const candidates = [];
  const pbtSuspects = [];
  let reruns = 0;
  let jobCount = 0;
  for (const run of runs) {
    reruns += Math.max(0, run.attempt - 1);
    for (const job of run.jobs) {
      if (PBT_JOBS.has(job.name)) continue;
      if (job.conclusion === "success" || job.conclusion === "failure") jobCount += 1;
    }
    for (const job of run.jobs) {
      if (job.conclusion !== "success") continue;
      const failedBefore = run.earlier.some((jobs) => jobs.some((j) => j.name === job.name && j.conclusion === "failure"));
      if (!failedBefore) continue;
      const item = { runId: run.id, workflow: run.workflow, job: job.name, createdAt: run.createdAt };
      (PBT_JOBS.has(job.name) ? pbtSuspects : candidates).push(item);
    }
  }
  return { candidates, pbtSuspects, reruns, jobCount };
}

// 成功したジョブの所要時間（秒）。stepPattern を渡すと、その名前の手順の所要時間も返す
export function jobDurations(runs, workflow, jobName, stepPattern) {
  const jobs = [];
  const steps = [];
  for (const run of runs) {
    if (run.workflow !== workflow) continue;
    for (const job of run.jobs) {
      if (job.name !== jobName || job.conclusion !== "success" || !job.startedAt || !job.completedAt) continue;
      jobs.push((Date.parse(job.completedAt) - Date.parse(job.startedAt)) / 1000);
      const step = stepPattern ? job.steps.find((s) => stepPattern.test(s.name) && s.startedAt && s.completedAt) : null;
      if (step) steps.push((Date.parse(step.completedAt) - Date.parse(step.startedAt)) / 1000);
    }
  }
  return { jobs, steps };
}

export function parseDiscoveryLayer(body) {
  const m = (body ?? "").replace(/\r/g, "").match(/###\s*発見層\s*\n+\s*([^\n]+)/);
  return m ? m[1].trim() : "不明（Issue Form 以外の起票）";
}

// 同じ週の Issue があれば返す。重複の判定は題の末尾の週の鍵で行う（検索 API は反映が遅れるので使わない）
export function findExistingIssue(issues, key) {
  return issues.find((i) => !i.pull_request && i.title.trim() === `${TITLE_PREFIX} ${key}`) ?? null;
}

// ソースから E2E の件数を数える。`@Test`（`@TestInstance` などは数えない）と、02c §5.1 の領域タグ `@e2e:<領域>`
export function countE2e(fileTexts) {
  let tests = 0;
  const areas = {};
  for (const text of fileTexts) {
    tests += (text.match(/^\s*@Test\b/gm) ?? []).length;
    for (const m of text.matchAll(/@e2e:([a-z-]+)/g)) areas[m[1]] = (areas[m[1]] ?? 0) + 1;
  }
  const tagged = Object.values(areas).reduce((a, b) => a + b, 0);
  return { tests, areas, tagged };
}

// ---------- 取得（gh api） ----------

function gh(args) {
  return execFileSync("gh", args, { encoding: "utf8", maxBuffer: 256 * 1024 * 1024, stdio: ["ignore", "pipe", "pipe"] });
}
const execFileAsync = promisify(execFile);
// 例外の理由を 1 行にする。gh の失敗は message の 1 行目が「Command failed: gh ...」だけなので、標準エラーの 1 行目を優先する
export function errorLine(e) {
  const first = (x) => String(x ?? "").trim().split("\n")[0].trim();
  return first(e?.stderr) || first(e?.message ?? e);
}
const ghAsync = async (args) => (await execFileAsync("gh", args, { encoding: "utf8", maxBuffer: 256 * 1024 * 1024 })).stdout;
const api = async (path) => JSON.parse(await ghAsync(["api", path]));
// ジョブのログは色付けの制御文字（ESC）を含む。新しい gh の api は、制御文字を含む応答を既定で出力せずに失敗する
// （「the response contains terminal escape sequences; pass --allow-escape-sequences to output it anyway」）。
// ログは端末に出さず parseMutationLog で数値だけを取り出すので、このオプションを付ける。古い gh には無いので、ヘルプで有無を確かめる
let escapeFlag;
const allowEscapeArgs = () => {
  escapeFlag ??= gh(["api", "--help"]).includes("--allow-escape-sequences") ? ["--allow-escape-sequences"] : [];
  return escapeFlag;
};
const jobLog = (repo, jobId) => ghAsync(["api", ...allowEscapeArgs(), `repos/${repo}/actions/jobs/${jobId}/logs`]);

// 実行ごとの取得が数百件に及ぶので、同時に GH_CONCURRENCY 件まで並行して取る
const GH_CONCURRENCY = 8;
async function mapLimit(items, fn) {
  const out = new Array(items.length);
  let next = 0;
  await Promise.all(
    Array.from({ length: Math.min(GH_CONCURRENCY, items.length) }, async () => {
      while (next < items.length) {
        const i = next++;
        out[i] = await fn(items[i]);
      }
    }),
  );
  return out;
}

async function paginate(path, key, maxPages = 10) {
  const out = [];
  for (let page = 1; page <= maxPages; page += 1) {
    const res = await api(`${path}${path.includes("?") ? "&" : "?"}per_page=100&page=${page}`);
    const items = key ? res[key] : res;
    out.push(...items);
    if (items.length < 100) break;
  }
  return out;
}

const toJob = (j) => ({
  name: j.name,
  conclusion: j.conclusion,
  startedAt: j.started_at,
  completedAt: j.completed_at,
  steps: (j.steps ?? []).map((s) => ({ name: s.name, conclusion: s.conclusion, startedAt: s.started_at, completedAt: s.completed_at })),
});

async function collectRuns(repo, wf, fromMs, toMs, { withLogs }) {
  const runs = await paginate(`repos/${repo}/actions/workflows/${wf.file}/runs?status=completed&created=${iso(fromMs)}..${iso(toMs)}`, "workflow_runs");
  return mapLimit(runs, async (r) => {
    const jobsOf = async (path) => (await paginate(path, "jobs")).map(toJob);
    const jobsRaw = await paginate(`repos/${repo}/actions/runs/${r.id}/jobs`, "jobs");
    const run = {
      id: r.id,
      workflow: wf.name,
      event: r.event,
      branch: r.head_branch,
      attempt: r.run_attempt,
      conclusion: r.conclusion,
      createdAt: r.created_at,
      jobs: jobsRaw.map(toJob),
      earlier: [],
      mutation: null,
    };
    for (let n = 1; n < r.run_attempt; n += 1) run.earlier.push(await jobsOf(`repos/${repo}/actions/runs/${r.id}/attempts/${n}/jobs`));
    if (withLogs) {
      const pit = jobsRaw.find((j) => j.name === "pitest" && (j.conclusion === "success" || j.conclusion === "failure"));
      if (pit) {
        try {
          run.mutation = parseMutationLog(await jobLog(repo, pit.id));
        } catch (e) {
          run.mutation = { error: errorLine(e) };
        }
      }
    }
    return run;
  });
}

async function collectBugs(repo, fromMs) {
  const [owner, name] = repo.split("/");
  const issues = (await paginate(`repos/${repo}/issues?labels=bug&state=all&sort=updated&direction=desc&since=${iso(fromMs)}`, null)).filter((i) => !i.pull_request);
  const bugs = issues.map((i) => ({
    number: i.number,
    title: i.title,
    createdAt: Date.parse(i.created_at),
    closedAt: i.closed_at ? Date.parse(i.closed_at) : null,
    layer: parseDiscoveryLayer(i.body),
  }));
  const open = (await paginate(`repos/${repo}/issues?labels=bug&state=open`, null)).filter((i) => !i.pull_request).length;
  const pulls = await paginate(`repos/${repo}/pulls?state=closed&sort=updated&direction=desc`, null, 3);
  const fixPulls = pulls.filter((p) => p.merged_at && /^fix(\(.+\))?!?:/.test(p.title));
  // 還元済み: 完了条件の検査（PR Bug Fix Check）が成功した fix: の PR が、Issue を閉じたもの
  const fixes = await mapLimit(fixPulls, async (p) => {
    const runs = (await api(`repos/${repo}/actions/runs?head_sha=${p.head.sha}&per_page=100`)).workflow_runs
      .filter((r) => r.name === CHECK_WORKFLOW)
      .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at));
    const query = "query($o:String!,$n:String!,$num:Int!){repository(owner:$o,name:$n){pullRequest(number:$num){closingIssuesReferences(first:20){nodes{number}}}}}";
    const g = JSON.parse(await ghAsync(["api", "graphql", "-f", `query=${query}`, "-f", `o=${owner}`, "-f", `n=${name}`, "-F", `num=${p.number}`]));
    return {
      number: p.number,
      title: p.title,
      mergedAt: Date.parse(p.merged_at),
      checkPassed: runs.length > 0 && runs[0].conclusion === "success",
      closes: g.data.repository.pullRequest.closingIssuesReferences.nodes.map((x) => x.number),
    };
  });
  return { bugs, fixes, openBugs: open };
}

function e2eBudgetMinutes() {
  const text = readFileSync(join(ROOT, ".github/workflows", CI.file), "utf8");
  const block = text.match(/^ {2}e2e:\n([\s\S]*?)(?=^ {2}\S|(?![\s\S]))/m);
  const t = block?.[1].match(/timeout-minutes:\s*(\d+)/);
  return t ? Number(t[1]) : null;
}

function readE2eSources() {
  const files = [];
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      const p = join(dir, name);
      if (statSync(p).isDirectory()) walk(p);
      else if (p.endsWith(".kt")) files.push(readFileSync(p, "utf8"));
    }
  };
  walk(join(ROOT, E2E_DIR));
  return files;
}

// ---------- 本文 ----------

const FAIL = (name, e) => `取得できず（${name}: ${errorLine(e)}）`;
const section = async (name, fn) => {
  try {
    return await fn();
  } catch (e) {
    return FAIL(name, e);
  }
};
const pct = (m) => (m && m.pct !== null && m.pct !== undefined ? `${m.pct}% (${m.killed}/${m.total})` : "-");

function nightlySection(runs, win) {
  const week = runs.filter((r) => inWindow(Date.parse(r.createdAt), win.start, win.end)).sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
  const rows = week.map((r) => {
    const job = (n) => r.jobs.find((j) => j.name === n)?.conclusion ?? "-";
    const mut = r.mutation?.error ? "取得できず" : null;
    return `| ${md(Date.parse(r.createdAt))} ${hm(Date.parse(r.createdAt))} | ${r.event} | ${job("pitest")} | ${job("pbt")} | ${mut ?? pct(r.mutation?.full)} | ${mut ?? pct(r.mutation?.domain)} |`;
  });
  const failed = week.filter((r) => r.conclusion !== "success").length;
  const thr = [...week].reverse().map((r) => r.mutation?.full?.threshold).find((t) => typeof t === "number");
  const lines = [
    `- 夜間の実行: ${week.length} 回、うち成功以外 ${failed} 回`,
    "",
    "| 実行（JST） | 契機 | pitest | pbt（PBT 増量） | Mutation Score 全体 | ドメイン層だけ |",
    "|---|---|---|---|---|---|",
    ...(rows.length ? rows : ["| （対象期間に夜間の実行なし） | | | | | |"]),
  ];
  const errs = week.filter((r) => r.mutation?.error);
  if (errs.length) lines.push("", `Mutation Score を取得できなかった実行: ${errs.length} 回。最初の 1 件（run ${errs[0].id}）の理由: ${errs[0].mutation.error}`);
  if (thr !== undefined) lines.push("", `夜間の閾値（ログの \`-Ppitest.threshold\`）: ${thr}%`);
  return { text: lines.join("\n"), week, failed, thr };
}

function mutationTrend(runs) {
  const pts = runs
    .filter((r) => r.mutation?.full?.pct !== undefined && r.mutation?.full?.pct !== null)
    .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
  if (pts.length === 0) return "- 直近 30 日に Mutation Score を取れた夜間の実行なし";
  const row = (label, kind) => {
    const v = pts.map((r) => r.mutation[kind]).filter((x) => x && typeof x.pct === "number");
    if (v.length === 0) return `| ${label} | - | - | - | - | - |`;
    const pcts = v.map((x) => x.pct);
    const first = v[0].total;
    const last = v[v.length - 1].total;
    const total = first === last ? `${last}` : `${first} → ${last}（**総数が変わった**）`;
    return `| ${label} | ${pcts[0]}% | ${pcts[pcts.length - 1]}% | ${Math.min(...pcts)}% | ${Math.max(...pcts)}% | ${total} |`;
  };
  const latest = pts[pts.length - 1].mutation.full;
  const lines = [
    `直近 30 日の ${pts.length} 回分（${md(Date.parse(pts[0].createdAt))} 〜 ${md(Date.parse(pts[pts.length - 1].createdAt))}）。`,
    "",
    "| 対象 | 期間の最初 | 最新 | 最小 | 最大 | ミュータントの総数 |",
    "|---|---|---|---|---|---|",
    row("全体", "full"),
    row("ドメイン層だけ", "domain"),
    "",
    "総数が変わると、スコアの比較は対象の変化を含む。",
    "",
  ];
  if (typeof latest.threshold === "number") {
    const gap = latest.pct - latest.threshold;
    lines.push(`- 最新の全体スコアと夜間の閾値（${latest.threshold}%）の差: ${gap} ポイント。${gap < NEAR_THRESHOLD_POINTS ? `**${NEAR_THRESHOLD_POINTS} ポイント未満（要確認）**` : `${NEAR_THRESHOLD_POINTS} ポイント以上で、要確認の線には届かない`}`);
  }
  return lines.join("\n");
}

function flakySection(all, win) {
  const weekRuns = all.filter((r) => inWindow(Date.parse(r.createdAt), win.start, win.end));
  const w = analyzeFlaky(weekRuns);
  const m = analyzeFlaky(all);
  const rate = (a) => (a.jobCount === 0 ? "-" : `${((100 * a.candidates.length) / a.jobCount).toFixed(1)}%`);
  const e2e30 = m.candidates.filter((c) => c.job === "e2e").length;
  const lines = [
    "| 範囲 | 再実行の回数 | flaky の候補（ジョブ） | 候補率 | 反例の可能性（PBT） |",
    "|---|---|---|---|---|",
    `| 対象の週 | ${w.reruns} | ${w.candidates.length} | ${rate(w)} | ${w.pbtSuspects.length} |`,
    `| 直近 30 日 | ${m.reruns} | ${m.candidates.length} | ${rate(m)} | ${m.pbtSuspects.length} |`,
    "",
    `- \`e2e\` ジョブの候補（直近 30 日）: ${e2e30} 件。04 §4.1 の例示の ${FLAKY_EXAMPLE_LIMIT} 回に${e2e30 >= FLAKY_EXAMPLE_LIMIT ? "**達した**" : "達していない"}`,
  ];
  if (w.candidates.length) {
    lines.push("", "対象の週の候補:", "");
    for (const c of w.candidates) lines.push(`- ${c.workflow} / ${c.job}（run ${c.runId}、${md(Date.parse(c.createdAt))}）`);
  }
  if (w.pbtSuspects.length) {
    lines.push("", "対象の週の、PBT を含むジョブの「失敗 → 再試行で成功」。反例の可能性があるので、ログのシードで再現する（02a §2.3.1）:", "");
    for (const c of w.pbtSuspects) lines.push(`- ${c.workflow} / ${c.job}（run ${c.runId}、${md(Date.parse(c.createdAt))}）`);
  }
  lines.push(
    "",
    "候補は、前の試行で失敗したジョブが、同じ実行の再試行で成功したもの。基盤の失敗も含むため、原因は人が確かめる。",
    "PBT を含むジョブ（`test`・`pbt`）は、候補率の分子にも分母にも数えない。",
  );
  return { text: lines.join("\n"), week: w, month: m, e2e30 };
}

function durationSection(all, win) {
  const weekRuns = all.filter((r) => inWindow(Date.parse(r.createdAt), win.start, win.end));
  const budget = e2eBudgetMinutes();
  const row = (label, runs, jobName, stepPattern) => {
    const d = jobDurations(runs, CI.name, jobName, stepPattern);
    return `| ${label} | ${d.jobs.length} | ${fmtMin(median(d.jobs))} | ${fmtMin(d.jobs.length ? Math.max(...d.jobs) : null)} | ${stepPattern ? `${fmtMin(median(d.steps))} / ${fmtMin(d.steps.length ? Math.max(...d.steps) : null)}` : "-"} |`;
  };
  const rows = [];
  for (const [label, runs] of [["対象の週", weekRuns], ["直近 30 日", all]]) {
    rows.push(row(`test（${label}）`, runs, "test"));
    rows.push(row(`integration（${label}）`, runs, "integration"));
    rows.push(row(`e2e（${label}）`, runs, "e2e", /e2eTest/));
  }
  // 予算で打ち切られたジョブは成功にならない。打ち切りの conclusion の値に頼らず、成否を問わず所要時間で数える
  const e2eSecs = all
    .filter((r) => r.workflow === CI.name)
    .flatMap((r) => r.jobs)
    .filter((j) => j.name === "e2e" && j.startedAt && j.completedAt && j.conclusion !== "skipped")
    .map((j) => (Date.parse(j.completedAt) - Date.parse(j.startedAt)) / 1000);
  let budgetLine = "- E2E の予算（02c §5.4）: 取得できず";
  if (budget !== null) {
    const reached = e2eSecs.filter((x) => x >= budget * 60).length;
    const maxSec = e2eSecs.length ? Math.max(...e2eSecs) : null;
    budgetLine = `- E2E の予算（02c §5.4。\`${CI.file}\` の \`e2e\` ジョブの \`timeout-minutes\`）: ${budget} 分。直近 30 日で予算に達した \`e2e\` ジョブ（成否を問わない）: ${reached ? `**${reached} 件**` : "0 件"}。最大の所要時間は予算の ${maxSec === null ? "-" : `${Math.round((100 * maxSec) / (budget * 60))}%`}`;
  }
  return {
    text: [
      "成功したジョブだけを数える。時間は準備（チェックアウト・Gradle のセットアップ）を含むジョブの所要時間。",
      "",
      "| ジョブ | 件数 | 中央値 | 最大 | `e2eTest` の手順（中央値 / 最大） |",
      "|---|---|---|---|---|",
      ...rows,
      "",
      budgetLine,
    ].join("\n"),
  };
}

function e2eCountSection() {
  const c = countE2e(readE2eSources());
  const areas = Object.entries(c.areas).sort();
  return [
    `- E2E のテスト数（\`@Test\`）: ${c.tests} 件。領域タグ（\`@e2e:<領域>\`）の付いた数: ${c.tagged} 件${c.tests !== c.tagged ? "（**一致しない**。02c §5.3 の審査の記載を確かめる）" : ""}`,
    areas.length ? `- 領域の内訳: ${areas.map(([k, v]) => `${k} ${v}`).join("、")}` : "- 領域の内訳: -",
  ].join("\n");
}

function bugSection(data, win) {
  const { bugs, fixes, openBugs } = data;
  const inW = (t) => t !== null && inWindow(t, win.start, win.end);
  const in30 = (t) => t !== null && inWindow(t, win.days30Start, win.end);
  const layers = {};
  for (const b of bugs.filter((x) => in30(x.createdAt))) layers[b.layer] = (layers[b.layer] ?? 0) + 1;
  const total30 = Object.values(layers).reduce((a, b) => a + b, 0);
  const late = Object.entries(layers).filter(([k]) => k.startsWith("層3") || k === "本番").reduce((a, [, v]) => a + v, 0);
  const refluxed = new Set(fixes.filter((f) => f.checkPassed).flatMap((f) => f.closes));
  const closedIn = (inRange) => bugs.filter((b) => inRange(b.closedAt));
  const reflux = (inRange) => {
    const closed = closedIn(inRange);
    const done = closed.filter((b) => refluxed.has(b.number)).length;
    return closed.length === 0 ? "（クローズなし）" : `${done} / ${closed.length}（${Math.round((100 * done) / closed.length)}%）`;
  };
  const lines = [
    "| 件数 | 対象の週 | 直近 30 日 |",
    "|---|---|---|",
    `| 起票された \`bug\` | ${bugs.filter((b) => inW(b.createdAt)).length} | ${total30} |`,
    `| クローズされた \`bug\` | ${closedIn(inW).length} | ${closedIn(in30).length} |`,
    `| マージされた \`fix:\` の PR | ${fixes.filter((f) => inW(f.mergedAt)).length} | ${fixes.filter((f) => in30(f.mergedAt)).length} |`,
    `| 還元率（還元済み / クローズされた \`bug\`） | ${reflux(inW)} | ${reflux(in30)} |`,
    "",
    `- 未クローズの \`bug\`: ${openBugs} 件`,
    "- 還元済み: 完了条件の検査（PR Bug Fix Check）が成功した \`fix:\` の PR が閉じた \`bug\`",
    total30 === 0
      ? "- 発見層の分布（直近 30 日）: 起票なし"
      : `- 発見層の分布（直近 30 日、起票 ${total30} 件）: ${Object.entries(layers).sort().map(([k, v]) => `${k} ${v}`).join("、")}。層3・本番の割合 ${Math.round((100 * late) / total30)}%`,
  ];
  const week = bugs.filter((b) => inW(b.createdAt));
  if (week.length) {
    lines.push("", "対象の週に起票された \`bug\`:", "");
    for (const b of week) lines.push(`- #${b.number} ${b.title}（${b.layer}）`);
  }
  return lines.join("\n");
}

const NOT_COLLECTED = [
  "ヒーリングの件数と履歴（昇格件数を含む）: 見分ける規約と実績が無い",
  "テスト単位の flaky: テストごとの結果の履歴が無い",
  "解釈と判断: 人が行う",
];

export function renderReport(win, parts, repo) {
  const week = `${ymd(win.start)}（月）〜 ${ymd(win.end - DAY)}（日）`;
  const s = (key) => parts[key];
  const lines = [
    `<!-- ops-weekly:${win.key} -->`,
    `# ${TITLE_PREFIX} ${win.key}`,
    "",
    `対象期間: ${week}（JST、7 日）。このレポートは \`scripts/weekly-ops-report.mjs\` が機械で集めた値だけを載せる。判断は人が行う。`,
    `指標の定義は 04 §3・§5、運用の形は実現例 \`examples/kotlin-spring/docs/04.md\` §5 を参照。対象リポジトリ: ${repo}`,
    "",
    "## 1. 夜間の実行（PBT 増量・全体ミューテーション）",
    "",
    typeof s("nightly") === "string" ? s("nightly") : s("nightly").text,
    "",
    "### Mutation Score の推移（層1）",
    "",
    s("trend"),
    "",
    "## 2. flaky の候補と再実行",
    "",
    typeof s("flaky") === "string" ? s("flaky") : s("flaky").text,
    "",
    "## 3. 実行時間",
    "",
    typeof s("duration") === "string" ? s("duration") : s("duration").text,
    "",
    "## 4. E2E の件数",
    "",
    s("e2e"),
    "",
    "## 5. 不具合",
    "",
    s("bugs"),
    "",
    "## 6. 集めていないもの",
    "",
    "理由は実現例 `04.md` §5.4。",
    "",
    ...NOT_COLLECTED.map((x) => `- ${x}`),
    "",
    "## 人が見ること",
    "",
    `- [ ] 夜間の失敗と、Mutation Score が閾値を下回っていないか。閾値との差が ${NEAR_THRESHOLD_POINTS} ポイント未満なら、原因を調べる`,
    "- [ ] flaky の候補の原因。E2E の flaky は 02c §5.4 に従い、隔離して 14 日以内に修復か破棄を決める。PBT の反例の可能性は、シードで再現する（02a §2.3.1）",
    "- [ ] 実行時間が予算内か。超えていれば 04 §4 で審査する",
    "- [ ] 層3・本番で見つかった不具合が増えていないか。増えていれば、還元先の漏れを確かめる",
    "- [ ] ヒーリング履歴のレビュー（履歴の記録ができたら。いまは記録が無く、対象なし）",
  ];
  if (win.monthlyDue) {
    lines.push(
      "",
      "### 月次（月の最初の月曜に起票するレポートだけ）",
      "",
      "- [ ] E2E の見直し（04 §4.1 のトリガー: 実行時間の予算の超過、直近 30 日の flaky、重複、許可領域外、保守コスト）。上の 30 日の欄を使う",
      "- [ ] 指標のレビュー（04 §5 の指標表）。目標の方向から外れた指標があれば、原因と対応を決める",
    );
  }
  lines.push("", "確認したら、この Issue を閉じる。");
  return lines.join("\n");
}

async function buildReport(repo, win) {
  const parts = {};
  let nightlyRuns = [];
  let ciRuns = [];
  let nightlyErr = null;
  let ciErr = null;
  try {
    nightlyRuns = await collectRuns(repo, NIGHTLY, win.days30Start, win.end, { withLogs: true });
  } catch (e) {
    nightlyErr = FAIL("夜間の実行", e);
  }
  try {
    ciRuns = await collectRuns(repo, CI, win.days30Start, win.end, { withLogs: false });
  } catch (e) {
    ciErr = FAIL("CI の実行", e);
  }
  const flakyRuns = [...nightlyRuns, ...ciRuns];
  parts.nightly = nightlyErr ?? await section("夜間", () => nightlySection(nightlyRuns, win));
  parts.trend = nightlyErr ?? await section("Mutation Score", () => mutationTrend(nightlyRuns));
  parts.flaky = nightlyErr && ciErr ? nightlyErr : await section("flaky", () => flakySection(flakyRuns, win));
  parts.duration = ciErr ?? await section("実行時間", () => durationSection(ciRuns, win));
  parts.e2e = await section("E2E の件数", e2eCountSection);
  parts.bugs = await section("不具合", async () => bugSection(await collectBugs(repo, win.days30Start), win));
  return renderReport(win, parts, repo);
}

// ---------- 起票 ----------

function resolveRepo(arg) {
  return arg ?? process.env.GITHUB_REPOSITORY ?? gh(["repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"]).trim();
}

async function create(repo, win, bodyFn) {
  const title = `${TITLE_PREFIX} ${win.key}`;
  try {
    gh(["api", `repos/${repo}/labels/${encodeURIComponent(LABEL)}`]);
  } catch {
    console.error(`ラベル \`${LABEL}\` がありません。先に作成してください（例: gh label create ${LABEL} -R ${repo}）。起票せずに終了します。`);
    process.exit(2);
  }
  const existing = findExistingIssue(await paginate(`repos/${repo}/issues?labels=${encodeURIComponent(LABEL)}&state=all`, null, 3), win.key);
  if (existing) {
    console.log(`同じ週の Issue が既にあります（#${existing.number}、${title}）。起票しません。`);
    return null;
  }
  const body = await bodyFn();
  const dir = mkdtempSync(join(tmpdir(), "weekly-ops-"));
  const file = join(dir, "body.md");
  writeFileSync(file, body);
  const url = gh(["issue", "create", "-R", repo, "--title", title, "--label", LABEL, "--body-file", file]).trim();
  console.log(`起票しました: ${url}`);
  return body;
}

// ---------- 自己テスト ----------

function selfTest() {
  const fails = [];
  const eq = (name, actual, expected) => {
    if (JSON.stringify(actual) !== JSON.stringify(expected)) fails.push(`${name}: 期待 ${JSON.stringify(expected)}、実際 ${JSON.stringify(actual)}`);
  };
  const utc = (s) => Date.parse(s);

  // 週の窓。月曜 09:00 JST（00:00 UTC）に実行 → 直前の月〜日
  let w = reportWindow(utc("2026-11-02T00:00:00Z"));
  eq("窓 start", iso(w.start), "2026-10-25T15:00:00Z");
  eq("窓 end", iso(w.end), "2026-11-01T15:00:00Z");
  eq("週の鍵", w.key, "2026-W44");
  eq("月次（11/2 は最初の月曜）", w.monthlyDue, true);
  // 同じ週の日曜に手で起動しても、対象は同じ（完了した直近の週）
  w = reportWindow(utc("2026-11-08T00:00:00Z"));
  eq("日曜でも同じ週", w.key, "2026-W44");
  eq("月次（11/2 の週の日曜でも最初の月曜）", w.monthlyDue, true);
  // JST と UTC の日付のずれ: 日曜 23:00 UTC は JST で月曜 08:00
  w = reportWindow(utc("2026-11-01T23:00:00Z"));
  eq("JST で月曜になった時刻", w.key, "2026-W44");
  w = reportWindow(utc("2026-11-09T00:00:00Z"));
  eq("翌週の月曜", w.key, "2026-W45");
  eq("月次（11/9 は最初の月曜でない）", w.monthlyDue, false);
  // 年またぎ（2026 年は 53 週ある）
  eq("年またぎ", reportWindow(utc("2027-01-04T00:00:00Z")).key, "2026-W53");
  eq("年明けの第 1 週", reportWindow(utc("2027-01-11T00:00:00Z")).key, "2027-W01");

  // ミューテーションのログ
  const log = [
    "##[group]Run actions/checkout@abc",
    "##[group]Run ./gradlew pitest -Ppitest.threshold=70",
    ">> Generated 4 Killed 4 (100%)",
    ">> Generated 82 mutations Killed 71 (87%)",
    "##[group]Run ./gradlew pitest -Ppitest.domainOnly=true -Ppitest.threshold=70",
    ">> Generated 38 mutations Killed 34 (89%)",
    "##[group]Run python3 - >> $GITHUB_STEP_SUMMARY",
    ">> Generated 1 mutations Killed 0 (0%)",
  ].join("\n");
  eq("ログ（全体）", parseMutationLog(log).full, { total: 82, killed: 71, pct: 87, threshold: 70 });
  eq("ログ（ドメイン層）", parseMutationLog(log).domain, { total: 38, killed: 34, pct: 89, threshold: 70 });
  eq("ログ（無い場合）", parseMutationLog("なし"), { full: null, domain: null });

  // 例外の理由: gh の失敗は標準エラーの 1 行目、無ければ message の 1 行目
  eq("例外の理由（標準エラー）", errorLine({ message: "Command failed: gh api x\nHTTP 403", stderr: "\ngh: HTTP 403: Forbidden\nmore" }), "gh: HTTP 403: Forbidden");
  eq("例外の理由（標準エラーなし）", errorLine(new Error("boom\nstack")), "boom");
  // 夜間の節: 取得できなかった実行の数と、最初の 1 件の理由を表の外に書く
  const nWin = reportWindow(utc("2026-11-02T00:00:00Z"));
  const nRun = (id, createdAt, mutation) => ({ id, createdAt, event: "schedule", conclusion: "success", jobs: [], mutation });
  const nText = nightlySection([
    nRun(10, "2026-10-26T18:00:00Z", { error: "gh: HTTP 403: Forbidden" }),
    nRun(11, "2026-10-27T18:00:00Z", { error: "gh: HTTP 404: Not Found" }),
    nRun(12, "2026-10-28T18:00:00Z", parseMutationLog(log)),
  ], nWin).text;
  eq("夜間の節（理由）", nText.includes("Mutation Score を取得できなかった実行: 2 回。最初の 1 件（run 10）の理由: gh: HTTP 403: Forbidden"), true);
  eq("夜間の節（取れた値）", nText.includes("| 87% (71/82) | 89% (34/38) |"), true);
  eq("夜間の節（全部取れたら理由を書かない）", nightlySection([nRun(12, "2026-10-28T18:00:00Z", parseMutationLog(log))], nWin).text.includes("取得できなかった"), false);

  // flaky の候補: 前の試行で失敗し、最新の試行で成功した同名のジョブだけ
  const j = (name, conclusion) => ({ name, conclusion, steps: [] });
  const runs = [
    { id: 1, workflow: "A", attempt: 2, createdAt: "2026-10-27T00:00:00Z", jobs: [j("e2e", "success"), j("test", "success")], earlier: [[j("e2e", "failure"), j("test", "success")]] },
    { id: 2, workflow: "A", attempt: 1, createdAt: "2026-10-27T00:00:00Z", jobs: [j("e2e", "failure"), j("test", "success")], earlier: [] },
    { id: 3, workflow: "A", attempt: 2, createdAt: "2026-10-27T00:00:00Z", jobs: [j("e2e", "failure")], earlier: [[j("e2e", "failure")]] },
    { id: 4, workflow: "A", attempt: 2, createdAt: "2026-10-27T00:00:00Z", jobs: [j("test", "success")], earlier: [[j("test", "failure")]] },
  ];
  const f = analyzeFlaky(runs);
  eq("flaky の候補", f.candidates.map((c) => `${c.runId}:${c.job}`), ["1:e2e"]);
  eq("PBT のジョブは候補と分ける", f.pbtSuspects.map((c) => `${c.runId}:${c.job}`), ["4:test"]);
  eq("再実行の回数", f.reruns, 3);
  eq("ジョブ実行の件数（PBT のジョブは数えない）", f.jobCount, 3);

  // 所要時間: 成功したジョブだけ、手順は名前で選ぶ
  const timed = [{ workflow: "CI", jobs: [
    { name: "e2e", conclusion: "success", startedAt: "2026-10-27T00:00:00Z", completedAt: "2026-10-27T00:05:00Z", steps: [{ name: "Run ./gradlew e2eTest", startedAt: "2026-10-27T00:01:00Z", completedAt: "2026-10-27T00:04:00Z" }] },
    { name: "e2e", conclusion: "failure", startedAt: "2026-10-27T00:00:00Z", completedAt: "2026-10-27T00:09:00Z", steps: [] },
  ] }];
  eq("所要時間", jobDurations(timed, "CI", "e2e", /e2eTest/), { jobs: [300], steps: [180] });
  eq("中央値（偶数）", median([60, 120]), 90);

  // 発見層、E2E の数え方、重複判定
  eq("発見層", parseDiscoveryLayer("### 発見層\n\n層2 E2E\n\n### 再現手順"), "層2 E2E");
  eq("発見層（フォーム以外）", parseDiscoveryLayer("本文のみ"), "不明（Issue Form 以外の起票）");
  const src = "@TestInstance(PER_CLASS)\nclass X {\n  /** @e2e:display */\n  @Test\n  fun a() {}\n  @Test\n  fun b() {}\n}\n// @e2e:display\n";
  eq("E2E の数え方", countE2e([src]), { tests: 2, areas: { display: 2 }, tagged: 2 });
  const issues = [{ number: 5, title: `${TITLE_PREFIX} 2026-W43` }, { number: 6, title: `${TITLE_PREFIX} 2026-W44`, pull_request: {} }];
  eq("重複なし（PR は除く）", findExistingIssue(issues, "2026-W44"), null);
  eq("重複あり", findExistingIssue(issues, "2026-W43")?.number, 5);

  // 本文: 取得できなかった節があっても生成でき、鍵・チェックリストを含む
  const body = renderReport(reportWindow(utc("2026-11-02T00:00:00Z")), {
    nightly: FAIL("夜間", new Error("x")), trend: "t", flaky: "f", duration: "d", e2e: "e", bugs: "b",
  }, "o/r");
  eq("本文の鍵", body.startsWith("<!-- ops-weekly:2026-W44 -->\n# 週次運用レポート 2026-W44"), true);
  eq("取得できず", body.includes("取得できず（夜間: x）"), true);
  eq("月次の項目", body.includes("### 月次"), true);
  eq("月次でない週は出さない", renderReport(reportWindow(utc("2026-11-09T00:00:00Z")), { nightly: "n", trend: "t", flaky: "f", duration: "d", e2e: "e", bugs: "b" }, "o/r").includes("### 月次"), false);

  if (fails.length) {
    console.error(`自己テスト失敗 ${fails.length} 件`);
    for (const m of fails) console.error(`  - ${m}`);
    process.exit(1);
  }
  console.log("自己テスト: 成功");
}

// ---------- 入口 ----------

if (import.meta.url === `file://${process.argv[1]}`) {
  await main();
}

async function main() {
  const args = process.argv.slice(2);
  const opt = (name) => {
    const i = args.indexOf(name);
    return i >= 0 ? args[i + 1] : undefined;
  };
  if (args.includes("--self-test")) {
    selfTest();
  } else {
    const repo = resolveRepo(opt("--repo"));
    const now = opt("--now") ? Date.parse(opt("--now")) : Date.now();
    const win = reportWindow(now);
    const out = opt("--out");
    if (args.includes("--create")) {
      const body = await create(repo, win, () => buildReport(repo, win));
      if (out && body) writeFileSync(out, body);
    } else {
      const body = await buildReport(repo, win);
      if (out) writeFileSync(out, body);
      console.log(body);
    }
  }
}
