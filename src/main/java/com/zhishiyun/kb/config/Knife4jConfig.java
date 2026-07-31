package com.zhishiyun.kb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/** Knife4j / Swagger 文档配置。 */
@Configuration
public class Knife4jConfig {

    /** 扫描 com.zhishiyun.kb 包下接口生成 Swagger 文档。 */
    @Bean
    public Docket defaultApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.zhishiyun.kb"))
                .paths(PathSelectors.any())
                .build();
    }
}
