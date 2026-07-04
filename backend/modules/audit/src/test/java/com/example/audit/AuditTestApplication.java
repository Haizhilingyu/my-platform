package com.example.audit;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 审计模块测试专用引导类。仅用于 {@code @DataJpaTest} 切片测试，使 Spring Boot 能定位到 一个
 * {@code @SpringBootConfiguration}；主应用启动入口在 app 模块，此处不参与生产打包。
 */
@SpringBootApplication(scanBasePackages = "com.example.audit")
class AuditTestApplication {}
