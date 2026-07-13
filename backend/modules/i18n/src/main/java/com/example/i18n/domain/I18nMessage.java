package com.example.i18n.domain;

import com.example.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "i18n_message",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_i18n_key_locale",
            columnNames = {"message_key", "locale"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class I18nMessage extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "message_key", nullable = false, length = 200)
  private String messageKey;

  @Column(nullable = false, length = 10)
  private String locale;

  @Column(nullable = false, length = 50)
  private String module;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String value;

  @Column(length = 500)
  private String description;

  @Version private Long version;
}
