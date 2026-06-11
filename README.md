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

### 生产模式

```bash
./run                    # 启动全部模块
./run -backend=fireworks-server   # 仅启动后端
./run -frontend=fireworks-web     # 仅启动前端
```

### 开发模式

```bash
./runDev                 # 启动全部模块（前端热部署）
./runDev -backend=fireworks-server
./runDev -frontend=fireworks-web
```
