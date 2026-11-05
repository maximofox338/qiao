package com.qiao.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * Pagination query parameters ({@code page} is 1-based). Bound from the query string.
 */
public record PageQuery(Integer page, Integer size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        page = page == null || page < 1 ? 1 : page;
        size = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    public <T> Page<T> toPage() {
        return Page.of(page, size);
    }
}
