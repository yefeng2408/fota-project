package com.yef.utils;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;

public class CodeGenerator {

    public static void main(String[] args) {

        String url = "jdbc:mysql://localhost:3306/fota?useSSL=false&serverTimezone=UTC&characterEncoding=utf-8";
        String username = "root";
        String password = "yefeng";

        String projectPath = System.getProperty("user.dir") + "/fota-web-http-server";
        FastAutoGenerator.create(url, username, password)
                // 🔥 全局配置
                .globalConfig(builder -> {
                    builder
                            .author("yef")
                            .outputDir(projectPath + "/src/main/java")
                            .disableOpenDir(); // 不自动打开文件夹
                })

                // 🔥 包配置
                .packageConfig(builder -> {
                    builder
                            .parent("com.yef.fota")
                            .entity("entity")
                            .mapper("mapper")
                            .service("service")
                            .serviceImpl("service.impl")
                            .xml("mapper.xml")
                            // XML生成路径（推荐）
                            .pathInfo(Collections.singletonMap(
                                    OutputFile.xml,
                                    projectPath + "/src/main/resources/mapper"
                            ));
                })

                // 🔥 策略配置（核心🔥）
                .strategyConfig(builder -> {
                    builder

                            // 👉 这里放你所有表
                            .addInclude(
                                    "device",
                                    "upgrade_task",
                                    "device_upgrade_log",
                                    "batch_upgrade_task",
                                    "device_group",
                                    "device_group_relation",
                                    "user",
                                    "user_device_group",
                                    "operate_log"
                            )

                            // 👉 去掉表前缀（如果有）
                            //.addTablePrefix("t_")

                            // ========================
                            // Entity 配置
                            // ========================
                            .entityBuilder()
                            .enableLombok()
                            .enableTableFieldAnnotation()
                            .formatFileName("%sEntity")

                            // ========================
                            // Mapper 配置
                            // ========================
                            .mapperBuilder()
                            .enableMapperAnnotation()
                            .enableBaseResultMap()
                            .enableBaseColumnList()
                            .formatMapperFileName("%sMapper")
                            .formatXmlFileName("%sMapper")

                            // ========================
                            // Service 配置
                            // ========================
                            .serviceBuilder()
                            .formatServiceFileName("%sService")
                            .formatServiceImplFileName("%sServiceImpl");
                })

                // 🔥 使用 Freemarker 模板
                .templateEngine(new FreemarkerTemplateEngine())

                .execute();
    }
}