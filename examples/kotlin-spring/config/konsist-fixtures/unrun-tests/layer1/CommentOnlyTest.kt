package fixtures

// UnrunTestDetectionTest の適合例: コメントの中の @Test や org.junit.jupiter.api.Test の言及は違反にしない（コンパイル対象ではない。検査関数が判定できることを確認する用）
/**
 * JUnit の `@Test` は層1では実行されない（org.junit.jupiter.api.Test）。
 */
class CommentOnlyTest
