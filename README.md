# Fireworks

CBaymax 的个人项目。

## 技术栈

- **后端**: Kotlin + Spring Boot 4.0 + Ktorm + SQLite
- **前端**: Vue 3 + Vite + TypeScript + Element Plus + Pinia
- **构建**: Gradle 9.5 + JDK 25

## 环境要求

- JDK 25
- Node.js >= 22

## 启动

### 开发模式

```bash
 # 后端 8080 + 前端 5173 (热部署)
./gradlew fireworksRun -Penv=dev   
 # 仅启动后端 (8080)
./gradlew :fireworks-server:bootRun
```

### 生产模式

```bash
./gradlew fireworksRun              # 打包后端 + 前端
./gradlew :fireworks-server:bootJar # 仅打包后端
```
