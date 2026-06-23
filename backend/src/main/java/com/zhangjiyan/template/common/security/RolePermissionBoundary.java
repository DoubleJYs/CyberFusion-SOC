package com.zhangjiyan.template.common.security;

import java.util.List;
import java.util.Set;

public final class RolePermissionBoundary {

    private static final Set<String> ELEVATED_ROLES = Set.of(
            "super_admin", "admin", "platform_admin",
            "security_admin", "security_engineer",
            "security_analyst", "analyst", "auditor"
    );
    private static final Set<String> SIMPLE_ROLES = Set.of("employee", "ops", "user", "customer", "demo");
    private static final Set<String> SIMPLE_DENIED_PREFIXES = Set.of("soc:", "system:");
    private static final Set<String> SIMPLE_DENIED_PERMISSIONS = Set.of("dashboard:view");

    private RolePermissionBoundary() {
    }

    public static List<String> filterPermissions(List<String> roles, List<String> permissions) {
        if (!isSimpleOnly(roles)) {
            return permissions;
        }
        return permissions.stream()
                .filter(RolePermissionBoundary::allowedForSimpleRole)
                .distinct()
                .toList();
    }

    public static boolean menuAllowed(List<String> roles, String permission, String path, List<String> effectivePermissions) {
        if (!isSimpleOnly(roles)) {
            return true;
        }
        if (deniedSimplePath(path)) {
            return false;
        }
        if (permission == null || permission.isBlank()) {
            return true;
        }
        return effectivePermissions.contains(permission);
    }

    private static boolean isSimpleOnly(List<String> roles) {
        boolean simple = roles.stream().anyMatch(SIMPLE_ROLES::contains);
        boolean elevated = roles.stream().anyMatch(ELEVATED_ROLES::contains);
        return simple && !elevated;
    }

    private static boolean allowedForSimpleRole(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        if (SIMPLE_DENIED_PERMISSIONS.contains(permission)) {
            return false;
        }
        return SIMPLE_DENIED_PREFIXES.stream().noneMatch(permission::startsWith);
    }

    private static boolean deniedSimplePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.equals("/soc")
                || path.startsWith("/soc/")
                || path.equals("/system")
                || path.startsWith("/system/")
                || path.equals("/dashboard");
    }
}
