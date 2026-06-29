package com.example.sys.repository;

import com.example.sys.domain.SysConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 系统配置 Repository。 */
public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {

    Optional<SysConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);

    List<SysConfig> findByCategory(String category);
}
