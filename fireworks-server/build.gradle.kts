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
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
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

// ============================================================
// 正式模式：构建前端 → 打入 static/ → 启动 Spring Boot :8080
// ============================================================
// processResources 在 dev 模式下跳过前端构建，
// 其他情况（bootRun、bootJar、build 等）自动构建前端并复制到 static/
tasks.processResources {
    val isDev = gradle.startParameter.taskNames.any { it.contains("Dev", ignoreCase = true) }
    if (!isDev) {
        dependsOn(":fireworks-web:buildFrontend")
        into("static") {
            from("../fireworks-web/dist")
        }
    }
}

// ============================================================
// 开发模式：前端 Vite HMR (:5173) + 后端 :8080
// ============================================================
// 前端 dev server 代理 /api → localhost:8080
// 访问 http://localhost:5173 获得热部署体验
tasks.register<JavaExec>("bootRunDev") {
    group = "application"
    description = "开发模式：前端热部署 (Vite HMR :5173) + 后端 (:8080)"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("site.hanabii.fireworks.FireworksApplicationKt")

    doFirst {
        val frontendDir = file("../fireworks-web")
        println("[dev] 启动前端 Vite dev server (HMR)...")
        ProcessBuilder("npm", "run", "dev")
            .directory(frontendDir)
            .inheritIO()
            .start()
        // 等 Vite 启动（通常 2-3 秒）
        Thread.sleep(3000)
        println("[dev] 后端启动中...")
    }
}
