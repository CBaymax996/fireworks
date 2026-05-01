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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// processResources 依赖前端构建，确保 dist 在复制前已生成。
// buildFrontend 配置了 Gradle 增量构建输入输出，若未变更则跳过 npm 编译。
tasks.processResources {
    dependsOn(":fireworks-web:buildFrontend")
    into("static") {
        from("../fireworks-web/dist")
    }
}
