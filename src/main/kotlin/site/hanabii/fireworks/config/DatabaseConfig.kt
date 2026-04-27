package site.hanabii.fireworks.config

import org.ktorm.database.Database
import org.ktorm.support.sqlite.SQLiteDialect
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.sqlite.SQLiteDataSource
import javax.sql.DataSource

@Configuration
class DatabaseConfig(
    @Value("\${app.database.url}") private val url: String
) {

    @Bean
    fun dataSource(): DataSource {
        return SQLiteDataSource().apply {
            setUrl(url)
        }
    }

    @Bean
    fun database(dataSource: DataSource): Database {
        return Database.connect(dataSource, dialect = SQLiteDialect()).apply {
            useConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS users (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL,
                            email TEXT NOT NULL UNIQUE,
                            created_at TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                }
            }
        }
    }
}
