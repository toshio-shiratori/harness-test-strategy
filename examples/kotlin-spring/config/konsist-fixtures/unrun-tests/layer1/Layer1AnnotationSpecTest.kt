package fixtures

import io.kotest.core.spec.style.AnnotationSpec

// UnrunTestDetectionTest の違反例: 層1の AnnotationSpec。import なしの入れ子の @Test を使うため JUnit の @Test と区別せず、違反にする（制約として KDoc に明記）（コンパイル対象ではない。検査関数が判定できることを確認する用）
class Layer1AnnotationSpecTest : AnnotationSpec() {
    @Test
    fun runs() {
    }
}
