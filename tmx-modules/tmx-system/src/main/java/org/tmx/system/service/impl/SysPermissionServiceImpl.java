package org.tmx.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import org.tmx.common.core.constant.SystemConstants;
import org.tmx.common.core.service.PermissionService;
import org.tmx.common.core.utils.StreamUtils;
import org.tmx.common.satoken.utils.LoginHelper;
import org.tmx.system.api.domain.RoleDTO;
import org.tmx.system.service.ISysMenuService;
import org.tmx.system.service.ISysPermissionService;
import org.tmx.system.service.ISysRoleService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 汇总用户的角色标识、菜单权限及各接口可用的数据范围角色。
 */
@RequiredArgsConstructor
@Service
public class SysPermissionServiceImpl implements ISysPermissionService, PermissionService {

    private final ISysRoleService roleService;
    private final ISysMenuService menuService;

    /**
     * 获取用户的角色标识；超级管理员使用固定角色标识。
     *
     * @param userId 用户主键
     * @return 用户可用的角色标识集合
     */
    @Override
    public Set<String> getRolePermission(Long userId) {
        Set<String> roles = new HashSet<>();
        // 管理员拥有所有权限
        if (LoginHelper.isSuperAdmin(userId)) {
            roles.add(SystemConstants.SUPER_ADMIN_ROLE_KEY);
        } else {
            roles.addAll(roleService.selectRolePermissionByUserId(userId));
        }
        return roles;
    }

    /**
     * 获取菜单与按钮权限；超级管理员拥有通配权限。
     *
     * @param userId 用户主键
     * @return 用户可用的权限标识集合
     */
    @Override
    public Set<String> getMenuPermission(Long userId) {
        Set<String> perms = new HashSet<>();
        // 管理员拥有所有权限
        if (LoginHelper.isSuperAdmin(userId)) {
            perms.add("*:*:*");
        } else {
            perms.addAll(menuService.selectMenuPermsByUserId(userId));
        }
        return perms;
    }

    /**
     * 按权限标识汇总具备数据权限的角色集合。
     *
     * @param roles 角色传输对象列表
     * @return key 为权限标识、value 为拥有该权限的角色主键列表
     */
    @Override
    public Map<String, List<Long>> getDataScopeRoleMap(List<RoleDTO> roles) {
        if (CollUtil.isEmpty(roles)) {
            return Map.of();
        }
        List<Long> roleIds = StreamUtils.toList(roles, RoleDTO::getRoleId);
        Map<Long, Set<String>> permsRoleIds = menuService.selectMenuPermsByRoleIds(roleIds);
        Map<String, List<Long>> rolePermsMap = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<String>> rolePermissions : permsRoleIds.entrySet()) {
            Long roleId = rolePermissions.getKey();
            for (String permission : rolePermissions.getValue()) {
                rolePermsMap.computeIfAbsent(permission, key -> new ArrayList<>()).add(roleId);
            }
        }
        return rolePermsMap;
    }
}
