package site.hanabii.fireworks.infra._config

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 为每个 HTTP 请求生成全局 traceId，存入 request attribute、MDC 和响应头。
 */
@Component
@Order(1)
class TraceIdFilter : Filter {

    companion object {
        const val TRACE_ID_ATTR = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        val traceId = UUID.randomUUID().toString().take(8)
        httpRequest.setAttribute(TRACE_ID_ATTR, traceId)
        httpResponse.setHeader(TRACE_ID_HEADER, traceId)

        try {
            MDC.put(TRACE_ID_ATTR, traceId)
            chain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID_ATTR)
        }
    }
}
