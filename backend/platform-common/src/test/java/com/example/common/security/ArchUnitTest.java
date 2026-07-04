package com.example.common.security;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 模块边界守护：platform-common 作为最底层基础模块，禁止反向依赖任何业务模块 （com.example.sys / com.example.app 等），否则将破坏分层架构。
 *
 * <p>若有人在 platform-common 中新增 {@code import com.example.sys.*}，本测试会立即失败。
 */
class ArchUnitTest {

  private final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.example.common..");

  @Test
  void commonMustNotDependOnSysModule() {
    noClasses()
        .that()
        .resideInAPackage("com.example.common..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.example.sys..")
        .check(classes);
  }

  @Test
  void commonMustNotDependOnAppModule() {
    noClasses()
        .that()
        .resideInAPackage("com.example.common..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.example.app..")
        .check(classes);
  }
}
