#!/usr/bin/env bash
# PR の差分から PIT の targetClasses（カンマ区切り）を決めて標準出力に出す。
# 対象は層1の全対象（examples/kotlin-spring の src/main の全パッケージ。build.gradle.kts の targetClasses の既定 com.example.*）。
# 詳細は examples/kotlin-spring/docs/02b.md §4.4。
#   ALL  : 層1の全対象を実行する。次のいずれかである。
#          1. examples/kotlin-spring/ の build.gradle.kts・settings.gradle.kts・gradle/・config/・.editorconfig のいずれかが変わった
#          2. 層1のテスト（examples/kotlin-spring/src/test/ 以下）が変わった。本番クラスの変更が同時にあっても ALL にする
#             （テストの弱体化を、変更クラスだけの実行で見逃さないため）
#          3. src/main/kotlin の変更から対象クラスを決められない（削除・リネームされた〔リネームは旧パスの削除として扱う〕、
#             パッケージ規約に合わない、Kotlin 以外のファイル）
#   NONE : 上記以外で、ミューテーション対象の変更がない（src/integrationTest・src/e2eTest・src/main/resources・docs など。
#          これらは PIT の実行（層1のテストと src/main/kotlin のクラス）に入らない）
#   上記以外 : src/main/kotlin の変更ファイルから決めたクラスのパターンをカンマ区切りで出す。
# 使い方: scripts/pitest-targets.sh origin/main（どこから実行してもよい。判定するパスはリポジトリのルートからの相対）
set -euo pipefail
base="${1:?base ref required}"
cd "$(dirname "${BASH_SOURCE[0]}")/.." # 変更ファイルの読み取りがカレントディレクトリに依存しないよう、リポジトリのルートで動かす
changed="$(git diff --name-only --no-renames "$base"...HEAD)" # リネームは旧パスの削除と新パスの追加として出す（旧パスが無い → ALL）

# ビルド・検査設定が変わったら全体で確認する（Gradle の依存更新 PR はここに入る。
# Dependabot の github-actions と npm の PR は該当せず、最終的に NONE になる）
if grep -qE '^examples/kotlin-spring/(build\.gradle\.kts|settings\.gradle\.kts|gradle/|config/|\.editorconfig)' <<<"$changed"; then
  echo ALL
  exit 0
fi

# 層1のテストが変わったら、本番クラスの変更の有無によらず全体で確認する（弱体化の検出）
if grep -qE '^examples/kotlin-spring/src/test/' <<<"$changed"; then
  echo ALL
  exit 0
fi

main_prefix='examples/kotlin-spring/src/main/(kotlin|java)/'
targets=()
while IFS= read -r f; do
  [[ "$f" =~ ^${main_prefix} ]] || continue
  # 本番ソースの変更で、クラスを決められないものは全体で確認する（削除・リネーム、com.example 以外、Kotlin 以外）
  if [[ ! "$f" =~ ^examples/kotlin-spring/src/main/kotlin/(com/example(/[A-Za-z0-9_]+)*)/([A-Za-z0-9_]+)\.kt$ || ! -f "$f" ]]; then
    echo ALL
    exit 0
  fi
  pkg="${BASH_REMATCH[1]//\//.}"
  file="${BASH_REMATCH[3]}"
  targets+=("$pkg.${file}Kt*") # トップレベル関数のファサードクラス
  # トップレベルの宣言名（class / interface / object。修飾子は前置される。fun interface の fun も修飾子として扱う）
  while IFS= read -r name; do
    targets+=("$pkg.$name*")
  done < <(grep -oE '^((sealed|data|enum|value|annotation|abstract|open|internal|private|public|fun) +)*(class|interface|object) +[A-Za-z0-9_]+' "$f" | awk '{print $NF}')
done <<<"$changed"

if ((${#targets[@]} > 0)); then
  (
    IFS=,
    echo "${targets[*]}"
  )
else
  echo NONE
fi
