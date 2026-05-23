package site.hanabii.fireworks.domain.portfolio

/**
 * 投资组合 V2 仓储接口。
 * 四表模型: portfolio / portfolio_holding / stock_history / portfolio_snapshot
 */
interface PortfolioRepository {

    // ==================== 组合管理（portfolio 表） ====================

    /** 列出所有不重复的组合名称 */
    fun findAllPortfolioNames(): List<String>

    /** 按组合名获取所有资产配比 */
    fun findPortfolioByName(name: String): List<Portfolio>

    /** 按 id 查单个配比 */
    fun findPortfolioById(id: Long): Portfolio?

    /** 保存配比（新增或更新） */
    fun savePortfolio(portfolio: Portfolio): Portfolio

    /** 更新配比目标比例 */
    fun updatePortfolioRatio(id: Long, targetRatio: Double): Portfolio

    /** 删除单条配比 */
    fun deletePortfolio(id: Long)

    /** 删除组合及其所有关联数据（holding、snapshot） */
    fun deletePortfolioByName(name: String)

    /** 重命名组合及其所有关联数据 */
    fun renamePortfolioByName(oldName: String, newName: String)

    // ==================== 持仓（portfolio_holding 表） ====================

    /** 按配比 id 查对应持仓 */
    fun findHoldingByAllocationId(allocationId: Long): PortfolioHolding?

    /** 按组合名获取所有配比+持仓（LEFT JOIN） */
    fun findHoldingsByPortfolioName(name: String): List<Pair<Portfolio, PortfolioHolding?>>

    /** 保存持仓 */
    fun saveHolding(holding: PortfolioHolding): PortfolioHolding

    /** 删除持仓（连同价格历史） */
    fun deleteHoldingByAllocationId(allocationId: Long)

    // ==================== 价格历史（stock_history 表） ====================

    /** 查询某持仓的价格历史 */
    fun findStockHistory(holdingId: Long, days: Int): List<StockPrice>

    /** 保存价格记录 */
    fun saveStockPrice(price: StockPrice): StockPrice

    // ==================== 快照（portfolio_snapshot 表） ====================

    /** 按组合名查询快照（最近 N 天） */
    fun findSnapshotsByPortfolioName(name: String, days: Int): List<PortfolioSnapshot>

    /** 查询净值走势：日期字符串 → 总净值 */
    fun findNavHistory(name: String, days: Int): List<Pair<String, Double>>

    /** 保存一条快照 */
    fun saveSnapshot(snapshot: PortfolioSnapshot): PortfolioSnapshot
}
