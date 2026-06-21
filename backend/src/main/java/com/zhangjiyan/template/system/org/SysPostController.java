package com.zhangjiyan.template.system.org;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhangjiyan.template.common.dto.PageResult;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.system.org.dto.PostRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Post", description = "岗位管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/posts")
public class SysPostController {

    private final SysPostMapper postMapper;

    @Operation(summary = "岗位分页")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:post:view')")
    public ApiResult<PageResult<SysPost>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer status) {
        Page<SysPost> page = postMapper.selectPage(Page.of(pageNum, pageSize), new LambdaQueryWrapper<SysPost>()
                .and(keyword != null && !keyword.isBlank(), q -> q.like(SysPost::getPostName, keyword).or().like(SysPost::getPostCode, keyword))
                .eq(status != null, SysPost::getStatus, status)
                .orderByAsc(SysPost::getSort));
        return ApiResult.ok(PageResult.from(page));
    }

    @Operation(summary = "新增岗位")
    @PostMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:post:create')")
    public ApiResult<Void> create(@Valid @RequestBody PostRequest request) {
        ensureCodeUnique(request.postCode(), null);
        postMapper.insert(toEntity(new SysPost(), request));
        return ApiResult.ok();
    }

    @Operation(summary = "编辑岗位")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:post:update')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody PostRequest request) {
        SysPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "岗位不存在");
        }
        ensureCodeUnique(request.postCode(), id);
        postMapper.updateById(toEntity(post, request));
        return ApiResult.ok();
    }

    @Operation(summary = "删除岗位")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:post:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        postMapper.deleteById(id);
        return ApiResult.ok();
    }

    private void ensureCodeUnique(String postCode, Long id) {
        Long count = postMapper.selectCount(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getPostCode, postCode)
                .ne(id != null, SysPost::getId, id));
        if (count > 0) {
            throw new BusinessException("岗位编码已存在");
        }
    }

    private SysPost toEntity(SysPost post, PostRequest request) {
        post.setPostCode(request.postCode());
        post.setPostName(request.postName());
        post.setSort(request.sort() == null ? 0 : request.sort());
        post.setStatus(request.status() == null ? 1 : request.status());
        post.setRemark(request.remark());
        return post;
    }
}
