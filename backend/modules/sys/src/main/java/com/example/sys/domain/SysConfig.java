package com.example.sys.domain;

import com.example.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 系统配置实体（键值对）。 */
@Entity
@Table(name = "sys_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysConfig extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "config_key", nullable = false, unique = true, length = 100)
  private String configKey;

  @Column(name = "config_value", columnDefinition = "TEXT")
  private String configValue;

  /** STRING / NUMBER / BOOLEAN / JSON */
  @Column(name = "config_type", nullable = false, length = 20)
  @Builder.Default
  private String configType = "STRING";

  @Column(length = 200)
  private String description;

  /** 配置分组 */
  @Column(length = 50)
  @Builder.Default
  private String category = "default";
}
