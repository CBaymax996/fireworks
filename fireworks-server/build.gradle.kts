plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")

    // Ktorm ORM
    implementation("org.ktorm:ktorm-core:4.1.1")
    implementation("org.ktorm:ktorm-support-sqlite:4.1.1")

    implementation("org.springframework.security:spring-security-crypto")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("site.hanabii.fireworks.FireworksApplicationKt")
}

// processResources 依赖前端构建，确保 dist 在复制前已生成。
// buildFrontend 配置了 Gradle 增量构建输入输出，若未变更则跳过 npm 编译。
// dev 模式下（执行 bootRunDev 时）跳过前端构建和静态资源复制，前端由独立 dev server 提供服务。
tasks.processResources {
    val isDevMode = gradle.startParameter.taskNames.any { it.contains("Dev", ignoreCase = true) }
    if (!isDevMode) {
        dependsOn(":fireworks-web:buildFrontend")
        into("static") {
            from("../fireworks-web/dist")
        }
    }
}

// debug 模式：同时启动 Spring Boot 后端和 Vue 前端 dev server（带 HMR）
tasks.register<JavaExec>("bootRunDev") {
    group = "application"
    description = "Run Spring Boot backend and Vue frontend dev server simultaneously"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("site.hanabii.fireworks.FireworksApplicationKt")

    // 后台启动前端 dev server
    doFirst {
        println("Starting Vue frontend dev server in background...")
        ProcessBuilder("npm", "run", "dev")
            .directory(file("../fireworks-web"))
            .inheritIO()
            .start()
    }
}
