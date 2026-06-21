package com.zhangjiyan.template.system.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.common.security.SecurityUtils;
import com.zhangjiyan.template.system.workflow.dto.BizFlowLogCreateRequest;
import com.zhangjiyan.template.system.workflow.vo.SysBizFlowLogVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SysBizFlowLogServiceImpl extends ServiceImpl<SysBizFlowLogMapper, SysBizFlowLog> implements SysBizFlowLogService {

    @Override
    public PageResult<SysBizFlowLogVO> pageLogs(long pageNum, long pageSize, String bizType, String bizId, String bizNo,
                                                String operatorName, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysBizFlowLog> wrapper = new LambdaQueryWrapper<SysBizFlowLog>()
                .eq(bizType != null && !bizType.isBlank(), SysBizFlowLog::getBizType, bizType)
                .eq(bizId != null && !bizId.isBlank(), SysBizFlowLog::getBizId, bizId)
                .eq(bizNo != null && !bizNo.isBlank(), SysBizFlowLog::getBizNo, bizNo)
                .like(operatorName != null && !operatorName.isBlank(), SysBizFlowLog::getOperatorName, operatorName)
                .ge(startTime != null, SysBizFlowLog::getCreatedAt, startTime)
                .le(endTime != null, SysBizFlowLog::getCreatedAt, endTime)
                .orderByDesc(SysBizFlowLog::getCreatedAt);
        Page<SysBizFlowLog> page = baseMapper.selectPage(Page.of(pageNum <= 0 ? 1 : pageNum, pageSize <= 0 ? 10 : pageSize), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SysBizFlowLogVO detail(Long id) {
        SysBizFlowLog log = getById(id);
        if (log == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "业务流程日志不存在");
        }
        return toVO(log);
    }

    @Override
    public SysBizFlowLogVO create(BizFlowLogCreateRequest request) {
        SysBizFlowLog log = new SysBizFlowLog();
        log.setBizType(request.bizType());
        log.setBizId(request.bizId());
        log.setBizNo(request.bizNo());
        log.setFromStatus(request.fromStatus());
        log.setToStatus(request.toStatus());
        log.setAction(request.action());
        log.setOperatorId(request.operatorId());
        log.setOperatorName(request.operatorName());
        SecurityUtils.currentUser().ifPresent(loginUser -> {
            if (log.getOperatorId() == null) {
                log.setOperatorId(loginUser.userId());
            }
            if (log.getOperatorName() == null || log.getOperatorName().isBlank()) {
                log.setOperatorName(loginUser.nickname());
            }
        });
        log.setReason(request.reason());
        log.setRemark(request.remark());
        save(log);
        return toVO(log);
    }

    private SysBizFlowLogVO toVO(SysBizFlowLog log) {
        return new SysBizFlowLogVO(log.getId(), log.getBizType(), log.getBizId(), log.getBizNo(), log.getFromStatus(),
                log.getToStatus(), log.getAction(), log.getOperatorId(), log.getOperatorName(), log.getReason(),
                log.getRemark(), log.getCreatedAt());
    }
}
