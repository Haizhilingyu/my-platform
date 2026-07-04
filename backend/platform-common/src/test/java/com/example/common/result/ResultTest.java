package com.example.common.result;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Result 统一响应体测试。 */
@DisplayName("统一响应体 Result")
class ResultTest {

  @Test
  @DisplayName("ok(data)：code=200, data 非空")
  void should_return200WithData_when_okWithData() {
    Result<String> result = Result.ok("hello");

    assertThat(result.code()).isEqualTo(200);
    assertThat(result.message()).isEqualTo("success");
    assertThat(result.data()).isEqualTo("hello");
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("ok()：code=200, data=null")
  void should_return200NoData_when_okNoData() {
    Result<Void> result = Result.ok();

    assertThat(result.code()).isEqualTo(200);
    assertThat(result.data()).isNull();
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("fail(code, msg)：返回错误码和消息")
  void should_returnErrorCode_when_fail() {
    Result<Void> result = Result.fail(404, "不存在");

    assertThat(result.code()).isEqualTo(404);
    assertThat(result.message()).isEqualTo("不存在");
    assertThat(result.data()).isNull();
    assertThat(result.isSuccess()).isFalse();
  }
}
