plugins {
    base
}

// 前端构建产物输出目录
val frontendDir = projectDir

// 清理前端构建产物
tasks.register<Exec>("cleanFrontend") {
    group = "build"
    description = "Clean Vue frontend build output"
    workingDir = frontendDir
    commandLine("npm", "run", "build-only")
    // 先执行 npm clean 或删除 dist
    doFirst {
        delete("$frontendDir/dist")
    }
}

// 构建前端（npm run build）
tasks.register<Exec>("buildFrontend") {
    group = "build"
    description = "Build Vue frontend with npm"
    workingDir = frontendDir
    commandLine("npm", "run", "build-only")

    inputs.dir("$frontendDir/src")
    inputs.dir("$frontendDir/public")
    inputs.file("$frontendDir/package.json")
    inputs.file("$frontendDir/vite.config.ts")
    inputs.file("$frontendDir/index.html")
    outputs.dir("$frontendDir/dist")
}

// 运行前端开发服务器（热部署）
tasks.register<Exec>("runDev") {
    group = "application"
    description = "Run Vue frontend dev server with hot reload"
    workingDir = frontendDir
    commandLine("npm", "run", "dev")
}
