package com.example.sys.service;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.sys.domain.SysUnit;
import com.example.sys.dto.UnitDTO;
import com.example.sys.dto.UnitTreeNode;
import com.example.sys.repository.SysUnitRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单位服务。
 */
@Service
@RequiredArgsConstructor
public class UnitService {

    private final SysUnitRepository unitRepository;

    @Transactional(readOnly = true)
    public List<UnitTreeNode> getTree() {
        List<SysUnit> all = unitRepository.findAll();
        return buildTree(all);
    }

    @Transactional(readOnly = true)
    public SysUnit getById(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("单位", id));
    }

    @Transactional
    public Long create(UnitDTO dto) {
        if (unitRepository.existsByUnitCode(dto.getUnitCode())) {
            throw new BizException("单位编码已存在: " + dto.getUnitCode());
        }
        SysUnit unit = SysUnit.builder()
                .parentId(dto.getParentId())
                .unitCode(dto.getUnitCode())
                .unitName(dto.getUnitName())
                .sort(dto.getSort() != null ? dto.getSort() : 0)
                .status(dto.getStatus() != null ? dto.getStatus() : 1)
                .remark(dto.getRemark())
                .build();
        return unitRepository.save(unit).getId();
    }

    @Transactional
    public void update(Long id, UnitDTO dto) {
        SysUnit unit = getById(id);
        if (dto.getParentId() != null) {
            if (dto.getParentId().equals(id)) {
                throw new BizException("上级单位不能是自己");
            }
            unit.setParentId(dto.getParentId());
        }
        if (dto.getUnitName() != null) {
            unit.setUnitName(dto.getUnitName());
        }
        if (dto.getSort() != null) {
            unit.setSort(dto.getSort());
        }
        if (dto.getStatus() != null) {
            unit.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            unit.setRemark(dto.getRemark());
        }
        unitRepository.save(unit);
    }

    @Transactional
    public void delete(Long id) {
        SysUnit unit = getById(id);
        List<SysUnit> children = unitRepository.findByParentId(id);
        if (!children.isEmpty()) {
            throw new BizException("存在子单位，无法删除");
        }
        unitRepository.delete(unit);
    }

    public static List<UnitTreeNode> buildTree(List<SysUnit> units) {
        List<UnitTreeNode> nodes = units.stream().map(UnitTreeNode::of).toList();
        Map<Long, List<UnitTreeNode>> parentIdMap = nodes.stream()
                .collect(Collectors.groupingBy(n -> n.getParentId() == null ? 0L : n.getParentId()));

        List<UnitTreeNode> roots = parentIdMap.getOrDefault(0L, new ArrayList<>());
        roots.addAll(nodes.stream().filter(n -> n.getParentId() == null).toList());

        for (UnitTreeNode node : nodes) {
            List<UnitTreeNode> children = parentIdMap.get(node.getId());
            if (children != null) {
                children.sort(Comparator.comparingInt(UnitTreeNode::getSort));
                node.setChildren(children);
            }
        }

        roots.sort(Comparator.comparingInt(UnitTreeNode::getSort));
        return roots;
    }
}
