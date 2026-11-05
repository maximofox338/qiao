package com.qiao.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> records, long total, long page, long size) {

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        return of(page, page.getRecords().stream().map(mapper).toList());
    }

    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
