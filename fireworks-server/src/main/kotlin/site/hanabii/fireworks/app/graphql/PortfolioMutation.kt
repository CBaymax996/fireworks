package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.portfolio.PortfolioService
import site.hanabii.fireworks.app.portfolio.RebalanceExecuteRequest
import site.hanabii.fireworks.domain.portfolio.Portfolio

/**
 * 投资组合变更 — GraphQL Mutation resolver。
 */
@Component
class PortfolioMutation(
    private val portfolioService: PortfolioService
) {
    @GraphQLDescription("创建新投资组合")
    fun createPortfolio(name: String): PortfolioSummaryGQL {
        portfolioService.createPortfolio(name)
        val summaries = portfolioService.listPortfolios()
        val created = summaries.find { it.portfolioName == name }
            ?: throw site.hanabii.fireworks.app.AppException(
                code = site.hanabii.fireworks.app.ErrorCode.INTERNAL_ERROR,
                status = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                message = "组合创建后未找到: $name"
            )
        return PortfolioSummaryGQL(
            id = created.portfolioName,
            name = created.portfolioName,
            totalNav = created.totalNav,
            assetCount = created.assetCount
        )
    }

    @GraphQLDescription("删除投资组合")
    fun deletePortfolio(name: String): Boolean {
        portfolioService.deletePortfolio(name)
        return true
    }

    @GraphQLDescription("重命名投资组合")
    fun renamePortfolio(name: String, newName: String): Boolean {
        portfolioService.renamePortfolio(name, newName)
        return true
    }

    @GraphQLDescription("添加资产配比")
    fun addAllocation(
        name: String,
        code: String,
        assetName: String,
        assetType: String = "ETF",
        targetRatio: Double
    ): AllocationGQL {
        portfolioService.addAllocation(name, code, assetName, assetType, targetRatio)
        // 查找新添加的配比
        val allocs = portfolioService.getAllocation(name)
        val created = allocs.find { it.code == code }
            ?: throw site.hanabii.fireworks.app.AppException(
                code = site.hanabii.fireworks.app.ErrorCode.INTERNAL_ERROR,
                status = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                message = "配比添加后未找到: $code"
            )
        return AllocationGQL(
            id = created.id.toString(),
            code = created.code,
            name = created.name,
            assetType = created.assetType,
            targetRatio = created.targetRatio
        )
    }

    @GraphQLDescription("更新资产配比")
    fun updateAllocation(name: String, allocationId: Long, targetRatio: Double): Boolean {
        portfolioService.updateAllocation(name, allocationId, targetRatio)
        return true
    }

    @GraphQLDescription("删除资产配比")
    fun deleteAllocation(name: String, allocationId: Long): Boolean {
        portfolioService.deleteAllocation(name, allocationId)
        return true
    }

    @GraphQLDescription("入金（直接增加现金资产份额）")
    fun deposit(name: String, amount: Double): PortfolioOverviewGQL {
        portfolioService.deposit(name, amount)
        // 重新获取组合概览
        return buildPortfolioOverview(name)
    }

    @GraphQLDescription("出金（手动指定卖出资产和股数）")
    fun withdraw(name: String, allocationId: Long, shares: Int): PortfolioOverviewGQL {
        portfolioService.withdraw(name, allocationId, shares.toLong())
        return buildPortfolioOverview(name)
    }

    @GraphQLDescription("再平衡预览（仅返回建议，不修改持仓）")
    fun rebalancePreview(name: String): RebalanceResultGQL {
        val result = portfolioService.rebalancePreview(name)
        return RebalanceResultGQL(
            portfolioName = name,
            targetNav = result.navAfter,
            operations = result.operations.map { op ->
                RebalanceOpGQL(
                    allocationId = op.allocationId,
                    code = op.code,
                    name = op.name,
                    assetType = "",  // rebalance op 不含 assetType，用空字符串
                    targetRatio = 0.0,
                    currentRatio = 0.0,
                    diff = 0.0,
                    action = op.action,
                    shares = op.shares.toInt()
                )
            },
            executed = false
        )
    }

    @GraphQLDescription("执行再平衡操作（实际修改持仓）")
    fun rebalanceExecute(name: String, operations: List<RebalanceOpInput>): RebalanceResultGQL {
        val serviceOps = operations.map { op ->
            RebalanceExecuteRequest(
                allocationId = op.allocationId,
                type = op.type,
                shares = op.shares.toLong()
            )
        }
        val result = portfolioService.rebalanceExecute(name, serviceOps)
        return RebalanceResultGQL(
            portfolioName = name,
            targetNav = result.navAfter,
            operations = result.operations.map { op ->
                RebalanceOpGQL(
                    allocationId = op.allocationId,
                    code = op.code,
                    name = op.name,
                    assetType = "",
                    targetRatio = 0.0,
                    currentRatio = 0.0,
                    diff = 0.0,
                    action = op.action,
                    shares = op.shares.toInt()
                )
            },
            executed = true
        )
    }

    @GraphQLDescription("同步 tushare 价格（批量获取最新收盘价）")
    fun syncPrices(name: String, codes: List<String>? = null): SyncPricesResultGQL {
        val result = portfolioService.syncPrices(name, codes)
        return SyncPricesResultGQL(
            portfolioName = name,
            successCount = result.synced,
            totalCount = result.details.size,
            results = result.details.map { detail ->
                PriceSyncItemGQL(
                    code = detail.code,
                    name = detail.name,
                    price = detail.price,
                    error = detail.error
                )
            }
        )
    }

    // ==================== 辅助方法 ====================

    private fun buildPortfolioOverview(name: String): PortfolioOverviewGQL {
        val overview = portfolioService.getPortfolio(name)
        val cash = overview.assets.find { it.assetType == "CASH" }
        return PortfolioOverviewGQL(
            id = overview.portfolioName,
            name = overview.portfolioName,
            totalNav = overview.totalNav,
            totalCost = overview.totalInvested,
            totalProfit = overview.totalReturn,
            totalProfitRate = overview.returnRate,
            cash = cash?.marketValue ?: 0.0,
            holdings = overview.assets.map { asset ->
                PortfolioHoldingGQL(
                    allocationId = asset.allocationId,
                    code = asset.code,
                    name = asset.name,
                    assetType = asset.assetType,
                    targetRatio = asset.targetRatio,
                    actualRatio = asset.actualRatio,
                    shares = asset.shares.toInt(),
                    avgCost = asset.price ?: 0.0,
                    currentPrice = asset.price,
                    marketValue = asset.marketValue,
                    profit = asset.marketValue - (asset.shares * (asset.price ?: 0.0)),
                    profitRate = if (asset.shares > 0 && asset.price != null && asset.price > 0) {
                        (asset.marketValue / (asset.shares * asset.price) - 1)
                    } else 0.0
                )
            }
        )
    }
}

// ==================== GraphQL 类型映射 ====================

/** GraphQL 再平衡操作输入 */
data class RebalanceOpInput(
    val allocationId: Long,
    val type: String,   // "BUY" 或 "SELL"
    val shares: Int
)

/** GraphQL 再平衡操作 */
data class RebalanceOpGQL(
    val allocationId: Long,
    val code: String,
    val name: String,
    val assetType: String,
    val targetRatio: Double,
    val currentRatio: Double,
    val diff: Double,
    val action: String,
    val shares: Int
)

/** GraphQL 再平衡结果 */
data class RebalanceResultGQL(
    val portfolioName: String,
    val targetNav: Double,
    val operations: List<RebalanceOpGQL>,
    val executed: Boolean
)

/** GraphQL 价格同步结果 */
data class SyncPricesResultGQL(
    val portfolioName: String,
    val successCount: Int,
    val totalCount: Int,
    val results: List<PriceSyncItemGQL>
)

/** GraphQL 价格同步明细 */
data class PriceSyncItemGQL(
    val code: String,
    val name: String,
    val price: Double?,
    val error: String?
)
