package com.yef.fota.config;

import com.dtflys.forest.config.SpringForestProperties;
import com.dtflys.forest.interceptor.SpringInterceptorFactory;
import com.dtflys.forest.reflection.SpringForestObjectFactory;
import com.dtflys.forest.spring.ForestBeanProcessor;
import com.dtflys.forest.springboot.ForestBeanRegister;
import com.dtflys.forest.springboot.properties.ForestConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * Forest 1.5.x starter 的自动配置在当前 Spring Boot 组合下会拿到空的 applicationContext，
 * 这里显式传入上下文，绕开其内部的字段注入问题。
 */
@Configuration
@EnableConfigurationProperties(ForestConfigurationProperties.class)
public class ForestCompatConfig {

    @Bean
    @ConditionalOnMissingBean
    public SpringForestProperties forestProperties() {
        return new SpringForestProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringForestObjectFactory forestObjectFactory() {
        return new SpringForestObjectFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringInterceptorFactory forestInterceptorFactory() {
        return new SpringInterceptorFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public ForestBeanProcessor forestBeanProcessor() {
        return new ForestBeanProcessor();
    }

    @Bean
    @DependsOn("forestBeanProcessor")
    @ConditionalOnMissingBean
    public ForestBeanRegister forestBeanRegister(
            ConfigurableApplicationContext applicationContext,
            SpringForestProperties properties,
            SpringForestObjectFactory forestObjectFactory,
            SpringInterceptorFactory forestInterceptorFactory,
            ForestConfigurationProperties forestConfigurationProperties) {
        ForestBeanRegister register = new ForestBeanRegister(
                applicationContext,
                forestConfigurationProperties,
                properties,
                forestObjectFactory,
                forestInterceptorFactory
        );
        register.registerForestConfiguration();
        register.registerScanner();
        return register;
    }
}
