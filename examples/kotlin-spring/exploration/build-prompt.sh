#!/usr/bin/env bash
# AI 探索のプロンプトを標準出力に組み立てる(examples/kotlin-spring/docs/02b.md §3.1)。
# 指示(prompt.md)の後ろに、入力として TRS の生成物のファイルから、UC(§2)と AC の一覧(§3 の AC の表)を連結する。
# 生成物のファイルは、TRS ファイルと同じ名前で docs/trs/generated/ にある(03b §4)。
# AC の一覧は、AC の表(見出しが 7 列の形)の「AC ID」と「検証する振る舞い」の列で、Gherkin のタグ行の @pending:<OI-ID> を AC に添える。
# 読み取りの空振り(UC も AC も 0 件)のときは、空のプロンプトを返さず、失敗にする。
# 期待値は TRS が正で、ここでは書き換えない。
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
trs="${1:-$here/../docs/trs/generated/TODO-001-task-management.md}"

if [[ ! -f "$trs" ]]; then
  echo "build-prompt.sh: 生成物のファイルが無い: $trs" >&2
  exit 1
fi

# UC は「### 2. ユースケース(UC)」から「### 3. 受入条件」の手前まで
uc="$(sed -n '/^### 2\. ユースケース/,/^### 3\. 受入条件/p' "$trs" | sed '$d')"
if [[ -z "$uc" ]]; then
  echo "build-prompt.sh: UC の節を読めない: $trs" >&2
  exit 1
fi

# AC の表の見出し(03a §3)。この見出しと完全に一致する表の行だけを読む
header='| AC ID | 関連 UC | テストレベル | 検証層 | 優先度 | 検証する振る舞い | 根拠 |'

# 1 回目に、Gherkin のタグ行から AC ごとの @pending:<OI-ID> を集める。2 回目に、表の行を「AC ID: 振る舞い」の形で出す
ac="$(awk -F'|' -v header="$header" '
  function trim(s) { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }
  NR == FNR {
    if (match($0, /@AC-[0-9][0-9]-[0-9][0-9]/)) {
      id = substr($0, RSTART + 1, RLENGTH - 1)
      rest = substr($0, RSTART + RLENGTH)
      while (match(rest, /@pending:[^ \t`|]*/)) {
        pending[id] = pending[id] " " substr(rest, RSTART, RLENGTH)
        rest = substr(rest, RSTART + RLENGTH)
      }
    }
    next
  }
  $0 == header { inTable = 1; next }
  inTable && !/^\|/ { inTable = 0 }
  inTable && $2 ~ /^ AC-[0-9][0-9]-[0-9][0-9] $/ {
    id = trim($2)
    printf "%s%s: %s\n", id, pending[id], trim($7)
  }
' "$trs" "$trs")"
if [[ -z "$ac" ]]; then
  echo "build-prompt.sh: AC の表を読めない(見出しが 7 列の形の表が無い、または行が無い): $trs" >&2
  exit 1
fi

cat "$here/prompt.md"

printf '\n## 入力 1: ユースケース(UC)\n\n'
printf '%s\n' "$uc"

printf '\n## 入力 2: 受入条件(AC)の一覧\n\n'
printf '各行は、AC の番号と、検証する振る舞いである。@pending: が付いた AC は、期待値が未確定である。\n\n'
printf '%s\n' "$ac"
