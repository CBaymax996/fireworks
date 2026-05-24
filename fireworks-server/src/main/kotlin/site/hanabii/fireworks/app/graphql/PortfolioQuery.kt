package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.portfolio.PortfolioService
import site.hanabii.fireworks.app.portfolio.TushareService
import site.hanabii.fireworks.domain.portfolio.Portfolio

/**
 * 投资组合查询 — GraphQL Query resolver。
 */
@Component
class PortfolioQuery(
    private val portfolioService: PortfolioService,
    private val tushareService: TushareService
) {
    @GraphQLDescription("列出所有投资组合概要")
    fun portfolios(): List<PortfolioSummaryGQL> {
        return portfolioService.listPortfolios().map {
            PortfolioSummaryGQL(
                id = it.portfolioName,  // 使用组合名作为 ID
                name = it.portfolioName,
                totalNav = it.totalNav,
                assetCount = it.assetCount
            )
        }
    }

    @GraphQLDescription("获取组合概览（含各资产详情、收益率、偏离度）")
    fun portfolio(name: String): PortfolioOverviewGQL {
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

    @GraphQLDescription("获取组合的资产配比方案")
    fun allocation(name: String): List<AllocationGQL> {
        return portfolioService.getAllocation(name).map {
            AllocationGQL(
                id = it.id.toString(),
                code = it.code,
                name = it.name,
                assetType = it.assetType,
                targetRatio = it.targetRatio
            )
        }
    }

    @GraphQLDescription("获取组合净值走势")
    fun navHistory(name: String, days: Int = 60): NavHistoryGQL {
        val history = portfolioService.getNavHistory(name, days)
        return NavHistoryGQL(
            portfolioName = name,
            data = history.dates.zip(history.values).map { (date, nav) ->
                NavPointGQL(date = date, nav = nav)
            }
        )
    }

    @GraphQLDescription("通过关键字搜索 tushare 资产（ETF/股票）")
    fun tushareSearch(keyword: String): List<TushareSearchResultGQL> {
        return tushareService.searchAsset(keyword).map {
            TushareSearchResultGQL(
                code = it.code,
                name = it.name,
                assetType = it.type
            )
        }
    }
}

// ==================== GraphQL 类型映射 ====================

/** GraphQL 组合概要 */
data class PortfolioSummaryGQL(
    val id: String,
    val name: String,
    val totalNav: Double,
    val assetCount: Int
)

/** GraphQL 组合概览（含持仓） */
data class PortfolioOverviewGQL(
    val id: String,
    val name: String,
    val totalNav: Double,
    val totalCost: Double,
    val totalProfit: Double,
    val totalProfitRate: Double,
    val cash: Double,
    val holdings: List<PortfolioHoldingGQL>
)

/** GraphQL 持仓明细 */
data class PortfolioHoldingGQL(
    val allocationId: Long,
    val code: String,
    val name: String,
    val assetType: String,
    val targetRatio: Double,
    val actualRatio: Double,
    val shares: Int,
    val avgCost: Double,
    val currentPrice: Double?,
    val marketValue: Double?,
    val profit: Double?,
    val profitRate: Double?
)

/** GraphQL 资产配比 */
data class AllocationGQL(
    val id: String,
    val code: String,
    val name: String,
    val assetType: String,
    val targetRatio: Double
)

/** GraphQL 净值历史 */
data class NavHistoryGQL(
    val portfolioName: String,
    val data: List<NavPointGQL>
)

/** GraphQL 净值点 */
data class NavPointGQL(
    val date: String,
    val nav: Double
)

/** GraphQL tushare 搜索结果 */
data class TushareSearchResultGQL(
    val code: String,
    val name: String,
    val assetType: String
)
