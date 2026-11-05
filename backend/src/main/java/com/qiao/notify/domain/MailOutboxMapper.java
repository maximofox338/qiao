package com.qiao.notify.domain;

import com.qiao.common.persistence.EntityMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailOutboxMapper extends EntityMapper<MailOutbox> {
}
