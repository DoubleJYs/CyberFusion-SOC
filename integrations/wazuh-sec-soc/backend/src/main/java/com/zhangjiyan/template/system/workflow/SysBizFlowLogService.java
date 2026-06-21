package com.zhangjiyan.template.system.workflow;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.system.workflow.dto.BizFlowLogCreateRequest;
import com.zhangjiyan.template.system.workflow.vo.SysBizFlowLogVO;

import java.time.LocalDateTime;

public interface SysBizFlowLogService extends IService<SysBizFlowLog> {
    PageResult<SysBizFlowLogVO> pageLogs(long pageNum, long pageSize, String bizType, String bizId, String bizNo,
                                         String operatorName, LocalDateTime startTime, LocalDateTime endTime);

    SysBizFlowLogVO detail(Long id);

    SysBizFlowLogVO create(BizFlowLogCreateRequest request);
}
