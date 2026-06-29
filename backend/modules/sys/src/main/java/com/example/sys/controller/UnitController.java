package com.example.sys.controller;

import com.example.common.result.Result;
import com.example.common.security.RequiresPermission;
import com.example.sys.domain.SysUnit;
import com.example.sys.dto.UnitDTO;
import com.example.sys.dto.UnitTreeNode;
import com.example.sys.service.UnitService;
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
 * 单位管理 Controller。
 */
@RestController
@RequestMapping("/sys/unit")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService unitService;

    @RequiresPermission("sys:unit:list")
    @GetMapping("/tree")
    public Result<List<UnitTreeNode>> tree() {
        return Result.ok(unitService.getTree());
    }

    @RequiresPermission("sys:unit:list")
    @GetMapping("/{id}")
    public Result<SysUnit> get(@PathVariable Long id) {
        return Result.ok(unitService.getById(id));
    }

    @RequiresPermission("sys:unit:add")
    @PostMapping
    public Result<Long> create(@RequestBody @Valid UnitDTO dto) {
        return Result.ok(unitService.create(dto));
    }

    @RequiresPermission("sys:unit:edit")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UnitDTO dto) {
        unitService.update(id, dto);
        return Result.ok();
    }

    @RequiresPermission("sys:unit:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        unitService.delete(id);
        return Result.ok();
    }
}
