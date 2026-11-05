package com.qiao.billing.domain;

import com.qiao.common.persistence.EntityMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper extends EntityMapper<Payment> {
}
