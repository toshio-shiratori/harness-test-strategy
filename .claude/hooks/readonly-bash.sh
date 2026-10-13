#!/bin/bash
# 読み取り専用のレビュー役(spec-reviewer / gate-guard)の Bash を制限する PreToolUse フック。
# 標準入力の JSON から tool_input.command を取り、書き込み系なら一致したパターンを
# stderr に出して終了コード 2 で止める。それ以外は終了コード 0。
# 脅威モデル: 協調的な読み取り専用エージェントの誤操作を防ぐガードであり、セキュリティ境界ではない。
#   悪意ある回避は想定しない。シェル構文は精密に再現せず、見慣れない構文は拒否する(フェイルクローズ)。
#   JSON が不正、jq が失敗、command が空のときも終了コード 2 で止める。
# 拒否リスト方式。漏れが見つかったら deny_* に足す。回避用のフラグや環境変数は設けない。
# macOS 標準の bash 3.2 で動く書き方にしている(${var^^} などは使わない)。
# ラッパー(env / sudo / nice / time / command / nohup / exec / xargs)は、値を取らないと
#   分かっているオプションだけを読み飛ばして後ろのコマンド名を判定する。未知のオプションは拒否する。
# 既知の限界(検知しない):
#   - クォート内の文字列(例: awk '{print > "f"}' のリダイレクト、grep の引数など)
#   - python -c / node -e など別言語経由の書き込み
#   - sed の w / e コマンド
#   - ヒアドキュメントの本文(コマンドとして判定されて誤拒否になることもある)
#   - timeout など環境依存のラッパー

ESC=$'\001'   # エスケープ(バックスラッシュ + 1文字)を置き換えるマーカー

block() {
  echo "Blocked (read-only reviewer): $1" >&2
  echo "Command: ${COMMAND:-<取得できず>}" >&2
  exit 2
}

INPUT=$(cat)
COMMAND=$(printf '%s' "$INPUT" | jq -er '.tool_input.command // empty' 2>/dev/null) \
  || block '入力 JSON を解釈できない(フェイルクローズ)'
[ -z "$COMMAND" ] && block 'tool_input.command が空(フェイルクローズ)'

upper() { printf '%s' "$1" | tr '[:lower:]' '[:upper:]'; }

# 分からない構文は拒否する: $'...'、行継続(バックスラッシュ + 改行)
case "$COMMAND" in
  *"\$'"*) block "\$'...' (ANSI-C クォート) は解釈しない" ;;
  *'\'$'\n'*) block '行継続(バックスラッシュ + 改行)は解釈しない' ;;
esac

# 1) \x の形のエスケープを ESC に置き換える(先に消費するので \' や \" をクォートと読み違えない)
# 2) クォート内を Q に置換する。ダブルクォート内の $( とバッククォートは実行されるので拒否する
# 3) 単語の中の空クォート(r''m など)は拒否する
SANITIZED=$(printf '%s' "$COMMAND" | perl -0777 -pe '
  our ($bad, $syn);
  s{\\.|\x27[^\x27]*\x27|"((?:[^"\\]|\\.)*)"}{
    my $in = $1;
    if (defined $in) { $bad = 1 if $in =~ /\$\(|`/; ($in eq "") ? "\002" : "Q" }
    elsif (substr($&, 0, 1) eq "\\") { "\001" }
    elsif ($& eq "\x27\x27") { "\002" }
    else { "Q" }
  }gse;
  $syn = 1 if /[^\s;|&()<>\002]\002|\002[^\s;|&()<>\002]/;
  tr/\002/Q/;
  $_ .= "\n__BAD_SUBST__" if $bad;
  $_ .= "\n__BAD_SYNTAX__" if $syn;
') || block '前処理(perl)に失敗(フェイルクローズ)'
case "$SANITIZED" in
  *__BAD_SUBST__*) block 'ダブルクォート内のコマンド置換' ;;
  *__BAD_SYNTAX__*) block '単語の中の空クォートは解釈しない' ;;
esac

# 許可するリダイレクト(2>&1、>/dev/null、2>/dev/null)を除き、残った > を拒否する
STRIPPED=$(printf '%s' "$SANITIZED" | sed -E \
  -e 's/[0-9]*>&[0-9]+//g' \
  -e 's/(&|[0-9]*)>>?[[:space:]]*\/dev\/null//g') \
  || block '前処理(sed)に失敗(フェイルクローズ)'
case "$STRIPPED" in
  *'>'*) block 'リダイレクト > / >>' ;;
esac

# 解釈できない単語(クォート済み、エスケープ入り)か
opaque() { case "$1" in Q|*"$ESC"*) return 0 ;; esac; return 1; }

deny_git() {
  local i=0 sub="" r v joined
  local args=("$@") rest=()
  while [ $i -lt ${#args[@]} ]; do
    case "${args[$i]}" in
      -c) block 'git -c は解釈しない' ;;
      -C|--git-dir|--work-tree|--namespace) i=$((i+2)) ;;
      -*) i=$((i+1)) ;;
      *) sub="${args[$i]}"; rest=("${args[@]:$((i+1))}"); break ;;
    esac
  done
  [ -z "$sub" ] && return 0
  if opaque "$sub"; then block 'git のサブコマンドを解釈できない'; fi
  joined=" ${rest[*]} "
  case "$sub" in
    commit|push|pull|add|rm|mv|reset|checkout|switch|restore|clean|rebase|merge|cherry-pick|revert|am|apply|update-ref|symbolic-ref|gc|prune|filter-branch|replace|notes|submodule|sparse-checkout|checkout-index|read-tree|update-index|difftool|mergetool|clone|init|bisect)
      block "git $sub" ;;
    hash-object)
      case "$joined" in *" -w"*) block 'git hash-object -w' ;; esac ;;
    reflog)
      case "$joined" in *" expire "*|*" delete "*) block 'git reflog expire/delete' ;; esac ;;
    stash)
      case "${rest[0]}" in list|show) : ;; *) block 'git stash (list / show 以外)' ;; esac ;;
    format-patch)
      case "$joined" in *" --stdout "*) : ;; *) block 'git format-patch (--stdout 以外)' ;; esac ;;
    archive)
      case "$joined" in *" -o"*|*" --output"*) block 'git archive -o' ;; esac ;;
    bundle)
      case "${rest[0]}" in create) block 'git bundle create' ;; esac ;;
    branch)
      for r in "${rest[@]}"; do
        case "$r" in -d|-D|-m|-M|-c|-C|-f|-u|--delete|--move|--copy|--force|--set-upstream-to*|--unset-upstream|--edit-description) block "git branch $r" ;; esac
      done
      case "$joined" in
        *" -l "*|*" --list "*|*" --contains "*|*" --merged "*|*" --no-merged "*|*" --show-current "*|*" -a "*|*" -r "*|*" -v "*|*" -vv "*) : ;;
        *) for r in "${rest[@]}"; do case "$r" in -*) ;; *) block 'git branch (作成)' ;; esac; done ;;
      esac ;;
    tag)
      case "$joined" in
        *" -l "*|*" --list "*|*" --contains "*|*" --points-at "*) : ;;
        *) [ ${#rest[@]} -gt 0 ] && block 'git tag' ;;
      esac ;;
    worktree)
      case "${rest[0]}" in add|remove|prune|move|lock|unlock|repair) block "git worktree ${rest[0]}" ;; esac ;;
    remote)
      case "${rest[0]}" in add|remove|rm|rename|set-url|set-head|set-branches|prune) block "git remote ${rest[0]}" ;; esac ;;
    config)
      case "$joined" in
        *" --get"*|*" -l "*|*" --list "*) : ;;
        *) [ ${#rest[@]} -gt 1 ] && block 'git config (書き込み)' ;;
      esac ;;
  esac
  case "$joined" in *" --output"*) block "git $sub --output" ;; esac
  return 0
}

deny_gh() {
  local args=("$@") i=0 w=() r prev="" m g s
  while [ $i -lt ${#args[@]} ]; do
    case "${args[$i]}" in
      -R|--repo|--hostname) i=$((i+2)) ;;
      -*) i=$((i+1)) ;;
      *) w+=("${args[$i]}"); i=$((i+1)) ;;
    esac
  done
  g="${w[0]}"; s="${w[1]}"
  if opaque "$g"; then block 'gh のサブコマンドを解釈できない'; fi
  if [ "$g" != api ] && opaque "$s"; then block 'gh のサブコマンドを解釈できない'; fi
  case "$g" in
    issue|pr)
      case "$s" in create|edit|close|comment|merge|review|reopen|ready|lock|unlock|delete|transfer|pin|unpin|checkout|update-branch|develop|revert) block "gh $g $s" ;; esac ;;
    label)
      case "$s" in create|edit|delete|clone) block "gh label $s" ;; esac ;;
    alias)
      case "$s" in set|delete|import) block "gh alias $s" ;; esac ;;
    auth)
      case "$s" in login|logout|refresh|setup-git|switch) block "gh auth $s" ;; esac ;;
    extension)
      case "$s" in install|remove|upgrade|create|exec) block "gh extension $s" ;; esac ;;
    release|repo|gist|workflow|run|secret|variable|cache|ruleset|project|ssh-key|gpg-key|codespace|config)
      case "$s" in download|create|edit|delete|fork|rename|archive|unarchive|sync|set|set-default|deploy-key|autolink|run|rerun|cancel|enable|disable|upload|delete-asset|add|remove|clone|transfer|item-*|field-*|link|unlink|copy|mark-template|close) block "gh $g $s" ;; esac ;;
    api)
      for r in "${args[@]}"; do
        case "$r" in
          -f|-F|--field|--raw-field|--input|-f*|-F*|--field=*|--raw-field=*|--input=*) block "gh api $r" ;;
          -X|--method) ;;
          -X*) m="${r#-X}"; [ "$(upper "$m")" != "GET" ] && block "gh api $r" ;;
          --method=*) m="${r#--method=}"; [ "$(upper "$m")" != "GET" ] && block "gh api $r" ;;
          *) if [ "$prev" = "-X" ] || [ "$prev" = "--method" ]; then
               [ "$(upper "$r")" != "GET" ] && block "gh api $prev $r"
             fi ;;
        esac
        prev="$r"
      done ;;
  esac
  return 0
}

# ラッパーのオプションが値を取るか(取る場合は値も読み飛ばす)。網羅はしない
takes_value() {
  case "$1:$2" in
    env:-u|env:-C|sudo:-u|sudo:-g|nice:-n|xargs:-n|xargs:-I|xargs:-L|xargs:-P|xargs:-d|xargs:-E) return 0 ;;
  esac
  return 1
}

# 値を取らないと分かっているオプションか。これ以外のラッパーのオプションは拒否する
known_flag() {
  case "$1:$2" in
    env:-i|env:-0|env:-v|sudo:-E|sudo:-H|sudo:-n|sudo:-k|nice:-[0-9]*|time:-p|time:-v|time:-l) return 0 ;;
    command:-p|exec:-c|exec:-l|xargs:-0|xargs:-r|xargs:-t|xargs:-x|xargs:-n[0-9]*|xargs:-P[0-9]*|xargs:-L[0-9]*) return 0 ;;
  esac
  return 1
}

deny_segment() {
  # 引数は単語列(クォートは Q に置換済み)
  local words=("$@") a cmd args joined w b o wrapper lookup=0
  while [ ${#words[@]} -gt 0 ]; do
    w="${words[0]}"; b="${w##*/}"
    case "$w" in
      then|do|else|elif|while|until|if|fi|done|'!'|'{'|'}') words=("${words[@]:1}"); continue ;;
    esac
    case "$w" in GIT_*=*) block 'GIT_* 環境変数の前置' ;; esac
    if [[ "$w" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; then words=("${words[@]:1}"); continue; fi
    case "$b" in
      env|sudo|nice|time|command|nohup|exec|xargs)
        wrapper="$b"; words=("${words[@]:1}")
        while [ ${#words[@]} -gt 0 ]; do
          o="${words[0]}"
          case "$o" in
            --) words=("${words[@]:1}"); break ;;
            -S|--split-string*) block "$wrapper $o は解釈しない" ;;
            -v|-V) if [ "$wrapper" = command ]; then lookup=1; words=("${words[@]:1}"); elif known_flag "$wrapper" "$o"; then words=("${words[@]:1}"); else block "$wrapper のオプション $o を解釈できない"; fi ;;
            -*) if takes_value "$wrapper" "$o"; then words=("${words[@]:2}")
                elif known_flag "$wrapper" "$o"; then words=("${words[@]:1}")
                else block "$wrapper のオプション $o を解釈できない"; fi ;;
            *) break ;;
          esac
        done
        [ $lookup -eq 1 ] && return 0
        continue ;;
    esac
    break
  done
  [ ${#words[@]} -eq 0 ] && return 0
  cmd="${words[0]##*/}"
  if opaque "${words[0]}" || [ "${words[0]#\$}" != "${words[0]}" ]; then
    block 'コマンド名を解釈できない(クォート・エスケープ・変数)'
  fi
  args=("${words[@]:1}")
  joined=" ${args[*]} "

  # 修正系ツールの実行
  for a in "${args[@]}"; do
    case "$a" in
      --fix|--fix=*|--write|--write=*|*ktlintF*|*spotlessA*) block "$cmd $a" ;;
    esac
  done

  case "$cmd" in
    rm|rmdir|mv|cp|mkdir|touch|tee|chmod|chown|ln|link|install|dd|truncate|unlink|shred|patch|ditto|gzip|gunzip|unzip|rsync|split)
      block "$cmd" ;;
    noglob|nocorrect|repeat|coproc|caffeinate|function)
      block "$cmd (zsh の前置語・構文は解釈しない)" ;;
    bash|sh|zsh)
      # --version / -v / --help だけの呼び出しは許可する。それ以外(-c、スクリプト、標準入力)は拒否する
      [ ${#args[@]} -eq 0 ] && block "$cmd (間接実行)"
      for a in "${args[@]}"; do
        case "$a" in --version|-v|--help) ;; *) block "$cmd (間接実行)" ;; esac
      done ;;
    eval|source|.|builtin)
      block "$cmd (間接実行)" ;;
    sed)
      for a in "${args[@]}"; do
        case "$a" in
          --in-place*) block 'sed --in-place' ;;
          --*) ;;
          -*i*) block 'sed -i' ;;
        esac
      done
      ;;
    sort)
      for a in "${args[@]}"; do
        case "$a" in --output*|-o*|-[!-]*o*) block 'sort -o' ;; esac
      done ;;
    curl)
      # -o - / --output -(標準出力)は許可する。ファイル名を指す -o / -O / --output は拒否する
      local ci=0 nx v
      while [ $ci -lt ${#args[@]} ]; do
        a="${args[$ci]}"; nx="${args[$((ci+1))]}"
        case "$a" in
          --output) [ "$nx" = "-" ] || block 'curl --output'; ci=$((ci+1)) ;;
          --output=*) [ "${a#--output=}" = "-" ] || block 'curl --output' ;;
          --remote-name*) block 'curl --remote-name' ;;
          --*) ;;
          -*[O]*) block 'curl -O' ;;
          -*o*)
            v="${a#*o}"
            if [ -z "$v" ]; then [ "$nx" = "-" ] || block 'curl -o'; ci=$((ci+1))
            else [ "$v" = "-" ] || block 'curl -o'; fi ;;
        esac
        ci=$((ci+1))
      done ;;
    tar)
      for a in "${args[@]}"; do
        case "$a" in --extract|--get) block 'tar --extract' ;; --*) ;; -*x*) block 'tar -x' ;; esac
      done
      for a in "${args[@]}"; do
        case "$a" in --create) block 'tar --create' ;; --*) ;; -*c*f*|-*f*c*) block 'tar -c ... f' ;; esac
      done
      case "${args[0]}" in -*) ;; *x*|*c*f*) block 'tar x / c' ;; esac ;;
    perl)
      for a in "${args[@]}"; do
        case "$a" in -*i*) block 'perl -i' ;; esac
      done ;;
    find)
      case "$joined" in *" -delete "*|*" -exec "*|*" -execdir "*|*" -ok "*|*" -fprint"*|*" -fls "*) block 'find -delete/-exec/-fprint/-fls' ;; esac ;;
    npm)
      case "${args[0]}" in
        ci|install|i|add|uninstall|remove|rm|update|up|link|publish|version|pack|dedupe|prune) block "npm ${args[0]}" ;;
        audit)
          case "$joined" in *" fix "*|*" fix") block 'npm audit fix' ;; esac ;;
        pkg)
          case "${args[1]}" in set|delete|fix) block "npm pkg ${args[1]}" ;; esac ;;
        run|run-script|rum|urn)
          for a in "${args[@]}"; do
            case "$a" in *fix*) block "npm run ($a)" ;; esac
          done ;;
      esac ;;
    git) deny_git "${args[@]}" ;;
    gh) deny_gh "${args[@]}" ;;
  esac
  return 0
}

# 複合コマンドを分割して各部分を判定する(区切りは && || ; | & $( ( ) バッククォート、改行)
SEGMENTS=$(printf '%s' "$STRIPPED" | sed -E 's/&&|\|\||\$\(|[;|&()`]/\
/g') || block '前処理(分割)に失敗(フェイルクローズ)'
while IFS= read -r seg; do
  read -ra W <<< "$seg"
  [ ${#W[@]} -eq 0 ] && continue
  deny_segment "${W[@]}"
done <<< "$SEGMENTS"

exit 0
