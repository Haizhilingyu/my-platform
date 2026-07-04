package com.example.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.persistence.ScopedEntity;
import com.example.common.persistence.ScopedRepository;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ArchUnit 架构约束：处理 {@link ScopedEntity}（带数据权限范围）的 Repository 接口必须继承 {@link ScopedRepository}，从而获得
 * {@code JpaSpecificationExecutor} 与 {@code scopeFindById} 能力。
 */
class ScopedRepositoryArchitectureTest {

  @Test
  void repositoriesOverScopedEntitiesMustExtendScopedRepository() {
    JavaClasses classes = new ClassFileImporter().importPackages("com.example..repository");

    List<Class<?>> violating =
        classes.stream()
            .filter(JavaClass::isInterface)
            .filter(c -> c.isAssignableTo(JpaRepository.class))
            .map(JavaClass::reflect)
            .filter(ScopedRepositoryArchitectureTest::entityIsScoped)
            .filter(c -> !ScopedRepository.class.isAssignableFrom(c))
            .toList();

    assertThat(violating).as("处理 ScopedEntity 的 Repository 必须继承 ScopedRepository").isEmpty();
  }

  /** 解析 Repository 第一个 JpaRepository 泛型参数，判断其是否为 ScopedEntity 子类。 */
  private static boolean entityIsScoped(Class<?> repoInterface) {
    Class<?> entity =
        ResolvableType.forClass(repoInterface).as(JpaRepository.class).resolveGeneric(0);
    return entity != null && ScopedEntity.class.isAssignableFrom(entity);
  }
}
