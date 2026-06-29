package com.example.sys.controller;

import com.example.common.result.Result;
import com.example.common.security.RequiresPermission;
import com.example.sys.domain.SysMenu;
import com.example.sys.dto.MenuDTO;
import com.example.sys.dto.MenuTreeNode;
import com.example.sys.service.MenuService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜单管理 Controller。
 */
@RestController
@RequestMapping("/sys/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @RequiresPermission("sys:menu:list")
    @GetMapping("/tree")
    public Result<List<MenuTreeNode>> tree() {
        return Result.ok(menuService.getTree());
    }

    @RequiresPermission("sys:menu:list")
    @GetMapping("/{id}")
    public Result<SysMenu> get(@PathVariable Long id) {
        return Result.ok(menuService.getById(id));
    }

    @RequiresPermission("sys:menu:add")
    @PostMapping
    public Result<Long> create(@RequestBody @Valid MenuDTO dto) {
        return Result.ok(menuService.create(dto));
    }

    @RequiresPermission("sys:menu:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MenuDTO dto) {
        menuService.update(id, dto);
        return Result.ok();
    }

    @RequiresPermission("sys:menu:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.ok();
    }
}
