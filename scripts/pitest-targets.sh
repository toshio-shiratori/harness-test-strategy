#!/usr/bin/env bash
# PR の差分から PIT の targetClasses（カンマ区切り）を決めて標準出力に出す。
#   ALL  : ドメイン層全体を対象にする（ビルド設定やテストのみの変更など、クラスを特定できない場合）
#   NONE : ミューテーション対象の変更がない
# 使い方: scripts/pitest-targets.sh origin/main
set -euo pipefail
base="${1:?base ref required}"
changed="$(git diff --name-only "$base"...HEAD)"

# ビルド・検査設定が変わったら全体で確認する（依存更新 PR もここに入る）
if grep -qE '^(build\.gradle\.kts|settings\.gradle\.kts|gradle/|config/|\.editorconfig)' <<<"$changed"; then
  echo ALL
  exit 0
fi

targets=()
while IFS= read -r f; do
  [[ "$f" =~ ^src/main/kotlin/(com/example/(order|payment)/domain)/([A-Za-z0-9]+)\.kt$ ]] || continue
  pkg="${BASH_REMATCH[1]//\//.}"
  file="${BASH_REMATCH[3]}"
  targets+=("$pkg.${file}Kt*") # トップレベル関数のファサードクラス
  # トップレベルの宣言名（class / interface / object。value / data / enum / sealed は修飾子として前置される）
  while IFS= read -r name; do
    targets+=("$pkg.$name*")
  done < <(grep -oE '^(sealed |data |enum |value |annotation |abstract |open )*(class|interface|object) +[A-Za-z0-9_]+' "$f" | awk '{print $NF}')
done <<<"$changed"

if ((${#targets[@]} > 0)); then
  (
    IFS=,
    echo "${targets[*]}"
  )
elif grep -qE '^src/test/kotlin/com/example/(order|payment)/domain/' <<<"$changed"; then
  echo ALL # テストだけが変わった場合、弱体化を検出するため全体を確認する
else
  echo NONE
fi
