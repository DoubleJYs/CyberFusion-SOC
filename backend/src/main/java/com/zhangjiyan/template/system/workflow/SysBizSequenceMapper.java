package com.zhangjiyan.template.system.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysBizSequenceMapper extends BaseMapper<SysBizSequence> {
    @Select("select * from sys_biz_sequence where sequence_code = #{sequenceCode} for update")
    SysBizSequence selectByCodeForUpdate(String sequenceCode);
}
