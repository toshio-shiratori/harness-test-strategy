package fixtures

import org.springframework.http.HttpStatus

// 適合例: Spring のコンテキストを起動しない型（HttpStatus 等）の利用は層1でも許可する
class PureLayer1Test {
    val status = HttpStatus.OK
}
