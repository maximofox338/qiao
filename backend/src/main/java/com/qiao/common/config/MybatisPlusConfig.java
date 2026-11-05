package com.qiao.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiao.common.api.PageQuery;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;

@Configuration
public class MybatisPlusConfig {

    public MybatisPlusConfig(ObjectMapper objectMapper) {
        JacksonTypeHandler.setObjectMapper(objectMapper);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit((long) PageQuery.MAX_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * Fills {@code createdAt}/{@code updatedAt} on entities that declare them.
     */
    @Bean
    public MetaObjectHandler timestampFiller(Clock clock) {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                Instant now = clock.instant();
                strictInsertFill(metaObject, "createdAt", Instant.class, now);
                strictInsertFill(metaObject, "updatedAt", Instant.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                if (metaObject.hasSetter("updatedAt")) {
                    setFieldValByName("updatedAt", clock.instant(), metaObject);
                }
            }
        };
    }
}
