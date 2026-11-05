package com.qiao.review.domain;

import com.qiao.common.persistence.EntityMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewMapper extends EntityMapper<Review> {
}
