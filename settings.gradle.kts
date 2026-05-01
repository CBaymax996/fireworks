pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
    }
}

rootProject.name = "fireworks"
include("fireworks-server")
include("fireworks-web")
