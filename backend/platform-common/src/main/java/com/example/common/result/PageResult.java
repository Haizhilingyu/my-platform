package com.example.common.result;

import java.util.List;

/**
 * 分页查询结果。
 *
 * @param <T> 数据类型
 */
public record PageResult<T>(List<T> list, long total, int pageNum, int pageSize) {

  public static <T> PageResult<T> of(List<T> list, long total, int pageNum, int pageSize) {
    return new PageResult<>(list, total, pageNum, pageSize);
  }

  public int getTotalPages() {
    return pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
  }
}
