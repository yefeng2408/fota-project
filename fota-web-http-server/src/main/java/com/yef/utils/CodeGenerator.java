package com.yef.utils;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;

public class CodeGenerator {

    public static void main(String[] args) {

        FastAutoGenerator.create(
                        "jdbc:mysql://localhost:3306/fota?useSSL=false&serverTimezone=UTC",
                        "root",
                        "password"
                )
                .globalConfig(builder -> {
                    builder.author("yef")
                            .outputDir(System.getProperty("user.dir") + "/src/main/java");
                })
                .packageConfig(builder -> {
                    builder.parent("com.yef.fota")
                            .entity("entity")
                            .mapper("mapper")
                            .xml("mapper.xml");
                })
                .strategyConfig(builder -> {
                    builder.addInclude(
                                    "device",
                                    "upgrade_task",
                                    "device_upgrade_log",
                                    "batch_upgrade_task"
                            )
                            .entityBuilder()
                            .enableLombok()
                            .mapperBuilder()
                            .enableMapperAnnotation()
                            .enableBaseResultMap()
                            .enableBaseColumnList();
                })
                .execute();
    }
}
