package site.hanabii.fireworks.infra.portfolio

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.double
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.portfolio.StockPrice
import java.time.Instant

/**
 * 价格历史表映射（stock_history）。
 * 每条记录记录一个持仓在某个时间点的价格，来源 tushare 或手动录入。
 */
object StockHistoryDO : Table<Nothing>("stock_history") {
    val id = long("id").primaryKey()
    val holdingId = long("holding_id")
    val source = varchar("source")
    val price = double("price")
    val recordedAt = timestamp("recorded_at")
    val name = varchar("name")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS stock_history (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            holding_id    INTEGER NOT NULL,
            source        TEXT    NOT NULL,
            price         REAL    NOT NULL,
            recorded_at   TIMESTAMP NOT NULL,
            name          TEXT
        )
    """
}

fun QueryRowSet.toStockPrice(): StockPrice = StockPrice(
    id = this[StockHistoryDO.id],
    holdingId = this[StockHistoryDO.holdingId] ?: 0,
    source = this[StockHistoryDO.source] ?: "tushare",
    price = this[StockHistoryDO.price] ?: 0.0,
    recordedAt = this[StockHistoryDO.recordedAt] ?: Instant.now(),
    name = this[StockHistoryDO.name]
)
