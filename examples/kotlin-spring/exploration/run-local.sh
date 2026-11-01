#!/usr/bin/env bash
# AI 探索(層3)を、手元で一度だけ実行する(examples/kotlin-spring/docs/02b.md の §3.1)。
# リポジトリのスキル(.claude/skills/ai-exploration)が呼ぶ。人が直接実行してもよい。
#
# 流れ: MySQL とアプリを用意し、build-prompt.sh で入力を組み立て、別プロセスの `claude -p` に
# Playwright MCP だけを渡して探索させ、render-report.sh で Markdown にする。
# 探索するエージェントの入力を UC と AC の一覧に限る境界(§3.1.2)を保つため、
# このスクリプトを動かすセッションでは探索しない。
#
# 環境変数(すべて任意。値は検証する)の一覧と既定値は、スキルの SKILL.md の表を正とする。
# 名前は MODEL・MAX_TURNS・TIMEOUT_SEC・BROWSER・NO_SANDBOX・MYSQL_PORT・APP_PORT・OUT_DIR。
#
# 終了コード: 探索が正常に終われば 0。それ以外は 1。
# 時間切れは、報告の「claude の終了コード」の欄に 124 と出る(スクリプトの終了コードは 1)。
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project="$(cd "$here/.." && pwd)"

MODEL="${MODEL:-claude-sonnet-5-5}"
MAX_TURNS="${MAX_TURNS:-100}"
TIMEOUT_SEC="${TIMEOUT_SEC:-1200}"
BROWSER="${BROWSER:-chrome}"
NO_SANDBOX="${NO_SANDBOX:-0}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
APP_PORT="${APP_PORT:-8080}"
OUT_DIR="${OUT_DIR:-$project/build/exploration/$(date +%Y%m%d-%H%M%S)}"

# Playwright MCP の版は固定する(docs/02b.md の §3.1.7)
playwright_mcp="@playwright/mcp@0.0.84"
mysql_name="harness-sample-mysql"

die() { echo "エラー: $*" >&2; exit 1; }

# 1 以上の整数(上限があれば、その値以下)であること
need_int() {
  local name="$1" value="$2" max="${3:-}"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || die "${name} は 1 以上の整数で指定してください"
  if [ -n "$max" ]; then
    if [ "${#value}" -gt "${#max}" ] || [ "$value" -gt "$max" ]; then
      die "${name} は ${max} 以下で指定してください"
    fi
  fi
}

# --- 入力の検証 ---
[[ "$MODEL" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "MODEL の形式が不正です"
need_int MAX_TURNS "$MAX_TURNS"
need_int TIMEOUT_SEC "$TIMEOUT_SEC"
[[ "$BROWSER" =~ ^(chrome|firefox|webkit|msedge)$ ]] || die "BROWSER は chrome / firefox / webkit / msedge のいずれかです"
[[ "$NO_SANDBOX" =~ ^[01]$ ]] || die "NO_SANDBOX は 0 か 1 です"
need_int MYSQL_PORT "$MYSQL_PORT" 65535
need_int APP_PORT "$APP_PORT" 65535

for cmd in claude jq curl docker npx lsof pgrep pkill; do
  command -v "$cmd" > /dev/null || die "${cmd} が見つかりません"
done

app_url="http://localhost:$APP_PORT/"
mkdir -p "$OUT_DIR/playwright"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

# --- 後片付け: 自分が起動したものだけ止める ---
started_mysql=0
gradle_pid=""
app_pids=()
claude_pid=""
watchdog_pid=""
claude_dir=""

# 自分の子であるときだけ、プロセスとその子孫を止める(子孫を先に集めてから止める)
is_my_child() { [ "$(ps -o ppid= -p "$1" 2> /dev/null | tr -d ' ')" = "$$" ]; }
kill_tree() {
  local child
  for child in $(pgrep -P "$1" 2> /dev/null); do
    kill_tree "$child"
  done
  kill "$1" 2> /dev/null
}

# 終了コードは、シグナルのときは 128 + シグナル番号を引数で受ける
cleanup() {
  local code=${1:-$?}
  trap - EXIT INT TERM HUP
  set +eu
  if [ -n "$claude_pid" ] && is_my_child "$claude_pid"; then
    kill_tree "$claude_pid"
  fi
  if [ -n "$watchdog_pid" ] && is_my_child "$watchdog_pid"; then
    pkill -P "$watchdog_pid" 2> /dev/null
    kill "$watchdog_pid" 2> /dev/null
  fi
  if [ -n "$gradle_pid" ] && is_my_child "$gradle_pid"; then
    # アプリの JVM は Gradle のデーモンが起動するので、gradle_pid の子孫ではない。
    # 起動待ちの間に止められたときは、まだ記録していないので、ここで記録する
    if [ "${#app_pids[@]}" -eq 0 ]; then
      record_app_pids
    fi
    kill "$gradle_pid" 2> /dev/null
    # 記録した PID のうち、いまも同じポートを待ち受けているものだけを止める(PID の再利用を避ける)
    local pid current
    current=" $(listeners | tr '\n' ' ') "
    for pid in ${app_pids[@]+"${app_pids[@]}"}; do
      case "$current" in *" $pid "*) kill "$pid" 2> /dev/null ;; esac
    done
    wait "$gradle_pid" 2> /dev/null
  fi
  if [ "$started_mysql" = 1 ]; then
    docker stop "$mysql_name" > /dev/null 2>&1
  fi
  [ -n "$claude_dir" ] && rm -rf "$claude_dir"
  exit "$code"
}
trap cleanup EXIT
trap 'cleanup 130' INT
trap 'cleanup 143' TERM
trap 'cleanup 129' HUP

# --- MySQL とアプリを用意する(examples/kotlin-spring/README.md の「サンプルアプリを手元で動かす」と同じ) ---
is_sample_app() { curl -sf --max-time 5 "$app_url" 2> /dev/null | grep -q '<title>タスク管理</title>'; }
listeners() { lsof -ti "tcp:$APP_PORT" -sTCP:LISTEN 2> /dev/null || true; }

# 待ち受けの PID のうち、Java のプロセスだけを記録する(起動前にポートが空だったことを確かめてある)
record_app_pids() {
  local pid
  for pid in $(listeners); do
    if ps -o command= -p "$pid" 2> /dev/null | grep -q java; then
      app_pids+=("$pid")
    fi
  done
}

if is_sample_app; then
  echo "アプリは ${app_url} で動いているので、再利用します(終了時も止めません。DB のタスクは書き換わります)" >&2
else
  if [ -n "$(listeners)" ]; then
    die "ポート ${APP_PORT} は、サンプルアプリ以外が使っています。APP_PORT を変えてください"
  fi
  if docker ps --format '{{.Names}}' | grep -qx "$mysql_name"; then
    echo "MySQL(${mysql_name})は起動済みなので、再利用します(終了時も止めません)" >&2
  else
    echo "MySQL を起動します(ポート ${MYSQL_PORT})" >&2
    docker run -d --rm --name "$mysql_name" \
      -e MYSQL_ROOT_PASSWORD=sample -e MYSQL_DATABASE=sample \
      -p "$MYSQL_PORT:3306" mysql:8.4 > /dev/null \
      || die "MySQL を起動できません。ポート ${MYSQL_PORT} が使用中なら MYSQL_PORT を変えてください"
    started_mysql=1
    ready=0
    for _ in $(seq 1 60); do
      if docker exec "$mysql_name" mysqladmin ping -h127.0.0.1 -uroot -psample --silent > /dev/null 2>&1; then
        ready=1
        break
      fi
      sleep 2
    done
    [ "$ready" = 1 ] || { docker logs "$mysql_name" >&2; die "MySQL が起動しませんでした"; }
  fi
  # 起動済みの MySQL の公開ポートを、コンテナから読む(MYSQL_PORT と違うことがある)
  db_port="$(docker port "$mysql_name" 3306/tcp | head -n 1 | sed 's/.*://')"
  [[ "$db_port" =~ ^[0-9]+$ ]] || die "MySQL の公開ポートを取得できません"
  echo "アプリを起動します(ポート ${APP_PORT})" >&2
  (
    cd "$project"
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:$db_port/sample" \
      SPRING_DATASOURCE_USERNAME=root \
      SPRING_DATASOURCE_PASSWORD=sample \
      SERVER_PORT="$APP_PORT" \
      exec ./gradlew bootRun
  ) > "$OUT_DIR/app.log" 2>&1 &
  gradle_pid=$!
  ready=0
  for _ in $(seq 1 90); do
    if is_sample_app; then
      ready=1
      break
    fi
    sleep 2
  done
  record_app_pids
  [ "$ready" = 1 ] || { tail -n 50 "$OUT_DIR/app.log" >&2; die "アプリが起動しませんでした"; }
fi

# --- Playwright MCP を先に取得する(探索中の初回取得で MCP の接続が遅れないようにする)。claude の版も残す ---
npx -y "$playwright_mcp" --help > /dev/null 2>&1 || die "${playwright_mcp} を取得できません"
claude --version > "$OUT_DIR/claude-version.txt" 2>&1 || true

# --- 入力と MCP 設定を組み立てる(探索のエージェントには、この 2 つだけを渡す) ---
"$here/build-prompt.sh" | sed "s#http://localhost:8080#http://localhost:$APP_PORT#g" > "$OUT_DIR/prompt.txt"
jq -c . "$here/report-schema.json" > "$OUT_DIR/schema.json"
jq -n --arg dir "$OUT_DIR/playwright" --arg pkg "$playwright_mcp" --arg browser "$BROWSER" \
  --arg origin "http://localhost:$APP_PORT" --argjson nosandbox "$NO_SANDBOX" '{
  mcpServers: { playwright: { command: "npx", args: (
    ["-y", $pkg, "--headless", "--browser", $browser, "--isolated"]
    + (if $nosandbox == 1 then ["--no-sandbox"] else [] end)
    + ["--allowed-origins", $origin, "--output-dir", $dir]
  ) } }
}' > "$OUT_DIR/mcp.json"

# --- 探索 ---
# claude は、リポジトリの外の空のディレクトリで動かす(リポジトリの CLAUDE.md・設定・メモリを読ませない)。
# --bare は使わない(API キー認証を強制し、ログインだけの環境で失敗するため)。
# 代わりに、設定の読み込み(--setting-sources)・スキル(--disable-slash-commands)・メモリ・保存を止める。
# 環境変数の API キー・トークン・接続先・クラウド経由の指定は外し、利用者のログインで動かす。
claude_dir="$(mktemp -d "${TMPDIR:-/tmp}/ai-exploration.XXXXXX")"
echo "探索を始めます(モデル ${MODEL}、最大 ${MAX_TURNS} ターン、${TIMEOUT_SEC} 秒まで)" >&2
start="$(date +%s)"
set +e
(
  cd "$claude_dir"
  exec env -u ANTHROPIC_API_KEY -u ANTHROPIC_AUTH_TOKEN -u ANTHROPIC_BASE_URL \
      -u CLAUDE_CODE_USE_BEDROCK -u CLAUDE_CODE_USE_VERTEX CLAUDE_CODE_DISABLE_AUTO_MEMORY=1 claude -p \
    --model "$MODEL" \
    --max-turns "$MAX_TURNS" \
    --tools "" \
    --strict-mcp-config --mcp-config "$OUT_DIR/mcp.json" \
    --allowedTools "mcp__playwright__*" \
    --permission-mode dontAsk \
    --setting-sources local \
    --disable-slash-commands \
    --no-session-persistence \
    --output-format json --json-schema "$(cat "$OUT_DIR/schema.json")" \
    < "$OUT_DIR/prompt.txt" > "$OUT_DIR/claude-output.json" 2> "$OUT_DIR/claude-stderr.log"
) &
claude_pid=$!
# macOS には timeout がないので、見張りで止める。時間切れは 124 にそろえる(MCP とブラウザも止める)
(
  # sleep だけが止められたとき(正常終了後の後片付け)は、何もせず終わる
  sleep "$TIMEOUT_SEC" || exit 0
  # claude がすでに終わっている(消えた、またはゾンビ)なら、時間切れにしない
  case "$(ps -o stat= -p "$claude_pid" 2> /dev/null | tr -d ' ')" in "" | Z*) exit 0 ;; esac
  is_my_child "$claude_pid" || exit 0
  touch "$OUT_DIR/.timed-out"
  kill_tree "$claude_pid"
) 2> /dev/null &
watchdog_pid=$!
wait "$claude_pid"
status=$?
{
  pkill -P "$watchdog_pid"
  kill "$watchdog_pid"
  wait "$watchdog_pid"
} 2> /dev/null
set -e
if [ -f "$OUT_DIR/.timed-out" ]; then
  status=124
  rm -f "$OUT_DIR/.timed-out"
fi
elapsed=$(( $(date +%s) - start ))

# --- 報告 ---
"$here/render-report.sh" "$OUT_DIR/claude-output.json" "$status" "$elapsed" "$MODEL" > "$OUT_DIR/report.md"
cat "$OUT_DIR/report.md"
echo
echo "出力先: ${OUT_DIR}"
echo "気づきは自動で起票しません。examples/kotlin-spring/docs/02b.md の §3.1.5 の表に従って、人が扱います。"
exit $(( status == 0 ? 0 : 1 ))
