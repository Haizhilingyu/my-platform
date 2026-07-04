package com.example.sys.controller;

import com.example.common.exception.BizException;
import com.example.common.result.Result;
import com.example.common.security.CurrentUser;
import com.example.common.security.JwtUtil;
import com.example.sys.domain.SysMenu;
import com.example.sys.domain.SysUser;
import com.example.sys.dto.LoginDTO;
import com.example.sys.dto.LoginVO;
import com.example.sys.dto.MenuTreeNode;
import com.example.sys.dto.UserVO;
import com.example.sys.service.MenuService;
import com.example.sys.service.PermissionService;
import com.example.sys.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 Controller。负责登录、获取当前用户信息（权限、菜单）。
 */
@Tag(name = "认证", description = "登录、获取当前用户权限和菜单")
@RestController
@RequestMapping("/sys/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PermissionService permissionService;
    private final MenuService menuService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        SysUser user = userService.getEntityByUsername(dto.getUsername());
        if (user.getStatus() != 1) {
            throw new BizException(403, "用户已被禁用");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }

        List<String> roles = List.copyOf(permissionService.getUserRoleCodes(user.getId()));
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getUnitId(), roles);

        UserVO vo = UserVO.of(user);
        return Result.ok(new LoginVO(token, "Bearer", vo));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<UserVO> me() {
        Long userId = CurrentUser.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return Result.ok(userService.getById(userId));
    }

    @Operation(summary = "获取当前用户权限列表")
    @GetMapping("/permissions")
    public Result<Set<String>> permissions() {
        return Result.ok(CurrentUser.getPermissions());
    }

    @Operation(summary = "获取当前用户菜单树")
    @GetMapping("/menus")
    public Result<List<MenuTreeNode>> menus() {
        Long userId = CurrentUser.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        List<SysMenu> menus = permissionService.getUserMenus(userId);
        return Result.ok(MenuService.buildTree(menus));
    }
}
