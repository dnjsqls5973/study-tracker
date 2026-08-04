package com.wonbin.study_tracker.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationProdProfileTest {

    @Test
    void prod_프로파일은_컨테이너_mysql_서비스를_기본_호스트로_사용한다() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources =
                loader.load("application-prod", new ClassPathResource("application-prod.yaml"));

        String url = (String) sources.get(0).getProperty("spring.datasource.url");

        assertThat(url).isEqualTo(
                "jdbc:mysql://${DB_HOST:mysql}:${DB_PORT:3306}/study_tracker?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
    }
}
