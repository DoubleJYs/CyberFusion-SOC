package com.zhangjiyan.template.soc.workspace;

import com.zhangjiyan.template.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soc/user-workspaces")
public class SocUserWorkspaceController {

    private final SocUserWorkspaceService service;

    @GetMapping
    @PreAuthorize("hasRole('admin') or hasAuthority('soc:dashboard:view')")
    public ApiResult<List<SocUserWorkspaceService.UserWorkspaceCard>> cards() {
        return ApiResult.ok(service.cards());
    }
}
