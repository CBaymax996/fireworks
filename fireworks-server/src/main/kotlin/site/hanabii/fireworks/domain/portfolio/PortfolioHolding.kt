package site.hanabii.fireworks.domain.portfolio

import java.time.Instant

/**
 * 实际持仓（portfolio_holding 表）。
 * CASH 类型: current_price = 1.0, current_shares = 金额(元)，不参与 tushare 同步。
 */
data class PortfolioHolding(
    val id: Long? = null,
    val allocationId: Long,             // FK → portfolio.id
    val currentShares: Long = 0,
    val currentPrice: Double? = null,   // NULL=待同步; CASH 恒为 1.0
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(currentShares >= 0) { "currentShares must be >= 0, got $currentShares" }
        require(currentPrice == null || currentPrice > 0) { "currentPrice must be > 0 or null, got $currentPrice" }
    }
}
