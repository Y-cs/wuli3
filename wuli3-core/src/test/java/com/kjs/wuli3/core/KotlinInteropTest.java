package com.kjs.wuli3.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证同一模块内 Java 与 Kotlin 测试源码可以稳定混编。 */
final class KotlinInteropTest {
    @Test
    void javaCanCallKotlin() {
        assertThat(KotlinInteropFixture.isNull(null)).isTrue();
    }
}
