package site.hanabii.fireworks.infra.portfolio

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.double
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.portfolio.PortfolioSnapshot
import java.time.Instant

/**
 * 每日快照表映射（portfolio_snapshot）。
 * 每条 = 一个资产 × 一天，查询总净值需 GROUP BY snapshot_time, portfolio_id SUM(amount)。
 */
object PortfolioSnapshotDO : Table<Nothing>("portfolio_snapshot") {
    val id = long("id").primaryKey()
    val snapshotTime = timestamp("snapshot_time")
    val portfolioId = long("portfolio_id")
    val code = varchar("code")
    val amount = double("amount")
    val ratio = double("ratio")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS portfolio_snapshot (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            snapshot_time   TIMESTAMP NOT NULL,
            portfolio_id    INTEGER NOT NULL,
            code            TEXT    NOT NULL,
            amount          REAL    NOT NULL,
            ratio           REAL    NOT NULL
        )
    """
}

fun QueryRowSet.toPortfolioSnapshot(): PortfolioSnapshot = PortfolioSnapshot(
    id = this[PortfolioSnapshotDO.id],
    snapshotTime = this[PortfolioSnapshotDO.snapshotTime] ?: Instant.now(),
    portfolioId = this[PortfolioSnapshotDO.portfolioId] ?: 0,
    code = this[PortfolioSnapshotDO.code] ?: "",
    amount = this[PortfolioSnapshotDO.amount] ?: 0.0,
    ratio = this[PortfolioSnapshotDO.ratio] ?: 0.0
)
