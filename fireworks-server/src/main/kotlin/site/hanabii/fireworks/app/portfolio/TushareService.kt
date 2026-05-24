package site.hanabii.fireworks.app.portfolio

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * tushare 行情数据服务。
 * 负责调用 tushare HTTP API 获取 ETF 每日收盘价。
 * 免费版 fund_daily 限频 1 次/小时，批量拉取所有资产。
 *
 * fund_basic / stock_basic 每日限 5 次调用，故缓存到内存，
 * 首次请求拉取全量数据，后续搜索从缓存过滤。
 */
@Service
class TushareService(
    @Value("\${tushare.token}") private val token: String,
    @Value("\${tushare.base-url}") private val baseUrl: String
) {
    private val logger = LoggerFactory.getLogger(TushareService::class.java)
    private val objectMapper = ObjectMapper()

    /** 15s 超时的 RestTemplate */
    private val restTemplate: RestTemplate = RestTemplate(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(15_000)
            setReadTimeout(15_000)
        }
    )

    // ==================== 搜索数据缓存 ====================
    // fund_basic / stock_basic 每日仅 5 次调用，必须缓存避免每次按键都请求

    private data class CachedList(
        val items: List<TushareSearchResult>,
        val fetchDate: LocalDate
    )

    private val cacheLock = ReentrantLock()
    @Volatile private var fundCache: CachedList? = null
    @Volatile private var stockCache: CachedList? = null

    /**
     * 将资产代码映射为 tushare ts_code。
     * 5/6 开头 → .SH（上交所），0/1/2/3 开头 → .SZ（深交所）。
     * 若已包含 "." 后缀则原样返回。
     */
    fun toTsCode(code: String): String {
        if (code.contains(".")) return code
        require(code.length == 6) { "无效的资产代码: $code，应为 6 位数字" }
        return when (code.first()) {
            '5', '6' -> "$code.SH"
            '0', '1', '2', '3' -> "$code.SZ"
            else -> throw IllegalArgumentException("无法识别的资产代码: $code（期望以 5/6/0/1/2/3 开头）")
        }
    }

    /**
     * 批量从 tushare 获取多个 ETF 的最新收盘价。
     * 使用 fund_daily 接口（免费），单次请求合并所有代码。
     * @param codes 资产代码列表（如 ["510300", "518880"]，自动映射为 ts_code）
     * @return 价格信息列表（无数据的代码不包含在结果中）
     */
    fun fetchLatestPrices(codes: List<String>): List<TusharePrice> {
        if (codes.isEmpty()) return emptyList()
        val tsCodes = codes.map { toTsCode(it) }
        val today = LocalDate.now()
        val startDate = today.minusDays(5).format(DATE_FMT)
        val endDate = today.format(DATE_FMT)

        val requestBody = mapOf(
            "api_name" to "fund_daily",
            "token" to token,
            "params" to mapOf(
                "ts_code" to tsCodes.joinToString(","),
                "start_date" to startDate,
                "end_date" to endDate
            ),
            "fields" to "ts_code,trade_date,close"
        )

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(objectMapper.writeValueAsString(requestBody), headers)

        val responseBody: String = try {
            restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String::class.java).body
                ?: throw AppException(
                    code = ErrorCode.INTERNAL_ERROR,
                    status = HttpStatus.BAD_GATEWAY,
                    message = "tushare 返回空响应"
                )
        } catch (e: RestClientException) {
            logger.error("tushare 网络请求失败: codes=$codes", e)
            throw AppException(
                code = ErrorCode.INTERNAL_ERROR,
                status = HttpStatus.BAD_GATEWAY,
                message = "tushare 网络请求超时或失败: ${e.message}"
            )
        }

        val root = objectMapper.readTree(responseBody)

        // tushare 顶层 code 非 0 即为错误
        val respCode = root.get("code")?.asInt() ?: -1
        if (respCode != 0) {
            val msg = root.get("msg")?.asText() ?: "未知错误"
            logger.error("tushare API 返回错误: respCode=$respCode, msg=$msg, codes=$codes")
            throw AppException(
                code = ErrorCode.INTERNAL_ERROR,
                status = HttpStatus.BAD_GATEWAY,
                message = "tushare 错误 ($respCode): $msg"
            )
        }

        val data = root.get("data")
        val items = data?.get("items")
        if (items == null || !items.isArray || items.size() == 0) {
            logger.info("tushare fund_daily 返回空数据（非交易日或数据未更新）: codes=$codes")
            return emptyList()
        }

        // items 按日期倒序，同一 ts_code 取最新一条
        val codeToTsCode = codes.associateBy { toTsCode(it) }
        val results = mutableListOf<TusharePrice>()
        val seen = mutableSetOf<String>()
        for (i in 0 until items.size()) {
            val item = items[i]
            val tsCodeFromResp = item[0].asText()
            if (tsCodeFromResp in seen) continue
            seen.add(tsCodeFromResp)
            val originalCode = codeToTsCode.entries.firstOrNull { it.value == tsCodeFromResp }?.key ?: continue
            val tradeDate = item[1].asText()
            val closePrice = item[2].asDouble()
            results.add(TusharePrice(
                code = originalCode,
                tsCode = tsCodeFromResp,
                price = closePrice,
                tradeDate = LocalDate.parse(tradeDate, DATE_FMT)
            ))
        }

        logger.info("tushare fund_daily 获取价格: ${results.size} 个资产")
        return results
    }

    /**
     * 代码联想搜索：根据 keyword 在缓存的 fund/stock 全量数据中模糊匹配。
     * 首次调用时从 tushare 拉取全量数据缓存，当天后续调用直接从缓存过滤。
     */
    fun searchAsset(keyword: String): List<TushareSearchResult> {
        if (keyword.isBlank()) return emptyList()

        val keywordLower = keyword.lowercase()
        val results = mutableListOf<TushareSearchResult>()

        for (item in getFundCache()) {
            if (item.code.contains(keywordLower) || item.name.lowercase().contains(keywordLower)) {
                results.add(item)
                if (results.size >= 20) return results
            }
        }

        for (item in getStockCache()) {
            if (item.code.contains(keywordLower) || item.name.lowercase().contains(keywordLower)) {
                results.add(item)
                if (results.size >= 20) return results
            }
        }

        return results
    }

    /** 获取 ETF 缓存数据，过期自动刷新 */
    private fun getFundCache(): List<TushareSearchResult> {
        val cached = fundCache
        if (cached != null && cached.fetchDate == LocalDate.now()) {
            return cached.items
        }
        return cacheLock.withLock {
            // 双重检查
            val inside = fundCache
            if (inside != null && inside.fetchDate == LocalDate.now()) {
                return@withLock inside.items
            }
            val items = fetchAllFund()
            fundCache = CachedList(items, LocalDate.now())
            logger.info("fund_basic 缓存已刷新: ${items.size} 条")
            items
        }
    }

    /** 获取股票缓存数据，过期自动刷新 */
    private fun getStockCache(): List<TushareSearchResult> {
        val cached = stockCache
        if (cached != null && cached.fetchDate == LocalDate.now()) {
            return cached.items
        }
        return cacheLock.withLock {
            val inside = stockCache
            if (inside != null && inside.fetchDate == LocalDate.now()) {
                return@withLock inside.items
            }
            val items = fetchAllStocks()
            stockCache = CachedList(items, LocalDate.now())
            logger.info("stock_basic 缓存已刷新: ${items.size} 条")
            items
        }
    }

    /** 从 tushare 拉取全量 ETF 数据（上交所+深交所） */
    private fun fetchAllFund(): List<TushareSearchResult> {
        val results = mutableListOf<TushareSearchResult>()
        // 上交所 ETF（market=E）和深交所 ETF（market=SE）
        for (market in listOf("E", "SE")) {
            try {
                val requestBody = mapOf(
                    "api_name" to "fund_basic",
                    "token" to token,
                    "params" to mapOf("market" to market),
                    "fields" to "ts_code,name"
                )
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                val entity = HttpEntity(objectMapper.writeValueAsString(requestBody), headers)
                val responseBody = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String::class.java).body
                    ?: continue
                results.addAll(parseAllResults(responseBody, "ETF"))
            } catch (e: Exception) {
                logger.warn("tushare fund_basic(market=$market) 拉取失败: ${e.message}")
            }
        }
        return results.distinctBy { it.code }
    }

    /** 从 tushare 拉取全量上市股票数据 */
    private fun fetchAllStocks(): List<TushareSearchResult> {
        return try {
            val requestBody = mapOf(
                "api_name" to "stock_basic",
                "token" to token,
                "params" to mapOf("list_status" to "L"),
                "fields" to "ts_code,name"
            )
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val entity = HttpEntity(objectMapper.writeValueAsString(requestBody), headers)
            val responseBody = restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String::class.java).body
                ?: return emptyList()
            parseAllResults(responseBody, "STOCK")
        } catch (e: Exception) {
            logger.warn("tushare stock_basic 拉取失败: ${e.message}")
            emptyList()
        }
    }

    /** 解析 tushare 全量数据响应为 SearchResult 列表（不做关键词过滤） */
    private fun parseAllResults(responseBody: String, type: String): List<TushareSearchResult> {
        val root = objectMapper.readTree(responseBody)
        val respCode = root.get("code")?.asInt() ?: -1
        if (respCode != 0) {
            val msg = root.get("msg")?.asText() ?: "未知错误"
            logger.warn("tushare $type 返回错误: code=$respCode, msg=$msg")
            return emptyList()
        }

        val data = root.get("data")
        val items = data?.get("items")
        if (items == null || !items.isArray || items.size() == 0) return emptyList()

        val results = mutableListOf<TushareSearchResult>()
        for (i in 0 until items.size()) {
            val item = items[i]
            val tsCode = item[0].asText()
            val name = item[1].asText()
            val shortCode = tsCode.substringBefore(".")
            results.add(TushareSearchResult(code = shortCode, name = name, type = type))
        }
        return results
    }

    companion object {
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}

/** tushare 返回的单条价格数据 */
data class TusharePrice(
    val code: String,       // 原始资产代码（如 "510300"）
    val tsCode: String,     // tushare ts_code（如 "510300.SH"）
    val price: Double,      // 收盘价
    val tradeDate: LocalDate // 交易日
)

/** tushare 搜索结果 */
data class TushareSearchResult(
    val code: String,
    val name: String,
    val type: String   // ETF / STOCK
)
