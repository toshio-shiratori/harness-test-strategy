#!/usr/bin/env bash
# バグ修正の PR(タイトルが `fix:` / `fix(scope):` で始まる)が、バグ修正用の PR テンプレートの欄を持ち、
# 04 §2.5 の完了条件を全てチェック済みであることを検査する。バグ修正以外の PR は何もしない。
# 入力は環境変数 TITLE と BODY(PR のタイトルと本文)。ワークフローからも、手元でも同じ形で実行できる。
set -euo pipefail

title="${TITLE:-}"
body="${BODY:-}"

if ! printf '%s' "$title" | grep -Eq '^fix(\([^)]*\))?!?:'; then
  echo "バグ修正の PR ではないため、検査しない: $title"
  exit 0
fi

fail=0
for heading in '## 不具合還元' '## 完了条件'; do
  if ! printf '%s\n' "$body" | grep -Fq "$heading"; then
    echo "::error::バグ修正の PR の本文に「${heading}」の欄がありません。.github/PULL_REQUEST_TEMPLATE/bug_fix.md を使ってください(gh pr create --template bug_fix.md)。"
    fail=1
  fi
done

# 「## 完了条件」から次の「## 」までの間に、未チェックの項目が無いこと
unchecked="$(printf '%s\n' "$body" | awk '
  /^## /{ in_dod = ($0 ~ /^## 完了条件/) ; next }
  in_dod && /^- \[ \]/ { print }
')"
if [ -n "$unchecked" ]; then
  echo "::error::完了条件(04 §2.5)に未チェックの項目があります:"
  printf '%s\n' "$unchecked"
  fail=1
fi

exit "$fail"
