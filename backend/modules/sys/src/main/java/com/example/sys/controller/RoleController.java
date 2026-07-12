package com.example.sys.controller;

import com.example.common.result.Result;
import com.example.common.security.RequiresPermission;
import com.example.sys.domain.SysRole;
import com.example.sys.dto.RoleDTO;
import com.example.sys.service.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 角色管理 Controller。 */
@RestController
@RequestMapping("/api/sys/role")
@RequiredArgsConstructor
@Validated
public class RoleController {

  private final RoleService roleService;

  @RequiresPermission("sys:role:list")
  @GetMapping
  public Result<List<SysRole>> list() {
    return Result.ok(roleService.findAll());
  }

  @RequiresPermission("sys:role:list")
  @GetMapping("/{id}")
  public Result<SysRole> get(@PathVariable Long id) {
    return Result.ok(roleService.getById(id));
  }

  @RequiresPermission("sys:role:add")
  @PostMapping
  public Result<Long> create(@RequestBody @Valid RoleDTO dto) {
    return Result.ok(roleService.create(dto));
  }

  @RequiresPermission("sys:role:edit")
  @PutMapping("/{id}")
  public Result<Void> update(@PathVariable Long id, @RequestBody @Valid RoleDTO dto) {
    roleService.update(id, dto);
    return Result.ok();
  }

  @RequiresPermission("sys:role:delete")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    roleService.delete(id);
    return Result.ok();
  }

  @RequiresPermission("sys:role:perm")
  @PostMapping("/{id}/menus")
  public Result<Void> assignMenus(
      @PathVariable Long id,
      @RequestBody @NotEmpty(message = "{validation.controller.menuIds.notEmpty}")
          List<Long> menuIds) {
    roleService.assignMenus(id, menuIds);
    return Result.ok();
  }

  @RequiresPermission("sys:role:perm")
  @GetMapping("/{id}/menus")
  public Result<List<Long>> getRoleMenus(@PathVariable Long id) {
    return Result.ok(roleService.getRoleMenuIds(id));
  }

  @RequiresPermission("sys:role:perm")
  @PutMapping("/{id}/data-scope")
  public Result<Void> saveDataScope(@PathVariable Long id, @RequestBody List<Long> unitIds) {
    roleService.saveCustomUnits(id, unitIds);
    return Result.ok();
  }

  @RequiresPermission("sys:role:perm")
  @GetMapping("/{id}/data-scope")
  public Result<List<Long>> getDataScope(@PathVariable Long id) {
    return Result.ok(roleService.getCustomUnitIds(id));
  }
}
