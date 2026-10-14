package fixtures

// PitestExclusionTest が logicMembers の検出力を確認するための例。コンパイル対象ではない（config/konsist-fixtures/）。

// 適合例: 主コンストラクタのプロパティと、companion の const・関数だけ
@JvmInline
value class CleanValue private constructor(val value: Int) {
    companion object {
        const val MIN = 1

        fun of(value: Int): CleanValue? = if (value >= MIN) CleanValue(value) else null
    }
}

// 適合例: 引数なしのスーパータイプ呼び出しは許す
open class CleanParent

class CleanChild(val m: Int) : CleanParent()

interface Greeter

// 違反例
class WithFunction(val value: Int) {
    fun twice(): Int = value * 2
}

class WithInit(val value: Int) {
    init {
        require(value > 0)
    }
}

class WithBodyProperty(val value: Int) {
    val doubled: Int = value * 2
}

class WithCustomGetter(val value: Int) {
    val doubled: Int get() = value * 2
}

class WithDefaultValue(val value: Int = 1)

class WithSecondaryConstructor(val value: Int) {
    constructor() : this(1)
}

open class ArgParent(val n: Int)

class WithParentArguments(val m: Int) : ArgParent(m + 1)

class WithInterfaceDelegation(val g: Greeter) : Greeter by g

class WithNestedClass(val value: Int) {
    class Inner
}

class WithCompanionInit(val value: Int) {
    companion object {
        init {
            println("companion の init は外側クラスの static 初期化子に入る")
        }
    }
}

class WithCompanionJvmStatic(val value: Int) {
    companion object {
        @JvmStatic
        fun create(): WithCompanionJvmStatic = WithCompanionJvmStatic(1)
    }
}

class WithCompanionProperty(val value: Int) {
    companion object {
        val limit: Int = 10
    }
}
