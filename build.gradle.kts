plugins {
    kotlin("jvm") version "2.3.20" apply false
    kotlin("plugin.spring") version "2.3.20" apply false
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "site.hanabii"
    version = "1.0.0-SNAPSHOT"

    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/spring")
        mavenLocal()
        mavenCentral()
    }
}

tasks.register<Exec>("fireworksRunDev") {
    description = "启动 Fireworks 开发环境"
    group = "application"
    workingDir = rootProject.projectDir
    commandLine("gradle/script/run-dev.sh")
}