package com.example.sys.repository;

import com.example.sys.domain.SysMenuTranslation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysMenuTranslationRepository extends JpaRepository<SysMenuTranslation, Long> {

  List<SysMenuTranslation> findByMenuId(Long menuId);

  List<SysMenuTranslation> findByLocale(String locale);

  Optional<SysMenuTranslation> findByMenuIdAndLocale(Long menuId, String locale);

  List<SysMenuTranslation> findByMenuIdInAndLocale(Collection<Long> menuIds, String locale);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM SysMenuTranslation t WHERE t.locale = :locale")
  int deleteByLocale(@Param("locale") String locale);
}
