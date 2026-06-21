package com.zhangjiyan.template.soc.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.soc.dto.SocPageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SocNotificationService {

    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(
            "low", 1,
            "medium", 2,
            "high", 3,
            "critical", 4
    );

    private final SocNotificationChannelMapper channelMapper;
    private final SocNotificationLogMapper logMapper;

    public List<SocNotificationChannel> channels() {
        return channelMapper.selectList(new LambdaQueryWrapper<SocNotificationChannel>()
                .orderByDesc(SocNotificationChannel::getEnabled)
                .orderByAsc(SocNotificationChannel::getId));
    }

    public PageResult<SocNotificationLog> logs(SocPageRequest request) {
        LambdaQueryWrapper<SocNotificationLog> wrapper = new LambdaQueryWrapper<SocNotificationLog>()
                .and(notBlank(request.keyword()), q -> q.like(SocNotificationLog::getTitle, request.keyword())
                        .or().like(SocNotificationLog::getContent, request.keyword())
                        .or().like(SocNotificationLog::getTarget, request.keyword()))
                .eq(notBlank(request.status()), SocNotificationLog::getStatus, request.status())
                .orderByDesc(SocNotificationLog::getCreatedAt);
        return PageResult.from(logMapper.selectPage(Page.of(request.pageNum(), request.pageSize()), wrapper));
    }

    @Transactional
    public SocNotificationLog test(Long channelId) {
        SocNotificationChannel channel = channelMapper.selectById(channelId);
        if (channel == null) {
            throw new BusinessException("通知通道不存在");
        }
        return writeLog(channel, "test", "medium", "notification_channel", channel.getId(),
                "SOC 通知通道测试", "这是一条安全运营平台通知通道 dry-run 测试记录。");
    }

    @Transactional
    public void dispatch(String eventType, String severity, String bizType, Long bizId, String title, String content) {
        List<SocNotificationChannel> channels = channelMapper.selectList(new LambdaQueryWrapper<SocNotificationChannel>()
                .eq(SocNotificationChannel::getEnabled, 1));
        for (SocNotificationChannel channel : channels) {
            if (supportsEvent(channel, eventType) && supportsSeverity(channel, severity)) {
                writeLog(channel, eventType, severity, bizType, bizId, title, content);
            }
        }
    }

    private SocNotificationLog writeLog(SocNotificationChannel channel, String eventType, String severity,
                                        String bizType, Long bizId, String title, String content) {
        LocalDateTime now = LocalDateTime.now();
        String status = "dry_run".equalsIgnoreCase(channel.getSendMode()) ? "DRY_RUN" : "PENDING";

        SocNotificationLog log = new SocNotificationLog();
        log.setChannelId(channel.getId());
        log.setChannelType(channel.getChannelType());
        log.setEventType(eventType);
        log.setSeverity(severity);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setTitle(limit(title, 255));
        log.setContent(limit(content, 1000));
        log.setTarget(channel.getTarget());
        log.setStatus(status);
        log.setSentAt(now);
        logMapper.insert(log);

        channel.setLastStatus(status);
        channel.setLastSentAt(now);
        channelMapper.updateById(channel);
        return log;
    }

    private boolean supportsEvent(SocNotificationChannel channel, String eventType) {
        String trigger = channel.getTriggerEvent();
        return trigger == null || trigger.isBlank() || "*".equals(trigger) || trigger.equalsIgnoreCase(eventType);
    }

    private boolean supportsSeverity(SocNotificationChannel channel, String severity) {
        int actual = SEVERITY_ORDER.getOrDefault(normalize(severity), 0);
        int min = SEVERITY_ORDER.getOrDefault(normalize(channel.getMinSeverity()), 0);
        return actual >= min;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
