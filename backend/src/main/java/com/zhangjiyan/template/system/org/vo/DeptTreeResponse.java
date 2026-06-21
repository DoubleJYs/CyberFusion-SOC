package com.zhangjiyan.template.system.org.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record DeptTreeResponse(
        Long id,
        Long parentId,
        String deptName,
        String deptCode,
        String leader,
        String phone,
        Integer sort,
        Integer status,
        LocalDateTime createdAt,
        List<DeptTreeResponse> children
) {
    public static DeptTreeResponse leaf(Long id, Long parentId, String deptName, String deptCode, String leader,
                                        String phone, Integer sort, Integer status, LocalDateTime createdAt) {
        return new DeptTreeResponse(id, parentId, deptName, deptCode, leader, phone, sort, status, createdAt, new ArrayList<>());
    }

    public DeptTreeResponse withChildren(List<DeptTreeResponse> children) {
        return new DeptTreeResponse(id, parentId, deptName, deptCode, leader, phone, sort, status, createdAt, children);
    }
}
