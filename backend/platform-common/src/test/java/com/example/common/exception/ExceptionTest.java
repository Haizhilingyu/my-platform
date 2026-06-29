package com.example.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 异常类测试。
 */
@DisplayName("异常体系")
class ExceptionTest {

    @Test
    @DisplayName("BizException：默认 code=500")
    void should_defaultTo500_when_noCode() {
        BizException ex = new BizException("业务错误");

        assertThat(ex.getCode()).isEqualTo(500);
        assertThat(ex.getMessage()).isEqualTo("业务错误");
    }

    @Test
    @DisplayName("BizException：自定义 code")
    void should_useCustomCode_when_specified() {
        BizException ex = new BizException(400, "参数错误");

        assertThat(ex.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("NotFoundException：code=404")
    void should_be404_when_notFound() {
        NotFoundException ex = new NotFoundException("用户", 123L);

        assertThat(ex.getCode()).isEqualTo(404);
        assertThat(ex.getMessage()).contains("用户", "123");
    }

    @Test
    @DisplayName("ForbiddenException：code=403")
    void should_be403_when_forbidden() {
        ForbiddenException ex = new ForbiddenException("无权限");

        assertThat(ex.getCode()).isEqualTo(403);
    }
}
