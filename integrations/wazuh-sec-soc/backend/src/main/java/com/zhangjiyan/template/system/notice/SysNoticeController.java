package com.zhangjiyan.template.system.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.system.notice.dto.NoticeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Notice", description = "系统通知公告")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/notices")
public class SysNoticeController {

    private final SysNoticeMapper noticeMapper;

    @Operation(summary = "通知公告分页")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:notice:view')")
    public ApiResult<PageResult<SysNotice>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String noticeType,
                                                 @RequestParam(required = false) Integer status) {
        Page<SysNotice> page = noticeMapper.selectPage(Page.of(pageNum, pageSize), baseQuery(keyword, noticeType, status)
                .orderByDesc(SysNotice::getPinned)
                .orderByDesc(SysNotice::getPublishAt)
                .orderByDesc(SysNotice::getCreatedAt));
        return ApiResult.ok(PageResult.from(page));
    }

    @Operation(summary = "当前有效通知公告")
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<List<SysNotice>> active(@RequestParam(defaultValue = "5") long limit,
                                             @RequestParam(required = false) String noticeType) {
        LocalDateTime now = LocalDateTime.now();
        List<SysNotice> notices = noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, 1)
                .eq(noticeType != null && !noticeType.isBlank(), SysNotice::getNoticeType, noticeType)
                .le(SysNotice::getPublishAt, now)
                .and(query -> query.isNull(SysNotice::getExpireAt).or().gt(SysNotice::getExpireAt, now))
                .orderByDesc(SysNotice::getPinned)
                .orderByDesc(SysNotice::getPublishAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
        return ApiResult.ok(notices);
    }

    @Operation(summary = "通知公告详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:notice:view')")
    public ApiResult<SysNotice> detail(@PathVariable Long id) {
        return ApiResult.ok(requireNotice(id));
    }

    @Operation(summary = "新增通知公告")
    @PostMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:notice:create')")
    public ApiResult<Void> create(@Valid @RequestBody NoticeRequest request) {
        validateStatus(request.status());
        validateTimeRange(request);
        noticeMapper.insert(toEntity(new SysNotice(), request));
        return ApiResult.ok();
    }

    @Operation(summary = "编辑通知公告")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:notice:update')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        SysNotice notice = requireNotice(id);
        validateStatus(request.status());
        validateTimeRange(request);
        noticeMapper.updateById(toEntity(notice, request));
        return ApiResult.ok();
    }

    @Operation(summary = "删除通知公告")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:notice:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        requireNotice(id);
        noticeMapper.deleteById(id);
        return ApiResult.ok();
    }

    private SysNotice requireNotice(Long id) {
        SysNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知公告不存在");
        }
        return notice;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "状态只能是 0 或 1");
        }
    }

    private LambdaQueryWrapper<SysNotice> baseQuery(String keyword, String noticeType, Integer status) {
        return new LambdaQueryWrapper<SysNotice>()
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(SysNotice::getNoticeTitle, keyword)
                        .or()
                        .like(SysNotice::getNoticeContent, keyword))
                .eq(noticeType != null && !noticeType.isBlank(), SysNotice::getNoticeType, noticeType)
                .eq(status != null, SysNotice::getStatus, status);
    }

    private void validateTimeRange(NoticeRequest request) {
        if (request.publishAt() != null && request.expireAt() != null && !request.expireAt().isAfter(request.publishAt())) {
            throw new BusinessException("失效时间必须晚于发布时间");
        }
    }

    private SysNotice toEntity(SysNotice notice, NoticeRequest request) {
        notice.setNoticeTitle(request.noticeTitle());
        notice.setNoticeType(request.noticeType());
        notice.setNoticeContent(request.noticeContent());
        notice.setPinned(request.pinned() == null ? 0 : request.pinned());
        notice.setPublishAt(request.publishAt() == null ? LocalDateTime.now() : request.publishAt());
        notice.setExpireAt(request.expireAt());
        notice.setStatus(request.status() == null ? 1 : request.status());
        notice.setRemark(request.remark());
        return notice;
    }
}
