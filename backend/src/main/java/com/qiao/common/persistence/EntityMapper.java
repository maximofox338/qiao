package com.qiao.common.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * Base mapper with a pessimistic-lock read used to serialize status transitions.
 */
public interface EntityMapper<T> extends BaseMapper<T> {

    default T selectByIdForUpdate(Long id) {
        return selectOne(new QueryWrapper<T>().eq("id", id).last("FOR UPDATE"));
    }
}
