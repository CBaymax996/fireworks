package site.hanabii.fireworks.infra.portfolio

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.double
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.portfolio.Portfolio
import java.time.Instant

/**
 * 投资组合策略表映射（portfolio V2）。
 * 同一 portfolio_name 共享多行，每行一个资产配比。
 */
object PortfolioDO : Table<Nothing>("portfolio") {
    val id = long("id").primaryKey()
    val portfolioName = varchar("portfolio_name")
    val code = varchar("code")
    val name = varchar("name")
    val assetType = varchar("asset_type")
    val targetRatio = double("target_ratio")
    val createdAt = timestamp("created_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS portfolio (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            portfolio_name  TEXT    NOT NULL,
            code            TEXT    NOT NULL,
            name            TEXT    NOT NULL,
            asset_type      TEXT    NOT NULL DEFAULT 'ETF',
            target_ratio    REAL    NOT NULL,
            created_at      TIMESTAMP NOT NULL
        )
    """
}

fun QueryRowSet.toPortfolio(): Portfolio = Portfolio(
    id = this[PortfolioDO.id],
    portfolioName = this[PortfolioDO.portfolioName] ?: "",
    code = this[PortfolioDO.code] ?: "",
    name = this[PortfolioDO.name] ?: "",
    assetType = this[PortfolioDO.assetType] ?: "ETF",
    targetRatio = this[PortfolioDO.targetRatio] ?: 0.0,
    createdAt = this[PortfolioDO.createdAt] ?: Instant.now()
)
