package com.zhangjiyan.template.soc.correlation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SocIncidentEvidenceMapper extends BaseMapper<SocIncidentEvidence> {
    @Select("SELECT * FROM soc_incident_evidence WHERE cluster_id = #{clusterId} AND evidence_type = #{evidenceType} AND evidence_id = #{evidenceId} AND deleted = 0 LIMIT 1")
    SocIncidentEvidence selectByClusterAndEvidence(@Param("clusterId") Long clusterId,
                                                   @Param("evidenceType") String evidenceType,
                                                   @Param("evidenceId") Long evidenceId);
}
