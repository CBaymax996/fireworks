package site.hanabii.fireworks.domain.portfolio

import java.time.Instant

/**
 * 每日快照（portfolio_snapshot 表），每条 = 一个资产 × 一天。
 * 查询总净值: GROUP BY snapshot_time, portfolio_id SUM(amount)
 */
data class PortfolioSnapshot(
    val id: Long? = null,
    val snapshotTime: Instant,          // 快照时间（精确到秒）
    val portfolioId: Long,              // 对应 portfolio.id（allocation 的 id）
    val code: String,                   // "510300" / "CASH"
    val amount: Double,                 // 市值 = shares × price
    val ratio: Double                   // 占总净值比
) {
    init {
        require(amount >= 0) { "amount must be >= 0, got $amount" }
        require(ratio >= 0) { "ratio must be >= 0, got $ratio" }
        require(code.isNotBlank()) { "code must not be blank" }
    }
}
