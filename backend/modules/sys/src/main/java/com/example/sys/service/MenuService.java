package com.example.sys.service;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.common.i18n.Messages;
import com.example.sys.domain.SysMenu;
import com.example.sys.dto.MenuDTO;
import com.example.sys.dto.MenuTreeNode;
import com.example.sys.repository.SysMenuRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuService {

  private final SysMenuRepository menuRepository;

  @Transactional(readOnly = true)
  public List<MenuTreeNode> getTree() {
    return buildLocalizedTree(menuRepository.findAll());
  }

  @Transactional(readOnly = true)
  public List<MenuTreeNode> buildLocalizedTree(List<SysMenu> menus) {
    if (menus.isEmpty()) {
      return new ArrayList<>();
    }
    List<MenuTreeNode> nodes = menus.stream().map(MenuTreeNode::of).toList();
    List<MenuTreeNode> tree = buildTreeFromNodes(nodes);
    for (MenuTreeNode node : tree) {
      localizeNode(node);
    }
    return tree;
  }

  private void localizeNode(MenuTreeNode node) {
    String key = "sys.menu." + node.getId() + ".name";
    String translated = Messages.get(key);
    if (translated != null && !key.equals(translated)) {
      node.setMenuName(translated);
    }
    if (node.getChildren() != null) {
      for (MenuTreeNode child : node.getChildren()) {
        localizeNode(child);
      }
    }
  }

  @Transactional(readOnly = true)
  public SysMenu getById(Long id) {
    return menuRepository
        .findById(id)
        .orElseThrow(
            () ->
                NotFoundException.i18n(
                    "error.resource.not.found", Messages.get("resource.menu"), id));
  }

  @Transactional
  public Long create(MenuDTO dto) {
    SysMenu menu =
        SysMenu.builder()
            .parentId(dto.getParentId())
            .menuName(dto.getMenuName())
            .menuType(dto.getMenuType())
            .path(dto.getPath())
            .component(dto.getComponent())
            .permission(dto.getPermission())
            .icon(dto.getIcon())
            .sort(dto.getSort() != null ? dto.getSort() : 0)
            .visible(dto.getVisible() != null ? dto.getVisible() : 1)
            .status(dto.getStatus() != null ? dto.getStatus() : 1)
            .build();
    return menuRepository.save(menu).getId();
  }

  @Transactional
  public void update(Long id, MenuDTO dto) {
    SysMenu menu = getById(id);
    if (dto.getParentId() != null) {
      if (dto.getParentId().equals(id)) {
        throw BizException.i18n("menu.parent.self");
      }
      menu.setParentId(dto.getParentId());
    }
    if (dto.getMenuName() != null) {
      menu.setMenuName(dto.getMenuName());
    }
    if (dto.getMenuType() != null) {
      menu.setMenuType(dto.getMenuType());
    }
    if (dto.getPath() != null) {
      menu.setPath(dto.getPath());
    }
    if (dto.getComponent() != null) {
      menu.setComponent(dto.getComponent());
    }
    if (dto.getPermission() != null) {
      menu.setPermission(dto.getPermission());
    }
    if (dto.getIcon() != null) {
      menu.setIcon(dto.getIcon());
    }
    if (dto.getSort() != null) {
      menu.setSort(dto.getSort());
    }
    if (dto.getVisible() != null) {
      menu.setVisible(dto.getVisible());
    }
    if (dto.getStatus() != null) {
      menu.setStatus(dto.getStatus());
    }
    menuRepository.save(menu);
  }

  @Transactional
  public void delete(Long id) {
    SysMenu menu = getById(id);
    List<SysMenu> children = menuRepository.findByParentId(id);
    if (!children.isEmpty()) {
      throw BizException.i18n("menu.has.children");
    }
    menuRepository.delete(menu);
  }

  public static List<MenuTreeNode> buildTree(List<SysMenu> menus) {
    return buildTreeFromNodes(menus.stream().map(MenuTreeNode::of).toList());
  }

  public static List<MenuTreeNode> buildTreeFromNodes(List<MenuTreeNode> nodes) {
    Map<Long, List<MenuTreeNode>> parentIdMap =
        nodes.stream()
            .collect(Collectors.groupingBy(n -> n.getParentId() == null ? 0L : n.getParentId()));

    List<MenuTreeNode> roots = new ArrayList<>(parentIdMap.getOrDefault(0L, new ArrayList<>()));

    for (MenuTreeNode node : nodes) {
      List<MenuTreeNode> children = parentIdMap.get(node.getId());
      if (children != null) {
        children.sort(Comparator.comparingInt(MenuTreeNode::getSort));
        node.setChildren(children);
      }
    }

    roots.sort(Comparator.comparingInt(MenuTreeNode::getSort));
    return roots;
  }
}
