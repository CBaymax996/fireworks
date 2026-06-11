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

val env = project.findProperty("env")?.toString() ?: "prod"



tasks.register("fireworksRun") {
    description = "启动 Fireworks 应用。用法: ./gradlew fireworksRun -Penv=dev|prod (默认 prod)"
    group = "application"

    doFirst {
        println("fireworks run. env: $env")
    }

    when (env) {
        "dev" -> {
            dependsOn(":fireworks-server:bootRun")
            finalizedBy("npmRunDev")
        }
        "prod" -> {
            dependsOn(":fireworks-server:bootJar")
            finalizedBy("npmRunBuild")
        }
        else -> {
            throw GradleException("无效的环境参数: $env！支持 dev 或 prod")
        }
    }
}

tasks.register<Exec>("npmRunDev") {
    description = "在 fireworks-web 目录启动前端开发服务器 (npm run dev)"
    group = "application"
    workingDir = file("fireworks-web")
    commandLine("npm", "run", "dev")
}

tasks.register<Exec>("npmRunBuild") {
    description = "在 fireworks-web 目录构建前端生产包 (npm run build)"
    group = "build"
    workingDir = file("fireworks-web")
    commandLine("npm", "run", "build")
}