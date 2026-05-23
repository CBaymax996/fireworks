package site.hanabii.fireworks.infra.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import site.hanabii.fireworks.infra.UserDO
import site.hanabii.fireworks.infra.auth.AccountDO
import site.hanabii.fireworks.infra.vault.PasswordEntryDO
import site.hanabii.fireworks.infra.family.FamilyPersonDO
import site.hanabii.fireworks.infra.family.FamilyTreeDO
import site.hanabii.fireworks.infra.family.PersonSpouseDO
import site.hanabii.fireworks.infra.family.FamilyLineageDO
import site.hanabii.fireworks.infra.vault.VaultConfigDO
import site.hanabii.fireworks.infra.portfolio.PortfolioDO
import site.hanabii.fireworks.infra.portfolio.PortfolioHoldingDO
import site.hanabii.fireworks.infra.portfolio.StockHistoryDO
import site.hanabii.fireworks.infra.portfolio.PortfolioSnapshotDO
import javax.sql.DataSource

/**
 * 应用启动后初始化数据库 schema（对 SQLite 幂等，使用 IF NOT EXISTS）。
 */
@Component
class SchemaInitializer(
    private val dataSource: DataSource,
    @param:Value("\${fireworks.db.init-schema:true}") private val enabled: Boolean
) {

    @EventListener(ApplicationReadyEvent::class)
    @Order(1)
    fun init() {
        if (!enabled) return
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(UserDO.DDL.trimIndent())
                stmt.execute(VaultConfigDO.DDL.trimIndent())
                stmt.execute(PasswordEntryDO.DDL.trimIndent())
                stmt.execute(AccountDO.DDL.trimIndent())
                stmt.execute(FamilyPersonDO.DDL.trimIndent())
                stmt.execute(FamilyTreeDO.DDL.trimIndent())
                stmt.execute(PersonSpouseDO.DDL.trimIndent())
                stmt.execute(FamilyLineageDO.DDL.trimIndent())
                // V2 四表模型
                stmt.execute(PortfolioDO.DDL.trimIndent())
                stmt.execute(PortfolioHoldingDO.DDL.trimIndent())
                stmt.execute(StockHistoryDO.DDL.trimIndent())
                stmt.execute(PortfolioSnapshotDO.DDL.trimIndent())
            }
        }
    }
}
