package site.hanabii.fireworks.domain.portfolio

import java.time.Instant

/**
 * 投资组合策略/配比方案（portfolio 表）。
 * 同一 portfolio_name 可有多个资产行，每个资产占一行。
 * asset_type: ETF / STOCK / CASH
 */
data class Portfolio(
    val id: Long? = null,
    val portfolioName: String,          // "投资组合 1"
    val code: String,                   // "510300" / "CASH"
    val name: String,                   // "沪深300 ETF" / "现金/存款"
    val assetType: String = "ETF",      // ETF / STOCK / CASH
    val targetRatio: Double,            // 0.50 = 50%
    val createdAt: Instant = Instant.now()
) {
    init {
        require(portfolioName.isNotBlank()) { "portfolioName must not be blank" }
        require(code.isNotBlank()) { "code must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(assetType in setOf("ETF", "ETF/STOCK", "STOCK", "CASH")) { "assetType must be ETF, ETF/STOCK, STOCK or CASH, got '$assetType'" }
        require(targetRatio > 0) { "targetRatio must be > 0, got $targetRatio" }
    }
}
