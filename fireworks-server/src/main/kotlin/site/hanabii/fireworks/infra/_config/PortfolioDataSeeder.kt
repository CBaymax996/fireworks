package site.hanabii.fireworks.infra._config

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.DependsOn
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.portfolio.PortfolioService

/**
 * 应用启动后初始化种子数据。
 * 创建默认"投资组合 1"含 4 个 ETF + 1 个 CASH，以及 60 天模拟快照。
 */
@Component
@DependsOn("schemaInitializer")
class PortfolioDataSeeder(
    private val portfolioService: PortfolioService
) {

    @EventListener(ApplicationReadyEvent::class)
    @Order(2)
    fun seed() {
        portfolioService.seedDefaultPortfolio()
    }
}
