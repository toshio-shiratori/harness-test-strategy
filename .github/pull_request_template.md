<!-- markdownlint-disable MD041 -->
<!-- 通常の PR 用。バグ修正の PR(タイトルが fix: で始まるもの)は bug_fix テンプレートを使う: gh pr create --template bug_fix.md、または URL に ?template=bug_fix.md。使っていないと PR Bug Fix Check が失敗する -->
## 概要

Closes #

## 変更内容

## 既存の ID の変化

<!-- examples/kotlin-spring/docs/trs/generated/ の既存の ID が変わったときは、ID ごとに「- <ID>: <理由>」を書く。書き方は examples/kotlin-spring/docs/03.md の 5.8。変化が無ければ「なし」 -->
なし

## 検証

- [ ] `npm run lint`
- [ ] `examples/kotlin-spring/` で `./gradlew detekt ktlintCheck test pitest` と `./gradlew pitest -Ppitest.domainOnly=true`（`examples/kotlin-spring/src/` を変えた場合）
