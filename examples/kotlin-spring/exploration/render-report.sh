#!/usr/bin/env bash
# `claude -p --output-format json` の出力を、報告用の Markdown に整形して標準出力に出す
# (examples/kotlin-spring/docs/02b.md §3.1)。
# 使い方: render-report.sh <claude の JSON 出力> <claude の終了コード> <所要時間(秒)> <モデル ID>
# 数値は、claude が返した total_cost_usd と session_id、呼び出し側で測った所要時間だけを使う。
set -euo pipefail

out="${1:?claude の JSON 出力のパス}"
status="${2:?claude の終了コード}"
elapsed="${3:?所要時間(秒)}"
model="${4:?モデル ID}"

echo "## AI 探索の結果"
echo
echo "| 項目 | 値 |"
echo "|---|---|"
echo "| モデル | \`${model}\` |"
echo "| claude の終了コード | ${status} |"
echo "| 所要時間(claude の実行) | ${elapsed} 秒 |"

if [ "$status" = "124" ]; then
  echo "| 備考 | 時間切れで claude を止めました(終了コード 124)。費用と報告は残りません |"
fi

if [ ! -s "$out" ] || ! jq -e . "$out" >/dev/null 2>&1; then
  echo "| 費用(推定) | 不明 |"
  echo
  echo "claude の出力が空か、JSON として読めません。出力先の \`claude-stderr.log\` を確認してください。"
  exit 0
fi

jq -r '"| 費用(推定。ログイン認証では請求額と一致しない) | " + (if .total_cost_usd == null then "不明" else "\((.total_cost_usd * 10000 | round) / 10000) USD" end) + " |",
       "| セッション ID | `" + (.session_id // "不明") + "` |"' "$out"
echo

if ! jq -e '.structured_output | type == "object"' "$out" >/dev/null 2>&1; then
  echo "構造化された報告(\`structured_output\`)が返りませんでした。ターン数か時間の上限に達した可能性があります。出力先の \`claude-output.json\` を確認してください。"
  exit 0
fi

jq -r '
  def cls:
    if . == "contradicts_ac" then "AC に反する疑い(04 §2 へ)"
    elif . == "spec_gap" then "TRS が定めていない(03 §4 の Open Issues の候補)"
    elif . == "ux_concern" then "UX の違和感"
    else . end;
  def lines(f): if length == 0 then "- なし" else map("- " + f) | join("\n") end;
  .structured_output as $r
  | "### 気づき: \($r.findings | length) 件\n",
    (if ($r.findings | length) == 0 then "気づきはありませんでした。\n" else
      ($r.findings | to_entries | map(
        "#### \(.key + 1). \(.value.title)\n\n"
        + "- 分類: \(.value.classification | cls)\n"
        + "- 関連する AC・UC: \(if (.value.related | length) == 0 then "なし" else (.value.related | join("、")) end)\n"
        + "- TRS の記述: \(.value.spec_excerpt)\n"
        + "- 観察した事実: \(.value.observed)\n"
        + "- スクリーンショット: \(if (.value.evidence | length) == 0 then "なし" else (.value.evidence | join("、")) end)\n\n"
        + "再現手順:\n\n"
        + (.value.steps | to_entries | map("\(.key + 1). \(.value)") | join("\n")) + "\n"
      ) | join("\n"))
    end),
    "### 探索した範囲\n",
    ($r.explored | lines(.))
' "$out"
