package com.zhangjiyan.template.system.workflow;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.system.workflow.dto.BizNoGenerateRequest;
import com.zhangjiyan.template.system.workflow.dto.BizSequenceCreateRequest;
import com.zhangjiyan.template.system.workflow.dto.BizSequenceUpdateRequest;
import com.zhangjiyan.template.system.workflow.vo.BizNoGenerateVO;
import com.zhangjiyan.template.system.workflow.vo.SysBizSequenceVO;

public interface SysBizSequenceService extends IService<SysBizSequence> {
    PageResult<SysBizSequenceVO> pageSequences(long pageNum, long pageSize, String keyword, Integer enabled);

    SysBizSequenceVO create(BizSequenceCreateRequest request);

    SysBizSequenceVO update(Long id, BizSequenceUpdateRequest request);

    BizNoGenerateVO generate(BizNoGenerateRequest request);
}
