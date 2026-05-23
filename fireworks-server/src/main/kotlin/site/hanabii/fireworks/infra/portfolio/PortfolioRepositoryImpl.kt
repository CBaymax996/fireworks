package site.hanabii.fireworks.infra.portfolio

import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.asc
import org.ktorm.dsl.delete
import org.ktorm.dsl.desc
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.greaterEq
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.portfolio.Portfolio
import site.hanabii.fireworks.domain.portfolio.PortfolioHolding
import site.hanabii.fireworks.domain.portfolio.PortfolioRepository
import site.hanabii.fireworks.domain.portfolio.PortfolioSnapshot
import site.hanabii.fireworks.domain.portfolio.StockPrice
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * 投资组合 V2 仓储实现，四表模型: portfolio / portfolio_holding / stock_history / portfolio_snapshot
 */
@Repository
class PortfolioRepositoryImpl(
    private val database: Database
) : PortfolioRepository {

    // ==================== 组合管理（portfolio 表） ====================

    override fun findAllPortfolioNames(): List<String> {
        return database.from(PortfolioDO)
            .select(PortfolioDO.portfolioName)
            .map { row -> row[PortfolioDO.portfolioName] ?: "" }
            .filter { it.isNotBlank() }
            .distinct()
    }

    override fun findPortfolioByName(name: String): List<Portfolio> {
        return database.from(PortfolioDO)
            .select()
            .where { PortfolioDO.portfolioName eq name }
            .map { it.toPortfolio() }
    }

    override fun findPortfolioById(id: Long): Portfolio? {
        return database.from(PortfolioDO)
            .select()
            .where { PortfolioDO.id eq id }
            .map { it.toPortfolio() }
            .firstOrNull()
    }

    override fun savePortfolio(portfolio: Portfolio): Portfolio {
        val now = Instant.now()
        return if (portfolio.id == null) {
            database.insert(PortfolioDO) {
                set(it.portfolioName, portfolio.portfolioName)
                set(it.code, portfolio.code)
                set(it.name, portfolio.name)
                set(it.assetType, portfolio.assetType)
                set(it.targetRatio, portfolio.targetRatio)
                set(it.createdAt, now)
            }
            val lastId = lastInsertId()
            findPortfolioById(lastId) ?: throw IllegalStateException("Portfolio insert succeeded but cannot be loaded")
        } else {
            database.update(PortfolioDO) {
                set(it.portfolioName, portfolio.portfolioName)
                set(it.code, portfolio.code)
                set(it.name, portfolio.name)
                set(it.assetType, portfolio.assetType)
                set(it.targetRatio, portfolio.targetRatio)
                where { it.id eq portfolio.id!! }
            }
            portfolio
        }
    }

    override fun updatePortfolioRatio(id: Long, targetRatio: Double): Portfolio {
        database.update(PortfolioDO) {
            set(it.targetRatio, targetRatio)
            where { it.id eq id }
        }
        return findPortfolioById(id) ?: throw IllegalStateException("Portfolio not found: $id")
    }

    override fun deletePortfolio(id: Long) {
        database.delete(PortfolioDO) { it.id eq id }
    }

    override fun deletePortfolioByName(name: String) {
        val allocationIds = findPortfolioByName(name).mapNotNull { it.id }
        for (allocId in allocationIds) {
            database.delete(PortfolioSnapshotDO) { it.portfolioId eq allocId }
            val holding = findHoldingByAllocationId(allocId)
            if (holding?.id != null) {
                database.delete(StockHistoryDO) { it.holdingId eq holding.id!! }
            }
            database.delete(PortfolioHoldingDO) { it.allocationId eq allocId }
        }
        database.delete(PortfolioDO) { it.portfolioName eq name }
    }

    override fun renamePortfolioByName(oldName: String, newName: String) {
        database.update(PortfolioDO) {
            set(it.portfolioName, newName)
            where { it.portfolioName eq oldName }
        }
    }

    // ==================== 持仓（portfolio_holding 表） ====================

    override fun findHoldingByAllocationId(allocationId: Long): PortfolioHolding? {
        return database.from(PortfolioHoldingDO)
            .select()
            .where { PortfolioHoldingDO.allocationId eq allocationId }
            .map { it.toPortfolioHolding() }
            .firstOrNull()
    }

    override fun findHoldingsByPortfolioName(name: String): List<Pair<Portfolio, PortfolioHolding?>> {
        val allocs = findPortfolioByName(name)
        return allocs.map { alloc ->
            val holding = findHoldingByAllocationId(alloc.id!!)
            alloc to holding
        }
    }

    override fun saveHolding(holding: PortfolioHolding): PortfolioHolding {
        val now = Instant.now()
        return if (holding.id == null) {
            database.insert(PortfolioHoldingDO) {
                set(it.allocationId, holding.allocationId)
                set(it.currentShares, holding.currentShares)
                set(it.currentPrice, holding.currentPrice)
                set(it.updatedAt, now)
            }
            val lastId = lastInsertId()
            holding.copy(id = lastId, updatedAt = now)
        } else {
            database.update(PortfolioHoldingDO) {
                set(it.allocationId, holding.allocationId)
                set(it.currentShares, holding.currentShares)
                set(it.currentPrice, holding.currentPrice)
                set(it.updatedAt, now)
                where { it.id eq holding.id!! }
            }
            holding.copy(updatedAt = now)
        }
    }

    override fun deleteHoldingByAllocationId(allocationId: Long) {
        val holding = findHoldingByAllocationId(allocationId)
        if (holding?.id != null) {
            database.delete(StockHistoryDO) { it.holdingId eq holding.id!! }
        }
        database.delete(PortfolioHoldingDO) { it.allocationId eq allocationId }
    }

    // ==================== 价格历史（stock_history 表） ====================

    override fun findStockHistory(holdingId: Long, days: Int): List<StockPrice> {
        return database.from(StockHistoryDO)
            .select()
            .where { StockHistoryDO.holdingId eq holdingId }
            .orderBy(StockHistoryDO.recordedAt.desc())
            .map { it.toStockPrice() }
            .take(days)
            .reversed()
    }

    override fun saveStockPrice(price: StockPrice): StockPrice {
        database.insert(StockHistoryDO) {
            set(it.holdingId, price.holdingId)
            set(it.source, price.source)
            set(it.price, price.price)
            set(it.recordedAt, price.recordedAt)
        }
        val lastId = lastInsertId()
        return price.copy(id = lastId)
    }

    // ==================== 快照（portfolio_snapshot 表） ====================

    override fun findSnapshotsByPortfolioName(name: String, days: Int): List<PortfolioSnapshot> {
        val allocIds = findPortfolioByName(name).mapNotNull { it.id }
        if (allocIds.isEmpty()) return emptyList()
        val cutoff = Instant.now().minusSeconds(days.toLong() * 86400)
        val cutoffStr = DateTimeFormatter.ISO_INSTANT.format(cutoff)
        val allocIdList = allocIds.joinToString(",")

        val sql = """
            SELECT id, snapshot_time, portfolio_id, code, amount, ratio
            FROM portfolio_snapshot
            WHERE portfolio_id IN ($allocIdList)
              AND snapshot_time >= ?
            ORDER BY snapshot_time ASC
        """.trimIndent()

        return database.useConnection { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cutoffStr)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<PortfolioSnapshot>()
                    while (rs.next()) {
                        results.add(
                            PortfolioSnapshot(
                                id = rs.getLong("id"),
                                snapshotTime = rs.getTimestamp("snapshot_time").toInstant(),
                                portfolioId = rs.getLong("portfolio_id"),
                                code = rs.getString("code"),
                                amount = rs.getDouble("amount"),
                                ratio = rs.getDouble("ratio")
                            )
                        )
                    }
                    results
                }
            }
        }
    }

    override fun findNavHistory(name: String, days: Int): List<Pair<String, Double>> {
        val allocIds = findPortfolioByName(name).mapNotNull { it.id }
        if (allocIds.isEmpty()) return emptyList()
        val cutoff = Instant.now().minusSeconds(days.toLong() * 86400)
        val cutoffStr = DateTimeFormatter.ISO_INSTANT.format(cutoff)
        val allocIdList = allocIds.joinToString(",")

        val sql = """
            SELECT strftime('%Y-%m-%d', snapshot_time) as snap_date, 
                   SUM(amount) as total_nav
            FROM portfolio_snapshot
            WHERE portfolio_id IN ($allocIdList)
              AND snapshot_time >= ?
            GROUP BY snap_date
            ORDER BY snap_date ASC
        """.trimIndent()

        return database.useConnection { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cutoffStr)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Pair<String, Double>>()
                    while (rs.next()) {
                        results.add(rs.getString(1) to rs.getDouble(2))
                    }
                    results
                }
            }
        }
    }

    override fun saveSnapshot(snapshot: PortfolioSnapshot): PortfolioSnapshot {
        database.insert(PortfolioSnapshotDO) {
            set(it.snapshotTime, snapshot.snapshotTime)
            set(it.portfolioId, snapshot.portfolioId)
            set(it.code, snapshot.code)
            set(it.amount, snapshot.amount)
            set(it.ratio, snapshot.ratio)
        }
        val lastId = lastInsertId()
        return snapshot.copy(id = lastId)
    }

    // ==================== 内部工具 ====================

    private fun lastInsertId(): Long {
        return database.useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                    if (rs.next()) rs.getLong(1)
                    else throw IllegalStateException("Failed to get last insert rowid")
                }
            }
        }
    }
}
