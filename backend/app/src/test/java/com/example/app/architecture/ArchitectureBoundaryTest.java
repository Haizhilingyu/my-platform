package com.example.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 平台架构边界守护（ArchUnit）。
 *
 * <p>本测试集与 {@link ScopedRepositoryArchitectureTest}（数据权限 Repository 约束）和 platform-common 的 {@code
 * ArchUnitTest}（common 不反向依赖业务）互补，覆盖：
 *
 * <ol>
 *   <li><b>模块边界</b>：平台基础层（common / platform.security）不得反向依赖业务模块； 核心业务模块（sys / audit /
 *       openapp）之间不得相互依赖。
 *   <li><b>命名规范</b>：controller / service / repository 包内类名必须以对应后缀结尾。
 *   <li><b>分层方向</b>：Controller 不得直接访问 Repository（必须通过 Service）。
 *   <li><b>分层架构</b>：Controller → Service → Repository 单向依赖（consideringOnlyDependenciesInLayers 忽略对
 *       common / DTO / framework 的依赖，避免误报）。
 * </ol>
 *
 * <p>已知合法跨模块依赖（不视为违规）：
 *
 * <ul>
 *   <li>{@code notify → sys}：消息投递需解析用户/单位/角色（MessageService 注入 Sys*Repository）。
 *   <li>{@code login-ldap → sys}：LDAP 登录需查找/创建用户与角色（LdapLoginProvider 注入 Sys*Service/Repository）。
 * </ul>
 *
 * 这两个是「集成点」而非「循环依赖」，故仅约束 sys/audit/openapp 三个无依赖模块不得反向引入。
 */
class ArchitectureBoundaryTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    // app 模块测试 classpath 包含全部模块，一次性导入 com.example.. 主代码（排除测试类）。
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example..");
  }

  // ======================================================================
  // 1. 模块边界：平台基础层不得反向依赖业务模块
  // ======================================================================

  /**
   * platform-common 是最底层基础模块，禁止依赖任何业务模块（sys / audit / notify / openapp / loginldap），否则将破坏分层架构。
   *
   * <p>与 platform-common 自带的 {@code ArchUnitTest} 语义一致，但此处扫描全量 classpath 做一次集中校验， 覆盖所有业务模块包名。
   */
  @Test
  void platformCommonShouldNotDependOnBusinessModules() {
    noClasses()
        .that()
        .resideInAPackage("com.example.common..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.example.sys..",
            "com.example.audit..",
            "com.example.notify..",
            "com.example.openapp..",
            "com.example.loginldap..",
            "com.example.app..")
        .because("platform-common 是基础层，不得反向依赖业务或应用模块")
        .check(classes);
  }

  /**
   * platform-security 提供 JWT 过滤器与 SecurityConfig，只依赖 platform-common 的抽象（CurrentUser / JwtUtil /
   * PermissionLoader），不得直接耦合 sys 业务实现。
   *
   * <p>这是 T2 解耦的核心成果：SecurityConfig 通过 {@code PermissionLoader} 接口（在 common 定义）调用 sys 的
   * PermissionService，编译期无 sys 依赖。
   */
  @Test
  void platformSecurityShouldNotDependOnSys() {
    noClasses()
        .that()
        .resideInAPackage("com.example.platform.security..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.example.sys..",
            "com.example.audit..",
            "com.example.notify..",
            "com.example.openapp..",
            "com.example.loginldap..")
        .because("platform-security 只依赖 common 抽象（PermissionLoader/JwtUtil），" + "不得直接耦合业务模块实现")
        .check(classes);
  }

  /**
   * sys 是核心业务模块（用户/角色/菜单/单位），不得反向依赖 audit / notify / openapp / loginldap。
   *
   * <p>sys 是被依赖方（notify 与 loginldap 合法地依赖 sys），自身不得引入下游模块造成循环。
   */
  @Test
  void sysShouldNotDependOnOtherBusinessModules() {
    noClasses()
        .that()
        .resideInAPackage("com.example.sys..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.example.audit..",
            "com.example.notify..",
            "com.example.openapp..",
            "com.example.loginldap..")
        .because("sys 是核心业务模块，不得反向依赖 audit/notify/openapp/loginldap")
        .check(classes);
  }

  /** audit 是审计日志写入与查询模块，只通过 {@code @Auditable} 注解 + common 的 AuditAspect 解耦采集， 不得直接依赖任何业务模块。 */
  @Test
  void auditShouldNotDependOnBusinessModules() {
    noClasses()
        .that()
        .resideInAPackage("com.example.audit..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.example.sys..",
            "com.example.notify..",
            "com.example.openapp..",
            "com.example.loginldap..")
        .because("audit 通过注解 + AOP 解耦采集，不得直接依赖业务模块")
        .check(classes);
  }

  /**
   * openapp（OAuth2 授权服务器 + OIDC）是独立的安全基础设施，不得依赖业务模块。
   *
   * <p>openapp 的 client/jwk/webhook 组件自包含，仅依赖 common 与 Spring Authorization Server。
   */
  @Test
  void openappShouldNotDependOnBusinessModules() {
    noClasses()
        .that()
        .resideInAPackage("com.example.openapp..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.example.sys..",
            "com.example.audit..",
            "com.example.notify..",
            "com.example.loginldap..")
        .because("openapp 是独立 OAuth2 基础设施，不得依赖业务模块")
        .check(classes);
  }

  // ======================================================================
  // 2. 命名规范
  // ======================================================================

  /** controller 包内的 @RestController 类必须以 Controller 结尾。 */
  @Test
  void controllersShouldBeSuffixedController() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..controller..")
            .and()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should()
            .haveSimpleNameEndingWith("Controller")
            .because("REST 控制器统一以 Controller 结尾，便于识别与扫描");
    rule.check(classes);
  }

  /**
   * service 包内的 @Service 类必须以 Service 结尾。
   *
   * <p>限定 @Service 注解避免误伤 service 包内的 DTO / 辅助类（如 DataScopeResolver 若非 @Service）。
   */
  @Test
  void servicesShouldBeSuffixedService() {
    ArchRule rule =
        classes()
            .that()
            .resideInAPackage("..service..")
            .and()
            .areAnnotatedWith("org.springframework.stereotype.Service")
            .should()
            .haveSimpleNameEndingWith("Service")
            .because("@Service Bean 统一以 Service 结尾，便于识别与扫描");
    rule.check(classes);
  }

  /**
   * repository 包内的接口必须以 Repository 结尾。
   *
   * <p>只约束业务 repository 包（sys/audit/notify），不含 openapp.client（Spring AS 的
   * JdbcRegisteredClientRepository 是框架接口实现，不属本平台命名规范）。
   */
  @Test
  void repositoriesShouldBeSuffixedRepository() {
    ArchRule rule =
        classes()
            .that()
            .resideInAnyPackage(
                "com.example.sys.repository..",
                "com.example.audit.repository..",
                "com.example.notify.repository..")
            .and()
            .areInterfaces()
            .should()
            .haveSimpleNameEndingWith("Repository")
            .because("业务 Repository 接口统一以 Repository 结尾");
    rule.check(classes);
  }

  // ======================================================================
  // 3. 分层方向：Controller 不得直接访问 Repository
  // ======================================================================

  /**
   * Controller 只能调用 Service，不得直接注入或访问 Repository（绕过业务逻辑层）。
   *
   * <p>允许 Controller 依赖 common（Result / CurrentUser / 注解）、DTO、domain（返回类型）与 framework； 但任何对 {@code
   * ..repository..} 包的引用都视为越层。
   *
   * <p>当前 AuthController 引用 {@code SysMenu}（domain，非 repository）用于返回类型签名，本规则放行。
   */
  @Test
  void controllersShouldNotAccessRepositoriesDirectly() {
    noClasses()
        .that()
        .resideInAPackage("..controller..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.example.sys.repository..",
            "com.example.audit.repository..",
            "com.example.notify.repository..")
        .because("Controller 必须通过 Service 访问数据，不得越层直接注入 Repository")
        .check(classes);
  }

  /**
   * 分层架构：Controller → Service → Repository 单向依赖。
   *
   * <p>使用 {@code consideringOnlyDependenciesInLayers()} 只检查层间依赖，忽略对 common / DTO / framework /
   * 第三方的依赖，避免误报。Repository 不得依赖 Controller 或 Service；Service 不得依赖 Controller。
   */
  @Test
  void layeredArchitectureRespected() {
    com.tngtech.archunit.library.Architectures.layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Controller")
        .definedBy("..controller..")
        .layer("Service")
        .definedBy("..service..")
        .layer("Repository")
        .definedBy(
            "com.example.sys.repository..",
            "com.example.audit.repository..",
            "com.example.notify.repository..")
        .whereLayer("Controller")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("Service")
        .mayOnlyBeAccessedByLayers("Controller")
        .whereLayer("Repository")
        .mayOnlyBeAccessedByLayers("Controller", "Service")
        .check(classes);
  }
}
