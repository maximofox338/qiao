package com.qiao.common.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds {@code {STATUS: count}} maps (every enum value present) for dashboards.
 */
public final class StatusCounts {

    private StatusCounts() {
    }

    public static <E extends Enum<E>> Map<String, Long> count(BaseMapper<?> mapper, Class<E> statusType) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (E value : statusType.getEnumConstants()) {
            counts.put(value.name(), 0L);
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        BaseMapper<Object> untyped = (BaseMapper) mapper;
        untyped.selectMaps(new QueryWrapper<>().select("status", "COUNT(*) AS total").groupBy("status"))
                .forEach(row -> counts.put(String.valueOf(row.get("status")), ((Number) row.get("total")).longValue()));
        return counts;
    }
}
