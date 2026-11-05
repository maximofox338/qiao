package com.qiao.compliance.domain;

import com.qiao.common.persistence.EntityMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TransferEventMapper extends EntityMapper<TransferEvent> {

    @Select("SELECT COUNT(DISTINCT engineer_user_id) FROM transfer_event WHERE year = #{year}")
    long countUniqueEngineers(@Param("year") int year);

    @Select("SELECT COUNT(DISTINCT engineer_user_id) FROM transfer_event WHERE year = #{year} AND data_kind = #{kind}")
    long countUniqueEngineersByKind(@Param("year") int year, @Param("kind") String kind);

    @Select("""
            <script>
            SELECT DISTINCT engineer_user_id FROM transfer_event WHERE year = #{year} AND engineer_user_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> findCountedEngineers(@Param("year") int year, @Param("ids") List<Long> engineerUserIds);
}
