package site.hanabii.fireworks.domain.portfolio

import java.time.Instant

/**
 * 价格历史（stock_history 表），每条记录是一个持仓在某个时间点的价格。
 * 来源可以是 tushare 同步或手动录入。
 */
data class StockPrice(
    val id: Long? = null,
    val holdingId: Long,                // FK → portfolio_holding.id
    val source: String = "tushare",    // tushare / manual
    val price: Double,
    val recordedAt: Instant = Instant.now(),
    val name: String? = null           // 股票/ETF 名称（同步时自动写入）
) {
    init {
        require(price > 0) { "price must be > 0, got $price" }
        require(source in setOf("tushare", "manual")) { "source must be tushare or manual, got '$source'" }
    }
}
