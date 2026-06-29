package com.example.common.web;

import com.example.common.result.PageResult;
import org.springframework.data.domain.Page;

/**
 * 分页工具类。将 Spring Data 的 Page 转换为 PageResult。
 */
public final class PageUtils {

    private PageUtils() {}

    public static <T> PageResult<T> toPageResult(Page<T> page) {
        return PageResult.of(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize());
    }
}
