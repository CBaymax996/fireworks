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

/**
 * tushare 行情数据服务。
 * 负责调用 tushare HTTP API 获取 ETF 每日收盘价。
 * 免费版 fund_daily 限频 1 次/小时，批量拉取所有资产。
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

    /**
     * 将资产代码映射为 tushare ts_code。
     * 以 5 开头的 6 位代码 → .SH（上交所 ETF），以 0/3 开头的 6 位代码 → .SZ（深交所）。
     * 若已包含 "." 后缀则原样返回。
     */
    fun toTsCode(code: String): String {
        if (code.contains(".")) return code
        require(code.length == 6) { "无效的资产代码: $code，应为 6 位数字" }
        return when (code.first()) {
            '5' -> "$code.SH"
            '0', '3' -> "$code.SZ"
            else -> throw IllegalArgumentException("无法识别的资产代码: $code（期望以 5/0/3 开头）")
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
     * 代码联想搜索：根据 keyword 搜索 ETF（fund_basic + stock_basic）。
     * 先在 fund_basic 中搜索上交所 ETF（market=E），再在 stock_basic 中搜索股票。
     * @param keyword 搜索关键词（代码或名称片段）
     * @return 匹配的资产列表
     */
    fun searchAsset(keyword: String): List<TushareSearchResult> {
        if (keyword.isBlank()) return emptyList()

        val results = mutableListOf<TushareSearchResult>()

        // 1. 搜索 ETF（fund_basic，上交所 ETF market=E）
        try {
            val etfResults = searchFundBasic(keyword)
            results.addAll(etfResults)
        } catch (e: Exception) {
            logger.warn("搜索 fund_basic 失败: ${e.message}", e)
        }

        // 2. 搜索股票（stock_basic）
        try {
            val stockResults = searchStockBasic(keyword)
            results.addAll(stockResults)
        } catch (e: Exception) {
            logger.warn("搜索 stock_basic 失败: ${e.message}", e)
        }

        // 去重（按 code）
        return results.distinctBy { it.code }
    }

    /** 搜索 fund_basic（ETF） */
    private fun searchFundBasic(keyword: String): List<TushareSearchResult> {
        val requestBody = mapOf(
            "api_name" to "fund_basic",
            "token" to token,
            "params" to mapOf("market" to "E"),
            "fields" to "ts_code,name"
        )

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(objectMapper.writeValueAsString(requestBody), headers)

        val responseBody: String = try {
            restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String::class.java).body
                ?: return emptyList()
        } catch (e: RestClientException) {
            logger.error("tushare fund_basic 请求失败: keyword=$keyword", e)
            return emptyList()
        }

        return parseSearchResults(responseBody, keyword, "ETF")
    }

    /** 搜索 stock_basic（股票） */
    private fun searchStockBasic(keyword: String): List<TushareSearchResult> {
        val requestBody = mapOf(
            "api_name" to "stock_basic",
            "token" to token,
            "params" to mapOf("list_status" to "L"),
            "fields" to "ts_code,name"
        )

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(objectMapper.writeValueAsString(requestBody), headers)

        val responseBody: String = try {
            restTemplate.exchange(baseUrl, HttpMethod.POST, entity, String::class.java).body
                ?: return emptyList()
        } catch (e: RestClientException) {
            logger.error("tushare stock_basic 请求失败: keyword=$keyword", e)
            return emptyList()
        }

        return parseSearchResults(responseBody, keyword, "STOCK")
    }

    /** 解析 tushare 搜索结果，按 code 或 name 模糊匹配 */
    private fun parseSearchResults(responseBody: String, keyword: String, type: String): List<TushareSearchResult> {
        val root = objectMapper.readTree(responseBody)
        val respCode = root.get("code")?.asInt() ?: -1
        if (respCode != 0) return emptyList()

        val data = root.get("data")
        val items = data?.get("items")
        if (items == null || !items.isArray || items.size() == 0) return emptyList()

        val results = mutableListOf<TushareSearchResult>()
        val keywordLower = keyword.lowercase()

        for (i in 0 until items.size()) {
            val item = items[i]
            val tsCode = item[0].asText()
            val name = item[1].asText()

            // 从 ts_code 提取短代码（去掉 .SH / .SZ 后缀）
            val shortCode = tsCode.substringBefore(".")

            // 模糊匹配：关键词匹配代码或名称
            if (shortCode.contains(keywordLower) || name.lowercase().contains(keywordLower)) {
                results.add(TushareSearchResult(
                    code = shortCode,
                    name = name,
                    type = type
                ))
            }

            if (results.size >= 20) break  // 限制结果数量
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
