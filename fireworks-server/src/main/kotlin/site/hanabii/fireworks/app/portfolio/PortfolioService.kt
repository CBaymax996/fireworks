package site.hanabii.fireworks.app.portfolio

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.domain.portfolio.Portfolio
import site.hanabii.fireworks.domain.portfolio.PortfolioHolding
import site.hanabii.fireworks.domain.portfolio.PortfolioRepository
import site.hanabii.fireworks.domain.portfolio.PortfolioSnapshot
import site.hanabii.fireworks.domain.portfolio.StockPrice
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * 投资组合 V2 业务服务。
 * 四表模型: portfolio / portfolio_holding / stock_history / portfolio_snapshot
 * 负责组合概览、入金、出金、再平衡、价格同步等核心计算。
 */
@Service
class PortfolioService(
    private val portfolioRepository: PortfolioRepository,
    private val tushareService: TushareService
) {

    companion object {
        /** 买入卖出最小交易单位（股） */
        const val LOT_SIZE: Long = 100
    }

    // ==================== 组合列表管理 ====================

    /** 列出所有组合及其资产数、总净值 */
    fun listPortfolios(): List<PortfolioSummary> {
        val names = portfolioRepository.findAllPortfolioNames()
        return names.map { name ->
            val allocs = portfolioRepository.findPortfolioByName(name)
            val holdings = portfolioRepository.findHoldingsByPortfolioName(name)
            val nav = calculateNav(holdings)
            PortfolioSummary(
                portfolioName = name,
                assetCount = allocs.size,
                totalNav = roundTo2(nav)
            )
        }
    }

    /** 删除组合及其所有关联数据 */
    fun deletePortfolio(portfolioName: String) {
        portfolioRepository.deletePortfolioByName(portfolioName)
    }

    /** 重命名组合 */
    fun renamePortfolio(oldName: String, newName: String) {
        val existing = portfolioRepository.findPortfolioByName(oldName)
        if (existing.isEmpty()) {
            throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "组合不存在: $oldName"
            )
        }
        // 检查新名称是否冲突
        if (oldName != newName) {
            val conflict = portfolioRepository.findPortfolioByName(newName)
            if (conflict.isNotEmpty()) {
                throw AppException(
                    code = ErrorCode.INVALID_REQUEST,
                    status = HttpStatus.CONFLICT,
                    message = "组合名称已存在: $newName"
                )
            }
        }
        portfolioRepository.renamePortfolioByName(oldName, newName)
    }

    /** 创建新组合，自动添加一条 CASH 资产 */
    fun createPortfolio(portfolioName: String) {
        val existing = portfolioRepository.findPortfolioByName(portfolioName)
        if (existing.isNotEmpty()) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.CONFLICT,
                message = "组合已存在: $portfolioName"
            )
        }
        // 创建 CASH 资产配比（新建组合时现金占比 100%）
        val cashAllocation = Portfolio(
            portfolioName = portfolioName,
            code = "CASH",
            name = "现金",
            assetType = "CASH",
            targetRatio = 1.0
        )
        val saved = portfolioRepository.savePortfolio(cashAllocation)
        // 创建持仓（CASH: current_price = 1.0）
        portfolioRepository.saveHolding(
            PortfolioHolding(
                allocationId = saved.id!!,
                currentShares = 0,
                currentPrice = 1.0
            )
        )
    }

    // ==================== 组合概览 ====================

    /**
     * 获取组合概览：总净值、总投入、收益率、偏离度、各资产详情
     */
    fun getPortfolio(portfolioName: String): PortfolioOverview {
        val holdings = portfolioRepository.findHoldingsByPortfolioName(portfolioName)
        if (holdings.isEmpty()) {
            throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "组合不存在: $portfolioName"
            )
        }

        // 计算总净值
        val nav = calculateNav(holdings)
        // 总投入 = 所有持仓 shares × price（对于 CASH，price=1.0, shares=金额）
        val totalInvested = holdings.sumOf { (_, holding) ->
            if (holding != null && holding.currentPrice != null) {
                holding.currentShares * holding.currentPrice!!
            } else 0.0
        }

        val totalReturn = nav - totalInvested
        val returnRate = if (totalInvested > 0) totalReturn / totalInvested else 0.0

        // 各资产概览
        val assetOverviews = holdings.map { (alloc, holding) ->
            val price = holding?.currentPrice
            val shares = holding?.currentShares ?: 0
            val marketValue = if (price != null) shares * price else 0.0
            val actualRatio = if (nav > 0 && price != null) marketValue / nav else 0.0
            val deviation = actualRatio - alloc.targetRatio
            AssetOverview(
                allocationId = alloc.id ?: 0,
                code = alloc.code,
                name = alloc.name,
                assetType = alloc.assetType,
                shares = shares,
                price = price,
                marketValue = marketValue,
                actualRatio = roundTo4(actualRatio),
                targetRatio = alloc.targetRatio,
                deviation = roundTo4(deviation)
            )
        }

        // 总体偏离度 = 各资产偏离度绝对值中最大的
        val maxDeviation = assetOverviews.maxOfOrNull { abs(it.deviation) } ?: 0.0
        val maxDeviationAsset = assetOverviews.maxByOrNull { abs(it.deviation) }
        val deviationDetail = if (maxDeviationAsset != null && abs(maxDeviationAsset.deviation) > 0.001) {
            val direction = if (maxDeviationAsset.deviation > 0) "超配" else "低配"
            "${maxDeviationAsset.name} $direction ${formatPercent(abs(maxDeviationAsset.deviation))}"
        } else {
            "配置合理"
        }

        // 检查配比总和
        val totalTargetRatio = holdings.sumOf { (alloc, _) -> alloc.targetRatio }
        val ratioWarning = if (abs(totalTargetRatio - 1.0) > 0.001) {
            "当前配比总和 ${formatPercent(totalTargetRatio)}，${if (totalTargetRatio > 1.0) "超过" else "不足"} 100%"
        } else null

        return PortfolioOverview(
            portfolioName = portfolioName,
            totalNav = roundTo2(nav),
            totalInvested = roundTo2(totalInvested),
            totalReturn = roundTo2(totalReturn),
            returnRate = roundTo4(returnRate),
            deviation = roundTo4(maxDeviation),
            deviationDetail = deviationDetail,
            ratioWarning = ratioWarning,
            assets = assetOverviews
        )
    }

    /** 计算总净值：所有有价格的持仓 shares × price 之和 */
    private fun calculateNav(holdings: List<Pair<Portfolio, PortfolioHolding?>>): Double {
        return holdings.sumOf { (_, holding) ->
            if (holding?.currentPrice != null) holding.currentShares * holding.currentPrice!! else 0.0
        }
    }

    // ==================== 配比管理 ====================

    /** 获取组合的配比方案 */
    fun getAllocation(portfolioName: String): List<Portfolio> {
        return portfolioRepository.findPortfolioByName(portfolioName)
    }

    /** 添加资产到组合配比 */
    fun addAllocation(portfolioName: String, code: String, name: String, assetType: String, targetRatio: Double) {
        // 检查是否已存在相同代码
        val existing = portfolioRepository.findPortfolioByName(portfolioName)
        if (existing.any { it.code == code }) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.CONFLICT,
                message = "资产 $code 已存在于组合 $portfolioName"
            )
        }

        // 校验配比总和不超过 100%
        val currentTotal = existing.sumOf { it.targetRatio }
        if (currentTotal + targetRatio > 1.001) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "配比总和超过 100%（当前 ${formatPercent(currentTotal)}，新增 ${formatPercent(targetRatio)}）"
            )
        }

        val allocation = Portfolio(
            portfolioName = portfolioName,
            code = code,
            name = name,
            assetType = assetType,
            targetRatio = targetRatio
        )
        val saved = portfolioRepository.savePortfolio(allocation)
        // 自动创建持仓
        val defaultPrice = if (assetType == "CASH") 1.0 else null
        portfolioRepository.saveHolding(
            PortfolioHolding(
                allocationId = saved.id!!,
                currentShares = 0,
                currentPrice = defaultPrice
            )
        )
    }

    /** 修改配比（校验总和 100%） */
    fun updateAllocation(portfolioName: String, allocationId: Long, targetRatio: Double) {
        val alloc = portfolioRepository.findPortfolioById(allocationId)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "配比不存在: $allocationId"
            )

        // 校验配比总和不超过 100%
        val allAllocs = portfolioRepository.findPortfolioByName(portfolioName)
        val otherTotal = allAllocs.filter { it.id != allocationId }.sumOf { it.targetRatio }
        if (otherTotal + targetRatio > 1.001) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "配比总和超过 100%（其他合计 ${formatPercent(otherTotal)}，新配比 ${formatPercent(targetRatio)}）"
            )
        }

        portfolioRepository.updatePortfolioRatio(allocationId, targetRatio)
    }

    /** 删除资产配比及对应持仓 */
    fun deleteAllocation(portfolioName: String, allocationId: Long) {
        val alloc = portfolioRepository.findPortfolioById(allocationId)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "配比不存在: $allocationId"
            )
        portfolioRepository.deleteHoldingByAllocationId(allocationId)
        portfolioRepository.deletePortfolio(allocationId)
    }

    // ==================== 净值走势 ====================

    fun getNavHistory(portfolioName: String, days: Int): NavHistoryResponse {
        val results = portfolioRepository.findNavHistory(portfolioName, days)
        return NavHistoryResponse(
            dates = results.map { it.first },
            values = results.map { roundTo2(it.second) }
        )
    }

    // ==================== 入金 ====================

    /**
     * 入金：直接加到现金资产（CASH）的 current_shares。
     * 不再按目标比例分配给各资产。
     */
    fun deposit(portfolioName: String, amount: Double) {
        require(amount > 0) { "入金金额必须大于 0" }

        val holdings = portfolioRepository.findHoldingsByPortfolioName(portfolioName)
        if (holdings.isEmpty()) {
            throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "组合不存在: $portfolioName"
            )
        }

        // 找到 CASH 资产
        val cashEntry = holdings.firstOrNull { (alloc, _) -> alloc.assetType == "CASH" }
            ?: throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "组合中没有现金资产，无法入金"
            )

        val cashHolding = cashEntry.second
            ?: throw AppException(
                code = ErrorCode.INTERNAL_ERROR,
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                message = "现金资产缺少持仓记录"
            )

        // 直接增加现金份额
        val newShares = cashHolding.currentShares + amount.toLong()
        portfolioRepository.saveHolding(cashHolding.copy(currentShares = newShares))

        // 记录当日净值快照
        val navAfter = calculateNav(portfolioRepository.findHoldingsByPortfolioName(portfolioName))
        snapshotNav(portfolioName, navAfter)
    }

    // ==================== 出金 ====================

    /** 出金：手动指定卖出资产和股数 */
    fun withdraw(portfolioName: String, allocationId: Long, shares: Long) {
        require(shares > 0) { "卖出股数必须大于 0" }
        require(shares % LOT_SIZE == 0L) { "卖出股数必须是 ${LOT_SIZE} 的整数倍" }

        val alloc = portfolioRepository.findPortfolioById(allocationId)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "资产不存在: $allocationId"
            )

        val holding = portfolioRepository.findHoldingByAllocationId(allocationId)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "持仓不存在: $allocationId"
            )

        require(holding.currentShares >= shares) {
            "持有股数不足: 当前 ${holding.currentShares} 股，尝试卖出 $shares 股"
        }

        // 更新持仓
        portfolioRepository.saveHolding(holding.copy(currentShares = holding.currentShares - shares))

        // 记录快照
        val navAfter = calculateNav(portfolioRepository.findHoldingsByPortfolioName(portfolioName))
        snapshotNav(portfolioName, navAfter)
    }

    // ==================== 再平衡 ====================

    /**
     * 再平衡预览：计算当前总净值 → 各资产目标市值 → 差值得出买卖建议。
     * 仅返回操作建议，不实际修改持仓。
     */
    fun rebalancePreview(portfolioName: String): RebalanceResult {
        val holdings = portfolioRepository.findHoldingsByPortfolioName(portfolioName)
        val nav = calculateNav(holdings)

        val pricedHoldings = holdings.filter { (alloc, holding) ->
            holding?.currentPrice != null && holding.currentPrice!! > 0
        }
        if (pricedHoldings.isEmpty()) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "没有可用的资产价格，无法计算再平衡"
            )
        }

        // 归一化目标比例（排除 CASH，CASH 不参与再平衡买卖）
        val nonCashHoldings = pricedHoldings.filter { (alloc, _) -> alloc.assetType != "CASH" }
        val totalTargetRatio = nonCashHoldings.sumOf { (alloc, _) -> alloc.targetRatio }
        val operations = mutableListOf<RebalanceOperation>()
        val navBefore = nav

        for ((alloc, holding) in nonCashHoldings) {
            val price = holding!!.currentPrice!!
            val normalizedRatio = if (totalTargetRatio > 0) alloc.targetRatio / totalTargetRatio else 0.0
            val targetMarketValue = nav * normalizedRatio
            val currentMarketValue = holding.currentShares * price
            val diff = targetMarketValue - currentMarketValue

            if (abs(diff) < price * LOT_SIZE) continue  // 差异不足一个交易单位，跳过

            if (diff > 0) {
                // 建议买入
                val buyShares = floor(diff / price / LOT_SIZE).toLong() * LOT_SIZE
                if (buyShares > 0) {
                    operations.add(
                        RebalanceOperation(
                            allocationId = alloc.id ?: 0,
                            code = alloc.code,
                            name = alloc.name,
                            action = "BUY",
                            shares = buyShares,
                            price = price,
                            amount = roundTo2(buyShares * price)
                        )
                    )
                }
            } else {
                // 建议卖出
                val sellShares = floor(abs(diff) / price / LOT_SIZE).toLong() * LOT_SIZE
                val maxSellable = holding.currentShares
                val actualSell = minOf(sellShares, maxSellable)
                if (actualSell > 0) {
                    operations.add(
                        RebalanceOperation(
                            allocationId = alloc.id ?: 0,
                            code = alloc.code,
                            name = alloc.name,
                            action = "SELL",
                            shares = actualSell,
                            price = price,
                            amount = roundTo2(actualSell * price)
                        )
                    )
                }
            }
        }

        return RebalanceResult(
            navBefore = roundTo2(navBefore),
            navAfter = roundTo2(navBefore),  // 预览不改变净值
            operations = operations
        )
    }

    /**
     * 执行再平衡操作：根据用户确认的操作列表，实际修改持仓并记录快照。
     */
    fun rebalanceExecute(portfolioName: String, operations: List<RebalanceExecuteRequest>): RebalanceResult {
        val holdings = portfolioRepository.findHoldingsByPortfolioName(portfolioName)
        val navBefore = calculateNav(holdings)

        val executedOps = mutableListOf<RebalanceOperation>()

        for (op in operations) {
            val holding = portfolioRepository.findHoldingByAllocationId(op.allocationId)
                ?: throw AppException(
                    code = ErrorCode.ENTRY_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                    message = "持仓不存在: allocationId=${op.allocationId}"
                )
            val alloc = portfolioRepository.findPortfolioById(op.allocationId)
                ?: throw AppException(
                    code = ErrorCode.ENTRY_NOT_FOUND,
                    status = HttpStatus.NOT_FOUND,
                    message = "配比不存在: ${op.allocationId}"
                )

            val updatedHolding = when (op.type.uppercase()) {
                "BUY" -> holding.copy(currentShares = holding.currentShares + op.shares)
                "SELL" -> {
                    require(holding.currentShares >= op.shares) {
                        "持有股数不足: 当前 ${holding.currentShares} 股，尝试卖出 ${op.shares} 股"
                    }
                    holding.copy(currentShares = holding.currentShares - op.shares)
                }
                else -> throw AppException(
                    code = ErrorCode.INVALID_REQUEST,
                    status = HttpStatus.BAD_REQUEST,
                    message = "无效的操作类型: ${op.type}（应为 BUY 或 SELL）"
                )
            }
            portfolioRepository.saveHolding(updatedHolding)

            val price = holding.currentPrice ?: 0.0
            executedOps.add(
                RebalanceOperation(
                    allocationId = op.allocationId,
                    code = alloc.code,
                    name = alloc.name,
                    action = op.type.uppercase(),
                    shares = op.shares,
                    price = price,
                    amount = roundTo2(op.shares * price)
                )
            )
        }

        // 记录当日净值快照
        val navAfter = calculateNav(portfolioRepository.findHoldingsByPortfolioName(portfolioName))
        snapshotNav(portfolioName, navAfter)

        return RebalanceResult(
            navBefore = roundTo2(navBefore),
            navAfter = roundTo2(navAfter),
            operations = executedOps
        )
    }

    // ==================== 价格同步（tushare） ====================

    /**
     * 通过 tushare 同步组合中 ETF/STOCK 资产的最新收盘价（跳过 CASH）。
     */
    fun syncPrices(portfolioName: String, codes: List<String>?): SyncPricesResponse {
        val allAllocs = portfolioRepository.findPortfolioByName(portfolioName)
        if (allAllocs.isEmpty()) {
            throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "组合不存在: $portfolioName"
            )
        }

        // 过滤非 CASH 资产
        val nonCash = allAllocs.filter { it.assetType != "CASH" }
        val targetAllocs = if (codes.isNullOrEmpty()) {
            nonCash
        } else {
            nonCash.filter { it.code in codes }
        }

        if (targetAllocs.isEmpty()) {
            return SyncPricesResponse(synced = 0, details = emptyList())
        }

        val details = mutableListOf<SyncPriceDetail>()

        // 批量调用 fund_daily
        val prices = try {
            tushareService.fetchLatestPrices(targetAllocs.map { it.code })
        } catch (e: AppException) {
            return SyncPricesResponse(
                synced = 0,
                details = listOf(SyncPriceDetail(
                    code = "ALL",
                    name = "批量请求失败",
                    price = 0.0,
                    date = LocalDate.now().toString(),
                    error = e.message ?: "未知错误"
                ))
            )
        }

        val priceMap = prices.associateBy { it.code }
        for (alloc in targetAllocs) {
            val tusharePrice = priceMap[alloc.code]
            if (tusharePrice != null) {
                val holding = portfolioRepository.findHoldingByAllocationId(alloc.id!!)
                if (holding != null) {
                    // 更新持仓当前价格
                    portfolioRepository.saveHolding(holding.copy(currentPrice = tusharePrice.price))
                    // 记录价格历史
                    portfolioRepository.saveStockPrice(
                        StockPrice(
                            holdingId = holding.id!!,
                            source = "tushare",
                            price = tusharePrice.price,
                            recordedAt = tusharePrice.tradeDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                        )
                    )
                    details.add(
                        SyncPriceDetail(
                            code = alloc.code,
                            name = alloc.name,
                            price = tusharePrice.price,
                            date = tusharePrice.tradeDate.toString()
                        )
                    )
                }
            }
        }

        // 同步完成后保存当日净值快照
        if (details.any { it.error == null }) {
            val nav = calculateNav(portfolioRepository.findHoldingsByPortfolioName(portfolioName))
            snapshotNav(portfolioName, nav)
        }

        return SyncPricesResponse(
            synced = details.count { it.error == null },
            details = details
        )
    }

    // ==================== 内部工具方法 ====================

    /** 记录当日净值快照（每个资产一条） */
    private fun snapshotNav(portfolioName: String, nav: Double) {
        val holdings = portfolioRepository.findHoldingsByPortfolioName(portfolioName)
        val now = Instant.now()
        for ((alloc, holding) in holdings) {
            val marketValue = if (holding?.currentPrice != null) holding.currentShares * holding.currentPrice!! else 0.0
            val ratio = if (nav > 0) marketValue / nav else 0.0
            portfolioRepository.saveSnapshot(
                PortfolioSnapshot(
                    snapshotTime = now,
                    portfolioId = alloc.id ?: 0,
                    code = alloc.code,
                    amount = marketValue,
                    ratio = roundTo4(ratio)
                )
            )
        }
    }

    private fun roundTo2(value: Double): Double = round(value * 100) / 100
    private fun roundTo4(value: Double): Double = round(value * 10000) / 10000

    private fun formatPercent(value: Double): String {
        val pct = round(value * 10000) / 100
        return "${(pct * 100).toInt() / 100.0}%"
    }

    // ==================== 种子数据 ====================

    /**
     * 初始化种子数据：创建"投资组合 1"含 4 个 ETF + 1 个 CASH，并生成 60 天模拟快照。
     */
    fun seedDefaultPortfolio() {
        val existing = portfolioRepository.findPortfolioByName("投资组合 1")
        if (existing.isNotEmpty()) return  // 已有数据则跳过

        val seedAssets = listOf(
            Triple("510300", "沪深300 ETF", 0.30),
            Triple("518880", "黄金 ETF", 0.20),
            Triple("159915", "创业板 ETF", 0.20),
            Triple("512880", "证券 ETF", 0.20),
            Triple("CASH", "现金", 0.10)
        )

        val now = Instant.now()
        val savedAllocs = mutableListOf<Pair<Portfolio, PortfolioHolding>>()

        for ((code, name, ratio) in seedAssets) {
            val alloc = portfolioRepository.savePortfolio(
                Portfolio(
                    portfolioName = "投资组合 1",
                    code = code,
                    name = name,
                    assetType = if (code == "CASH") "CASH" else "ETF",
                    targetRatio = ratio,
                    createdAt = now
                )
            )
            // 种子初始持仓: 每资产约 10000 元市值，1000 股
            val initPrice = if (code == "CASH") 1.0 else 10.0
            val initShares = if (code == "CASH") 10000L else 1000L
            val holding = portfolioRepository.saveHolding(
                PortfolioHolding(
                    allocationId = alloc.id!!,
                    currentShares = initShares,
                    currentPrice = initPrice
                )
            )
            savedAllocs.add(alloc to holding)
        }

        // 生成 60 天模拟快照（种子初始净值约 50000）
        val baseNav = 50000.0
        for (dayOffset in 59 downTo 0) {
            val snapshotTime = now.minusSeconds(dayOffset.toLong() * 86400)
            val nav = baseNav * (1.0 + dayOffset * 0.0005)  // 每天略微增长
            for ((alloc, holding) in savedAllocs) {
                val marketValue = nav * alloc.targetRatio
                portfolioRepository.saveSnapshot(
                    PortfolioSnapshot(
                        snapshotTime = snapshotTime,
                        portfolioId = alloc.id ?: 0,
                        code = alloc.code,
                        amount = marketValue,
                        ratio = alloc.targetRatio
                    )
                )
            }
        }
    }
}

// ==================== 响应 DTO ====================

data class PortfolioSummary(
    val portfolioName: String,
    val assetCount: Int,
    val totalNav: Double
)

data class PortfolioOverview(
    val portfolioName: String,
    val totalNav: Double,
    val totalInvested: Double,
    val totalReturn: Double,
    val returnRate: Double,
    val deviation: Double,
    val deviationDetail: String,
    val ratioWarning: String?,
    val assets: List<AssetOverview>
)

data class AssetOverview(
    val allocationId: Long,
    val code: String,
    val name: String,
    val assetType: String,
    val shares: Long,
    val price: Double?,
    val marketValue: Double,
    val actualRatio: Double,
    val targetRatio: Double,
    val deviation: Double
)

data class NavHistoryResponse(
    val dates: List<String>,
    val values: List<Double>
)

data class RebalanceResult(
    val navBefore: Double,
    val navAfter: Double,
    val operations: List<RebalanceOperation>
)

data class RebalanceOperation(
    val allocationId: Long,
    val code: String,
    val name: String,
    val action: String,      // BUY / SELL
    val shares: Long,
    val price: Double,
    val amount: Double
)

data class SyncPricesResponse(
    val synced: Int,
    val details: List<SyncPriceDetail>
)

data class SyncPriceDetail(
    val code: String,
    val name: String,
    val price: Double? = null,
    val date: String? = null,
    val error: String? = null
)

/** 再平衡执行请求中的单条操作 */
data class RebalanceExecuteRequest(
    val allocationId: Long,
    val type: String,    // BUY / SELL
    val shares: Long,
    val note: String? = null   // 用户备注（可选）
)
