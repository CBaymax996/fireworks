package site.hanabii.fireworks.infra._config

import org.ktorm.database.Database
import org.ktorm.support.sqlite.SQLiteDialect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Ktorm Database 配置。基于 Spring Boot 自动装配的 DataSource 创建 Ktorm Database。
 */
@Configuration
class KtormConfig {

    @Bean
    fun ktormDatabase(dataSource: DataSource): Database {
        return Database.connect(dataSource, dialect = SQLiteDialect())
    }
}