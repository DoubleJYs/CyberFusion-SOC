package com.zhangjiyan.template.system.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.common.workflow.BizNoGenerator;
import com.zhangjiyan.template.common.workflow.BizNoRule;
import com.zhangjiyan.template.common.workflow.SequenceResetPolicy;
import com.zhangjiyan.template.system.workflow.dto.BizNoGenerateRequest;
import com.zhangjiyan.template.system.workflow.dto.BizSequenceCreateRequest;
import com.zhangjiyan.template.system.workflow.dto.BizSequenceUpdateRequest;
import com.zhangjiyan.template.system.workflow.vo.BizNoGenerateVO;
import com.zhangjiyan.template.system.workflow.vo.SysBizSequenceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SysBizSequenceServiceImpl extends ServiceImpl<SysBizSequenceMapper, SysBizSequence> implements SysBizSequenceService {

    @Override
    public PageResult<SysBizSequenceVO> pageSequences(long pageNum, long pageSize, String keyword, Integer enabled) {
        LambdaQueryWrapper<SysBizSequence> wrapper = new LambdaQueryWrapper<SysBizSequence>()
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(SysBizSequence::getSequenceCode, keyword)
                        .or()
                        .like(SysBizSequence::getSequenceName, keyword))
                .eq(enabled != null, SysBizSequence::getEnabled, enabled)
                .orderByDesc(SysBizSequence::getCreatedAt);
        Page<SysBizSequence> page = baseMapper.selectPage(Page.of(pageNum <= 0 ? 1 : pageNum, pageSize <= 0 ? 10 : pageSize), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional
    public SysBizSequenceVO create(BizSequenceCreateRequest request) {
        if (count(new LambdaQueryWrapper<SysBizSequence>().eq(SysBizSequence::getSequenceCode, request.sequenceCode())) > 0) {
            throw new BusinessException("编号规则编码已存在");
        }
        SysBizSequence sequence = new SysBizSequence();
        sequence.setSequenceCode(request.sequenceCode());
        apply(sequence, request.sequenceName(), request.prefix(), request.datePattern(), request.currentValue(),
                request.step(), request.length(), request.resetPolicy(), request.enabled(), request.remark());
        save(sequence);
        return toVO(sequence);
    }

    @Override
    @Transactional
    public SysBizSequenceVO update(Long id, BizSequenceUpdateRequest request) {
        SysBizSequence sequence = getById(id);
        if (sequence == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "编号规则不存在");
        }
        apply(sequence, request.sequenceName(), request.prefix(), request.datePattern(), request.currentValue(),
                request.step(), request.length(), request.resetPolicy(), request.enabled(), request.remark());
        updateById(sequence);
        return toVO(sequence);
    }

    @Override
    @Transactional
    public BizNoGenerateVO generate(BizNoGenerateRequest request) {
        SysBizSequence sequence = baseMapper.selectByCodeForUpdate(request.sequenceCode());
        if (sequence == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "编号规则不存在");
        }
        if (!Integer.valueOf(1).equals(sequence.getEnabled())) {
            throw new BusinessException("编号规则已停用");
        }
        LocalDate today = LocalDate.now();
        SequenceResetPolicy resetPolicy = parsePolicy(sequence.getResetPolicy());
        if (shouldReset(resetPolicy, sequence.getLastResetDate(), today)) {
            sequence.setCurrentValue(0L);
            sequence.setLastResetDate(today);
        }
        long nextValue = (sequence.getCurrentValue() == null ? 0 : sequence.getCurrentValue()) + sequence.getStep();
        sequence.setCurrentValue(nextValue);
        updateById(sequence);
        String bizNo = BizNoGenerator.generate(new BizNoRule(sequence.getPrefix(), sequence.getDatePattern(), sequence.getCurrentValue(),
                sequence.getStep(), sequence.getLength(), resetPolicy), nextValue, today);
        return new BizNoGenerateVO(sequence.getSequenceCode(), bizNo, sequence.getCurrentValue());
    }

    private void apply(SysBizSequence sequence, String name, String prefix, String datePattern, Long currentValue,
                       Integer step, Integer length, String resetPolicy, Integer enabled, String remark) {
        sequence.setSequenceName(name);
        sequence.setPrefix(prefix == null ? "" : prefix);
        sequence.setDatePattern(datePattern == null ? "yyyyMMdd" : datePattern);
        sequence.setCurrentValue(currentValue == null ? 0 : currentValue);
        sequence.setStep(step == null ? 1 : step);
        sequence.setLength(length == null ? 4 : length);
        sequence.setResetPolicy(parsePolicy(resetPolicy).name());
        sequence.setEnabled(enabled == null ? 1 : enabled);
        sequence.setRemark(remark);
        if (sequence.getLastResetDate() == null) {
            sequence.setLastResetDate(LocalDate.now());
        }
    }

    private boolean shouldReset(SequenceResetPolicy policy, LocalDate lastResetDate, LocalDate today) {
        if (lastResetDate == null) {
            return true;
        }
        return switch (policy) {
            case NEVER -> false;
            case DAILY -> !lastResetDate.equals(today);
            case MONTHLY -> lastResetDate.getYear() != today.getYear() || lastResetDate.getMonthValue() != today.getMonthValue();
            case YEARLY -> lastResetDate.getYear() != today.getYear();
        };
    }

    private SequenceResetPolicy parsePolicy(String resetPolicy) {
        try {
            return SequenceResetPolicy.valueOf(resetPolicy == null ? "NEVER" : resetPolicy);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("resetPolicy 仅支持 NEVER / DAILY / MONTHLY / YEARLY");
        }
    }

    private SysBizSequenceVO toVO(SysBizSequence sequence) {
        return new SysBizSequenceVO(sequence.getId(), sequence.getSequenceCode(), sequence.getSequenceName(), sequence.getPrefix(),
                sequence.getDatePattern(), sequence.getCurrentValue(), sequence.getStep(), sequence.getLength(), sequence.getResetPolicy(),
                sequence.getLastResetDate(), sequence.getEnabled(), sequence.getRemark(), sequence.getCreatedAt(), sequence.getUpdatedAt());
    }
}
