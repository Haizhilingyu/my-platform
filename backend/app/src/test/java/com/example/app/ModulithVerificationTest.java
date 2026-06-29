package com.example.app;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith 模块边界验证。
 * 确保模块间不存在违规依赖。
 */
class ModulithVerificationTest {

    @Test
    void verifyModuleStructure() {
        ApplicationModules.of(Application.class).verify();
    }
}
