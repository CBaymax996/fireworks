package site.hanabii.fireworks.infra.portfolio

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.double
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import site.hanabii.fireworks.domain.portfolio.PortfolioHolding
import java.time.Instant

/**
 * 实际持仓表映射（portfolio_holding）。
 * allocation_id 外键关联 portfolio.id。
 * CASH 类型: current_price = 1.0, 不参与 tushare 同步。
 */
object PortfolioHoldingDO : Table<Nothing>("portfolio_holding") {
    val id = long("id").primaryKey()
    val allocationId = long("allocation_id")
    val currentShares = long("current_shares")
    val currentPrice = double("current_price")  // NULL=待同步
    val updatedAt = timestamp("updated_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS portfolio_holding (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            allocation_id   INTEGER NOT NULL,
            current_shares  INTEGER NOT NULL DEFAULT 0,
            current_price   REAL,
            updated_at      TIMESTAMP NOT NULL
        )
    """
}

fun QueryRowSet.toPortfolioHolding(): PortfolioHolding = PortfolioHolding(
    id = this[PortfolioHoldingDO.id],
    allocationId = this[PortfolioHoldingDO.allocationId] ?: 0,
    currentShares = this[PortfolioHoldingDO.currentShares] ?: 0,
    currentPrice = this[PortfolioHoldingDO.currentPrice],
    updatedAt = this[PortfolioHoldingDO.updatedAt] ?: Instant.now()
)
