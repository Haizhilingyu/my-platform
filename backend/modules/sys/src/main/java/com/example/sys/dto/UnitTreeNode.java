package com.example.sys.dto;

import com.example.sys.domain.SysUnit;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 单位树节点 VO。 */
@Data
public class UnitTreeNode {

    private Long id;
    private Long parentId;
    private String unitCode;
    private String unitName;
    private Integer sort;
    private Integer status;
    private String remark;
    private List<UnitTreeNode> children = new ArrayList<>();

    public static UnitTreeNode of(SysUnit unit) {
        UnitTreeNode node = new UnitTreeNode();
        node.setId(unit.getId());
        node.setParentId(unit.getParentId());
        node.setUnitCode(unit.getUnitCode());
        node.setUnitName(unit.getUnitName());
        node.setSort(unit.getSort());
        node.setStatus(unit.getStatus());
        node.setRemark(unit.getRemark());
        return node;
    }
}
