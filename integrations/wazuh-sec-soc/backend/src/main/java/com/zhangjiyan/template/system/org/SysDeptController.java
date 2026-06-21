package com.zhangjiyan.template.system.org;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangjiyan.template.common.exception.BusinessException;
import com.zhangjiyan.template.common.result.ApiResult;
import com.zhangjiyan.template.common.result.ResultCode;
import com.zhangjiyan.template.system.org.dto.DeptRequest;
import com.zhangjiyan.template.system.org.vo.DeptTreeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Dept", description = "部门组织架构")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/depts")
public class SysDeptController {

    private final SysDeptMapper deptMapper;

    @Operation(summary = "部门树查询")
    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dept:view')")
    public ApiResult<List<DeptTreeResponse>> tree(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer status) {
        List<SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .and(keyword != null && !keyword.isBlank(), q -> q.like(SysDept::getDeptName, keyword).or().like(SysDept::getDeptCode, keyword))
                .eq(status != null, SysDept::getStatus, status)
                .orderByAsc(SysDept::getSort));
        return ApiResult.ok(tree(depts));
    }

    @Operation(summary = "新增部门")
    @PostMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dept:create')")
    public ApiResult<Void> create(@Valid @RequestBody DeptRequest request) {
        ensureCodeUnique(request.deptCode(), null);
        deptMapper.insert(toEntity(new SysDept(), request));
        return ApiResult.ok();
    }

    @Operation(summary = "编辑部门")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dept:update')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody DeptRequest request) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }
        ensureCodeUnique(request.deptCode(), id);
        deptMapper.updateById(toEntity(dept, request));
        return ApiResult.ok();
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin') or hasAuthority('system:dept:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("请先删除下级部门");
        }
        deptMapper.deleteById(id);
        return ApiResult.ok();
    }

    private void ensureCodeUnique(String deptCode, Long id) {
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getDeptCode, deptCode)
                .ne(id != null, SysDept::getId, id));
        if (count > 0) {
            throw new BusinessException("部门编码已存在");
        }
    }

    private SysDept toEntity(SysDept dept, DeptRequest request) {
        dept.setParentId(request.parentId() == null ? 0L : request.parentId());
        dept.setDeptName(request.deptName());
        dept.setDeptCode(request.deptCode());
        dept.setLeader(request.leader());
        dept.setPhone(request.phone());
        dept.setSort(request.sort() == null ? 0 : request.sort());
        dept.setStatus(request.status() == null ? 1 : request.status());
        return dept;
    }

    private List<DeptTreeResponse> tree(List<SysDept> depts) {
        Map<Long, List<SysDept>> byParent = depts.stream().collect(Collectors.groupingBy(SysDept::getParentId));
        return children(0L, byParent);
    }

    private List<DeptTreeResponse> children(Long parentId, Map<Long, List<SysDept>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparing(SysDept::getSort, Comparator.nullsLast(Integer::compareTo)))
                .map(dept -> DeptTreeResponse.leaf(dept.getId(), dept.getParentId(), dept.getDeptName(), dept.getDeptCode(),
                        dept.getLeader(), dept.getPhone(), dept.getSort(), dept.getStatus(), dept.getCreatedAt())
                        .withChildren(children(dept.getId(), byParent)))
                .toList();
    }
}
