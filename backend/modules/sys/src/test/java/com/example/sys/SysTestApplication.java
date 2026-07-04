package com.example.sys;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/** sys 模块测试专用引导类（库模块无 @SpringBootApplication，@DataJpaTest 需要一个 @SpringBootConfiguration）。 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class SysTestApplication {}
