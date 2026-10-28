<!-- markdownlint-disable MD041 -->
<!-- 通常の PR 用。バグ修正の PR(タイトルが fix: で始まるもの)は bug_fix テンプレートを使う: gh pr create --template bug_fix.md、または URL に ?template=bug_fix.md。使っていないと PR Bug Fix Check が失敗する -->
## 概要

Closes #

## 変更内容

## 検証

- [ ] `npm run lint`
- [ ] `examples/kotlin-spring/` で `./gradlew detekt ktlintCheck test pitest`（`examples/kotlin-spring/src/` を変えた場合）
