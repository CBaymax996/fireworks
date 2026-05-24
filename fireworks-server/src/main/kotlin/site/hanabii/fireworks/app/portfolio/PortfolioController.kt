package site.hanabii.fireworks.app.portfolio

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.domain.portfolio.Portfolio
import org.springframework.http.HttpStatus

/**
 * 投资组合 V2 REST 控制器。
 * 支持多组合管理、配比方案、持仓操作、tushare 价格同步。
 * 无 /{name} 的旧路径默认操作 "投资组合 1"。
 */
@RestController
@RequestMapping("/api")
class PortfolioController(
    private val portfolioService: PortfolioService,
    private val tushareService: TushareService
) {

    // ==================== 组合列表管理（无 name 前缀） ====================

    /** GET /api/portfolios — 列出所有组合 */
    @GetMapping("/portfolios")
    fun listPortfolios(): List<PortfolioSummary> {
        return portfolioService.listPortfolios()
    }

    /** POST /api/portfolios — 创建组合 */
    @PostMapping("/portfolios")
    fun createPortfolio(@Valid @RequestBody req: CreatePortfolioRequest): Map<String, String> {
        portfolioService.createPortfolio(req.portfolioName)
        return mapOf("message" to "组合已创建: ${req.portfolioName}")
    }

    /** DELETE /api/portfolios/{name} — 删除组合 */
    @DeleteMapping("/portfolios/{name}")
    fun deletePortfolio(@PathVariable name: String): Map<String, String> {
        portfolioService.deletePortfolio(name)
        return mapOf("message" to "组合已删除: $name")
    }

    /** PUT /api/portfolio/{name}/rename — 重命名组合 */
    @PutMapping("/portfolio/{name}/rename")
    fun renamePortfolio(
        @PathVariable name: String,
        @Valid @RequestBody req: RenamePortfolioRequest
    ): Map<String, String> {
        portfolioService.renamePortfolio(name, req.newName)
        return mapOf("message" to "组合已重命名: ${name} → ${req.newName}")
    }

    // ==================== 组合概览 ====================

    /** GET /api/portfolio/{name} — 组合概览 */
    @GetMapping("/portfolio/{name}")
    fun getPortfolio(@PathVariable name: String): PortfolioOverview {
        return portfolioService.getPortfolio(name)
    }

    /** GET /api/portfolio — 默认组合概览（兼容旧 API） */
    @GetMapping("/portfolio")
    fun getDefaultPortfolio(): PortfolioOverview {
        return portfolioService.getPortfolio("投资组合 1")
    }

    // ==================== 配比管理 ====================

    /** GET /api/portfolio/{name}/allocation — 获取配比方案 */
    @GetMapping("/portfolio/{name}/allocation")
    fun getAllocation(@PathVariable name: String): List<Portfolio> {
        return portfolioService.getAllocation(name)
    }

    /** POST /api/portfolio/{name}/allocation — 添加资产 */
    @PostMapping("/portfolio/{name}/allocation")
    fun addAllocation(
        @PathVariable name: String,
        @Valid @RequestBody req: AddAllocationRequest
    ): Map<String, String> {
        portfolioService.addAllocation(name, req.code, req.name, req.assetType ?: "ETF", req.targetRatio)
        return mapOf("message" to "资产已添加: ${req.code} ${req.name}")
    }

    /** PUT /api/portfolio/{name}/allocation/{id} — 修改配比 */
    @PutMapping("/portfolio/{name}/allocation/{id}")
    fun updateAllocation(
        @PathVariable name: String,
        @PathVariable id: Long,
        @Valid @RequestBody req: UpdateAllocationRequest
    ): Map<String, String> {
        portfolioService.updateAllocation(name, id, req.targetRatio)
        return mapOf("message" to "配比已更新: ${req.targetRatio}")
    }

    /** PUT /api/portfolio/{name}/allocations — 批量修改配比 */
    @PutMapping("/portfolio/{name}/allocations")
    fun batchUpdateAllocations(
        @PathVariable name: String,
        @Valid @RequestBody req: BatchUpdateAllocationsRequest
    ): Map<String, String> {
        portfolioService.batchUpdateAllocations(name, req.items.map { it.allocationId to it.targetRatio })
        return mapOf("message" to "配比已批量更新")
    }

    /** DELETE /api/portfolio/{name}/allocation/{id} — 删除资产 */
    @DeleteMapping("/portfolio/{name}/allocation/{id}")
    fun deleteAllocation(
        @PathVariable name: String,
        @PathVariable id: Long
    ): Map<String, String> {
        portfolioService.deleteAllocation(name, id)
        return mapOf("message" to "资产已删除")
    }

    // ==================== 净值走势 ====================

    /** GET /api/portfolio/{name}/snapshot?days=60 — 净值走势 */
    @GetMapping("/portfolio/{name}/snapshot")
    fun getNavHistory(
        @PathVariable name: String,
        @RequestParam(defaultValue = "60") @Min(1) @Max(365) days: Int
    ): NavHistoryResponse {
        return portfolioService.getNavHistory(name, days)
    }

    /** GET /api/portfolio/nav-history — 默认净值走势（兼容旧 API） */
    @GetMapping("/portfolio/nav-history")
    fun getDefaultNavHistory(
        @RequestParam(defaultValue = "60") @Min(1) @Max(365) days: Int
    ): NavHistoryResponse {
        return portfolioService.getNavHistory("投资组合 1", days)
    }

    // ==================== 操作 ====================

    /** POST /api/portfolio/{name}/deposit — 入金 */
    @PostMapping("/portfolio/{name}/deposit")
    fun deposit(
        @PathVariable name: String,
        @Valid @RequestBody req: DepositRequest
    ): Map<String, String> {
        portfolioService.deposit(name, req.amount)
        return mapOf("message" to "入金成功: ¥${req.amount}")
    }

    /** POST /api/portfolio/deposit — 默认入金（兼容旧 API） */
    @PostMapping("/portfolio/deposit")
    fun defaultDeposit(@Valid @RequestBody req: DepositRequest): Map<String, String> {
        portfolioService.deposit("投资组合 1", req.amount)
        return mapOf("message" to "入金成功: ¥${req.amount}")
    }

    /** POST /api/portfolio/{name}/withdraw — 出金 */
    @PostMapping("/portfolio/{name}/withdraw")
    fun withdraw(
        @PathVariable name: String,
        @Valid @RequestBody req: WithdrawRequest
    ): Map<String, String> {
        portfolioService.withdraw(name, req.allocationId, req.shares)
        return mapOf("message" to "出金成功: ${req.shares} 股")
    }

    /** POST /api/portfolio/withdraw — 默认出金（兼容旧 API） */
    @PostMapping("/portfolio/withdraw")
    fun defaultWithdraw(@Valid @RequestBody req: WithdrawRequest): Map<String, String> {
        portfolioService.withdraw("投资组合 1", req.allocationId, req.shares)
        return mapOf("message" to "出金成功: ${req.shares} 股")
    }

    /** POST /api/portfolio/{name}/rebalance — 再平衡预览（仅返回建议，不修改持仓） */
    @PostMapping("/portfolio/{name}/rebalance")
    fun rebalance(@PathVariable name: String): RebalanceResult {
        return portfolioService.rebalancePreview(name)
    }

    /** POST /api/portfolio/rebalance — 默认再平衡预览（兼容旧 API） */
    @PostMapping("/portfolio/rebalance")
    fun defaultRebalance(): RebalanceResult {
        return portfolioService.rebalancePreview("投资组合 1")
    }

    /** POST /api/portfolio/{name}/rebalance/execute — 执行再平衡操作 */
    @PostMapping("/portfolio/{name}/rebalance/execute")
    fun executeRebalance(
        @PathVariable name: String,
        @Valid @RequestBody req: RebalanceExecuteRequestWrapper
    ): RebalanceResult {
        return portfolioService.rebalanceExecute(name, req.operations)
    }

    /** POST /api/portfolio/rebalance/execute — 默认执行再平衡（兼容旧 API） */
    @PostMapping("/portfolio/rebalance/execute")
    fun defaultExecuteRebalance(
        @Valid @RequestBody req: RebalanceExecuteRequestWrapper
    ): RebalanceResult {
        return portfolioService.rebalanceExecute("投资组合 1", req.operations)
    }

    // ==================== tushare 搜索 ====================

    /** GET /api/tushare/search?keyword=510300 — 代码联想搜索 */
    @GetMapping("/tushare/search")
    fun searchAsset(@RequestParam keyword: String): List<TushareSearchResult> {
        if (keyword.isBlank()) return emptyList()
        return try {
            tushareService.searchAsset(keyword.trim())
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            throw AppException(
                code = ErrorCode.INTERNAL_ERROR,
                status = HttpStatus.BAD_GATEWAY,
                message = "tushare 搜索失败: ${e.message}"
            )
        }
    }

    /** POST /api/portfolio/{name}/sync-prices — 同步 tushare 价格 */
    @PostMapping("/portfolio/{name}/sync-prices")
    fun syncPrices(
        @PathVariable name: String,
        @RequestBody(required = false) req: SyncPricesRequest?
    ): SyncPricesResponse {
        return portfolioService.syncPrices(name, req?.codes)
    }

    /** POST /api/portfolio/sync-prices — 默认同步（兼容旧 API） */
    @PostMapping("/portfolio/sync-prices")
    fun defaultSyncPrices(
        @RequestBody(required = false) req: SyncPricesRequest?
    ): SyncPricesResponse {
        return portfolioService.syncPrices("投资组合 1", req?.codes)
    }
}

// ==================== 请求 DTO ====================

data class CreatePortfolioRequest(
    @field:NotNull
    val portfolioName: String
)

data class RenamePortfolioRequest(
    @field:NotNull
    val newName: String
)

data class AddAllocationRequest(
    @field:NotNull
    val code: String,
    @field:NotNull
    val name: String,
    val assetType: String? = "ETF",
    @field:Positive(message = "目标配比必须大于 0")
    val targetRatio: Double
)

data class UpdateAllocationRequest(
    @field:Positive(message = "目标配比必须大于 0")
    val targetRatio: Double
)

data class BatchAllocationItem(
    val allocationId: Long,
    val targetRatio: Double
)

data class BatchUpdateAllocationsRequest(
    val items: List<BatchAllocationItem>
)

data class DepositRequest(
    @field:Positive(message = "入金金额必须大于 0")
    @field:NotNull
    val amount: Double
)

data class WithdrawRequest(
    @field:Positive(message = "资产 ID 必须大于 0")
    @field:NotNull
    val allocationId: Long,
    @field:Positive(message = "卖出股数必须大于 0")
    @field:NotNull
    val shares: Long
)

data class SyncPricesRequest(
    val codes: List<String>? = null
)

/** 再平衡执行请求包装 */
data class RebalanceExecuteRequestWrapper(
    @field:NotNull
    val operations: List<RebalanceExecuteRequest>
)
