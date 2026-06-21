package com.zhangjiyan.template.soc.correlation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SocIncidentClusterMapper extends BaseMapper<SocIncidentCluster> {
    @Select("SELECT * FROM soc_incident_cluster WHERE correlation_key = #{correlationKey} AND deleted = 0 LIMIT 1")
    SocIncidentCluster selectByCorrelationKey(@Param("correlationKey") String correlationKey);
}
