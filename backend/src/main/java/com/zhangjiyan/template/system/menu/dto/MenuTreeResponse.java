package com.zhangjiyan.template.system.menu.dto;

import java.util.ArrayList;
import java.util.List;

public record MenuTreeResponse(
        Long id,
        Long parentId,
        String name,
        String path,
        String component,
        String icon,
        String type,
        String permission,
        Integer sort,
        Integer visible,
        Integer status,
        List<MenuTreeResponse> children
) {
    public MenuTreeResponse withChildren(List<MenuTreeResponse> nextChildren) {
        return new MenuTreeResponse(id, parentId, name, path, component, icon, type, permission, sort, visible, status, nextChildren);
    }

    public static MenuTreeResponse leaf(Long id, Long parentId, String name, String path, String component, String icon,
                                        String type, String permission, Integer sort, Integer visible, Integer status) {
        return new MenuTreeResponse(id, parentId, name, path, component, icon, type, permission, sort, visible, status, new ArrayList<>());
    }
}
