package com.kjs.wuli3.core

import com.kjs.wuli3.core.assertion.Asserts

/** Kotlin 测试夹具，验证 Kotlin 可以调用 Java 公共 API。 */
object KotlinInteropFixture {
    @JvmStatic fun isNull(value: Any?): Boolean = Asserts.whenNull(value).condition()
}
