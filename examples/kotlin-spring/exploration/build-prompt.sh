#!/usr/bin/env bash
# AI 探索のプロンプトを標準出力に組み立てる(examples/kotlin-spring/docs/02b.md §3.1)。
# 指示(prompt.md)の後ろに、入力として TRS の UC(§2)と AC の一覧(§3 の Scenario)を連結する。
# 期待値は TRS が正で、ここでは書き換えない。
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
trs="${1:-$here/../docs/trs/TODO-001-task-management.md}"

cat "$here/prompt.md"

printf '\n## 入力 1: ユースケース(UC)\n\n'
# UC は「### 2. ユースケース(UC)」から「### 3. 受入条件」の手前まで
sed -n '/^### 2\. ユースケース/,/^### 3\. 受入条件/p' "$trs" | sed '$d'

printf '\n## 入力 2: 受入条件(AC)の一覧\n\n'
printf '各行は、AC の番号(@AC-xx-yy)と、検証する振る舞いの題名である。\n\n'
# タグ行(@AC-…)と Scenario 行だけを取り出す。字下げを除く
grep -E '^[[:space:]]+(@AC-|Scenario)' "$trs" | sed -E 's/^[[:space:]]+//'
