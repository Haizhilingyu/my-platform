package com.example.sys.service;

import com.example.sys.domain.SysMenu;
import com.example.sys.domain.SysMenuTranslation;
import com.example.sys.dto.MenuTranslationImportDTO;
import com.example.sys.dto.MenuTranslationVO;
import com.example.sys.repository.SysMenuRepository;
import com.example.sys.repository.SysMenuTranslationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuTranslationService {

  private final SysMenuTranslationRepository translationRepository;
  private final SysMenuRepository menuRepository;

  @Transactional(readOnly = true)
  public List<MenuTranslationVO> list() {
    List<SysMenu> menus = menuRepository.findAll();
    Map<Long, SysMenu> menuMap =
        menus.stream().collect(Collectors.toMap(SysMenu::getId, Function.identity()));
    List<SysMenuTranslation> translations = translationRepository.findAll();
    List<MenuTranslationVO> result = new ArrayList<>();
    for (SysMenuTranslation t : translations) {
      MenuTranslationVO vo = new MenuTranslationVO();
      vo.setId(t.getId());
      vo.setMenuId(t.getMenuId());
      vo.setLocale(t.getLocale());
      vo.setDisplayName(t.getDisplayName());
      SysMenu menu = menuMap.get(t.getMenuId());
      vo.setMenuName(menu != null ? menu.getMenuName() : null);
      result.add(vo);
    }
    return result;
  }

  @Transactional
  public void update(Long id, String displayName) {
    SysMenuTranslation t = translationRepository.findById(id).orElse(null);
    if (t != null) {
      t.setDisplayName(displayName);
      translationRepository.save(t);
    }
  }

  @Transactional
  public void importTranslations(MenuTranslationImportDTO dto) {
    translationRepository.deleteByLocale(dto.getLocale());
    if (dto.getItems() != null) {
      List<SysMenuTranslation> batch =
          dto.getItems().stream()
              .map(
                  item ->
                      SysMenuTranslation.builder()
                          .menuId(item.getMenuId())
                          .locale(dto.getLocale())
                          .displayName(item.getDisplayName())
                          .build())
              .toList();
      translationRepository.saveAll(batch);
    }
  }

  @Transactional(readOnly = true)
  public List<MenuTranslationVO> exportByLocale(String locale) {
    List<SysMenu> menus = menuRepository.findAll();
    Map<Long, SysMenu> menuMap =
        menus.stream().collect(Collectors.toMap(SysMenu::getId, Function.identity()));
    List<SysMenuTranslation> translations = translationRepository.findByLocale(locale);
    List<MenuTranslationVO> result = new ArrayList<>();
    for (SysMenuTranslation t : translations) {
      MenuTranslationVO vo = new MenuTranslationVO();
      vo.setId(t.getId());
      vo.setMenuId(t.getMenuId());
      vo.setLocale(t.getLocale());
      vo.setDisplayName(t.getDisplayName());
      SysMenu menu = menuMap.get(t.getMenuId());
      vo.setMenuName(menu != null ? menu.getMenuName() : null);
      result.add(vo);
    }
    return result;
  }
}
