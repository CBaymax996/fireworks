<template>
  <div class="portfolio-page">
    <!-- ═══ Top Bar: 组合选择器 + 操作按钮 ═══ -->
    <div class="top-bar">
      <div class="top-bar-left">
        <!-- 组合下拉选择器 -->
        <div class="portfolio-selector" ref="selectorRef">
          <button class="portfolio-selector-btn" @click="toggleSelector">
            <span class="selector-label">{{ currentPortfolioName }} ▾</span>
          </button>
          <div class="selector-dropdown" v-if="showSelector">
            <div
              v-for="p in portfolios"
              :key="p.portfolioName"
              class="selector-item"
              :class="{ active: p.portfolioName === currentPortfolioName }"
              @click="switchPortfolio(p.portfolioName)"
            >
              <span class="selector-item-name">{{ p.portfolioName }}</span>
              <span class="selector-item-meta">{{ p.assetCount }} 资产 · ¥{{ formatNumber(p.totalNav) }}</span>
            </div>
            <!-- 新建/删除 -->
            <div class="selector-actions">
              <button class="selector-action-btn" @click="openNewPortfolioDialog">＋ 新建组合</button>
              <button
                class="selector-action-btn danger"
                :disabled="portfolios.length <= 1"
                @click="deleteCurrentPortfolio"
              >删除当前组合</button>
            </div>
          </div>
        </div>
        <button class="rename-portfolio-btn" title="重命名组合" @click="openRenameDialog">
          🖊️
        </button>
        <!-- 操作按钮 -->
        <div class="top-bar-actions">
          <button class="action-btn action-sm" @click="openModal('deposit')">➕</button>
          <button class="action-btn action-sm" @click="openModal('withdraw')">➖</button>
          <button class="action-btn action-lg" @click="handleRebalance()">⚖️ 再平衡</button>
        </div>
      </div>
    </div>

    <div class="container">
      <!-- ═══ Chart + Sidebar ═══ -->
      <div class="chart-section">
        <div class="chart-left">
          <div class="chart-title">📈 资产净值走势</div>
          <div class="chart-container" ref="chartRef"></div>
        </div>
        <div class="chart-right">
          <!-- Net Worth Card -->
          <div class="sidebar-networth">
            <div class="sn-label">总资产净值</div>
            <div class="sn-value">¥{{ formatNumber(portfolio.totalNav) }}</div>
            <div class="sn-meta">
              <span>累计投入 <strong>¥{{ formatNumber(portfolio.totalInvested) }}</strong></span>
              <span>累计收益 <strong>{{ returnSign }}{{ formatNumber(Math.abs(portfolio.totalReturn)) }}（{{ returnSign }}{{ formatPercent(portfolio.returnRate) }}）</strong></span>
            </div>
          </div>
          <!-- Deviation Alert -->
          <div class="sidebar-card">
            <div class="sc-label">偏离度告警</div>
            <div class="sc-value" :class="{ warn: portfolio.deviation > 0 }">{{ formatPercent(portfolio.deviation) }}</div>
            <div class="sc-sub">{{ portfolio.deviationDetail || '暂无告警' }}</div>
          </div>
        </div>
      </div>

      <!-- ═══ Asset Allocation Overview ═══ -->
      <div class="section-title">
        <span>资产配置概览</span>
        <div class="section-title-actions">
          <button class="add-asset-btn" @click="openAddAssetDialog">＋ 添加资产</button>
          <button class="add-asset-btn" @click="openAdjustRatioDrawer">调整比例</button>
        </div>
      </div>

      <!-- 双环饼图 -->
      <div class="pie-chart-wrapper">
        <div class="pie-chart-container" ref="pieChartRef"></div>
      </div>

      <div class="asset-grid">
        <div
          v-for="(asset, idx) in portfolio.assets"
          :key="asset.allocationId"
          class="asset-card"
          :style="Math.abs(asset.deviation) >= 0.05 ? { borderColor: '#fed7aa' } : {}"
        >
          <div class="card-header">
            <span class="name">
              <span class="asset-dot" :class="dotColors[idx % dotColors.length]" style="display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:6px;"></span>
              {{ asset.name }}
              <button class="edit-icon-btn" title="编辑资产" @click="openEditAssetDialog(asset)">🖊️</button>
            </span>
            <span class="code">{{ asset.code }}</span>
          </div>
          <div class="card-price">
            当前价：{{ asset.price ? '¥' + asset.price.toFixed(2) : '待 API 同步' }}
          </div>
          <div class="card-metrics">
            <div class="metric">
              <div class="metric-label">市值</div>
              <div class="metric-value">¥{{ formatNumber(asset.marketValue) }}</div>
            </div>
            <div class="metric">
              <div class="metric-label">{{ asset.code === 'CASH' ? '余额' : '持有' }}</div>
              <div class="metric-value">{{ asset.code === 'CASH' ? '¥' + formatNumber(asset.shares) : formatNumber(asset.shares) + ' 股' }}</div>
            </div>
          </div>
          <div class="card-ratio" @click.stop="startEditingRatio(asset)">
            <span v-if="editingRatioId !== asset.allocationId">
              实际 {{ formatPercent(asset.actualRatio) }} · 目标 {{ formatPercent(asset.targetRatio) }}
            </span>
            <span v-else class="ratio-edit-inline">
              <span class="ratio-edit-label">目标</span>
              <input
                type="range"
                v-model.number="editingRatioValue"
                class="ratio-slider"
                min="0"
                max="100"
                step="0.1"
                @focus="onRatioFocus"
                @blur="onRatioBlur(asset)"
              />
              <input
                v-model.number="editingRatioValue"
                class="ratio-input"
                type="number"
                min="0"
                max="100"
                step="0.1"
                @focus="onRatioFocus"
                @keydown.enter.prevent="saveRatio(asset)"
                @keydown.escape.prevent="cancelRatioEdit"
                @blur="onRatioBlur(asset)"
              />%
              <span v-if="ratioSaveStatus === 'success'" class="ratio-save-ok">✓</span>
              <span v-if="ratioSaveStatus === 'error'" class="ratio-save-err" :title="ratioSaveMessage">✗</span>
            </span>
          </div>
          <span
            class="deviation-tag"
            :class="Math.abs(asset.deviation) >= 0.05 ? 'over' : 'under'"
          >
            {{ Math.abs(asset.deviation) >= 0.05 ? '⚠ ' : '' }}偏离 {{ formatPercentSigned(asset.deviation) }}
          </span>
        </div>
      </div>

      <!-- 总配比提示 -->
      <div v-if="totalRatioWarning" class="ratio-warning">
        ⚠️ 总目标配比 {{ formatPercent(totalTargetRatio) }}，请调整为 100%
      </div>

      <!-- ═══ Holdings Table ═══ -->
      <div class="compact-table-wrap">
        <div class="table-header">
          <h3>持仓明细</h3>
          <span class="table-subtitle">{{ portfolio.assets?.length || 0 }} 个资产 · 向下取整至 100 股</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>资产</th>
              <th>持仓</th>
              <th>当前价</th>
              <th>占比</th>
              <th>偏离</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(asset, idx) in portfolio.assets"
              :key="asset.allocationId"
              :class="{ 'warn-row': Math.abs(asset.deviation) >= 0.03 }"
            >
              <td>
                <span class="asset-dot" :class="dotColors[idx % dotColors.length]"></span>
                <strong>{{ asset.name }}</strong>
                <span class="asset-code-sub">{{ asset.code }}</span>
              </td>
              <td>{{ asset.code === 'CASH' ? '¥' + formatNumber(asset.shares) : formatNumber(asset.shares) + ' 股 · ¥' + formatNumber(asset.marketValue) }}</td>
              <td>
                <span v-if="asset.price" class="price-val">¥{{ asset.price.toFixed(2) }}</span>
                <span v-else class="price-ph">待同步</span>
              </td>
              <td>{{ formatPercent(asset.actualRatio) }} / {{ formatPercent(asset.targetRatio) }}</td>
              <td :class="deviationClass(asset.deviation)">{{ formatPercentSigned(asset.deviation) }}</td>
              <td>
                <span class="status-tag" :class="statusClass(asset.deviation)">{{ statusText(asset.deviation) }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ═══ Footer Bar ═══ -->
      <div class="footer-bar">
        <span>{{ footerMessage }}</span>
        <span class="footer-sync">数据源：tushare · 最后同步：{{ lastSync }} · <a href="#" @click.prevent="syncPrices" class="sync-link" :class="{ disabled: syncing }">{{ syncing ? '同步中…' : '🔄' }}</a></span>
      </div>
    </div>

    <!-- ═══════ MODALS ═══════ -->

    <!-- 新建组合弹窗 -->
    <div class="modal-overlay" :class="{ active: activeModal === 'newPortfolio' }" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <h3>✨ 新建投资组合</h3>
          <button class="modal-close" @click="closeModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>组合名称</label>
            <input
              type="text"
              class="form-input"
              v-model="newPortfolioName"
              placeholder="例如：我的投资组合 2"
              @keydown.enter.prevent="confirmNewPortfolio"
            />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="confirmNewPortfolio" :disabled="!newPortfolioName.trim() || newPortfolioLoading">
            {{ newPortfolioLoading ? '创建中…' : '确认创建' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 添加资产弹窗 -->
    <div class="modal-overlay" :class="{ active: activeModal === 'addAsset' }" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <h3>＋ 添加资产</h3>
          <button class="modal-close" @click="closeModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>资产类型</label>
            <select class="form-input" v-model="newAssetType">
              <option value="ETF">ETF/股票</option>
              <option value="CASH">现金 (CASH)</option>
            </select>
          </div>
          <div class="form-item">
            <label>代码</label>
            <div class="code-search-wrapper">
              <input
                type="text"
                class="form-input"
                v-model="newAssetCode"
                :placeholder="newAssetType === 'CASH' ? '自动设为 CASH' : '例如：510300（输入后自动联想）'"
                :disabled="newAssetType === 'CASH'"
                @input="onCodeInput"
                @focus="onCodeFocus"
                @blur="onCodeBlur"
                autocomplete="off"
              />
              <div class="code-search-dropdown" v-if="showCodeSuggestions && codeSuggestions.length > 0">
                <div
                  v-for="item in codeSuggestions"
                  :key="item.code"
                  class="code-suggestion-item"
                  @mousedown.prevent="selectCodeSuggestion(item)"
                >
                  <span class="code-suggestion-code">{{ item.code }}</span>
                  <span class="code-suggestion-name">{{ item.name }}</span>
                  <span class="code-suggestion-type">{{ item.type === 'ETF' ? 'ETF' : '股票' }}</span>
                </div>
              </div>
            </div>
          </div>
          <div class="form-item">
            <label>名称</label>
            <input
              type="text"
              class="form-input"
              v-model="newAssetName"
              :placeholder="newAssetType === 'CASH' ? '自动设为 现金' : '例如：沪深300 ETF'"
              :disabled="newAssetType === 'CASH'"
            />
          </div>
          <div class="form-item">
            <label>目标占比（%）</label>
            <input
              type="number"
              class="form-input"
              v-model.number="newAssetRatio"
              placeholder="例如：20"
              min="0"
              max="100"
              step="0.1"
            />
            <div class="form-hint">当前总配比：{{ formatPercent(totalTargetRatio) }}，新增后需 <= 100%</div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="confirmAddAsset" :disabled="addAssetLoading">
            {{ addAssetLoading ? '添加中…' : '确认添加' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 编辑资产弹窗 -->
    <div class="modal-overlay" :class="{ active: activeModal === 'editAsset' }" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <h3>🖊️ 编辑资产</h3>
          <button class="modal-close" @click="closeModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>名称</label>
            <input type="text" class="form-input" v-model="editingAsset.name" />
          </div>
          <div class="form-item">
            <label>代码</label>
            <input type="text" class="form-input" v-model="editingAsset.code" :disabled="editingAsset.code === 'CASH'" />
          </div>
          <div class="form-item">
            <label>目标占比（%）</label>
            <input
              type="number"
              class="form-input"
              v-model.number="editingAsset.targetRatioPercent"
              placeholder="例如：20"
              min="0"
              max="100"
              step="0.1"
            />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="confirmEditAsset" :disabled="editAssetLoading">
            {{ editAssetLoading ? '保存中…' : '保存修改' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Deposit Modal -->
    <div class="modal-overlay" :class="{ active: activeModal === 'deposit' }" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <h3>💰 手动加钱</h3>
          <button class="modal-close" @click="closeModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>入金金额（元）</label>
            <input type="number" class="form-input" v-model.number="depositAmount" placeholder="例如：10000" min="0" />
            <div class="form-hint">金额将加入现金账户</div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="confirmDeposit" :disabled="depositLoading">
            {{ depositLoading ? '处理中...' : '确认加钱' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Withdraw Modal -->
    <div class="modal-overlay" :class="{ active: activeModal === 'withdraw' }" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <h3>💸 手动减钱</h3>
          <button class="modal-close" @click="closeModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>选择卖出资产</label>
            <select class="form-input" v-model="withdrawAssetId">
              <option v-for="a in sellableAssets" :key="a.allocationId" :value="a.allocationId">
                {{ a.name }} ({{ a.code }}) — 持有 {{ formatNumber(a.shares) }} {{ a.code === 'CASH' ? '元' : '股' }}
              </option>
            </select>
          </div>
          <div class="form-item">
            <label>卖出数量（{{ selectedWithdrawAsset?.code === 'CASH' ? '元' : '100 股整数倍' }}）</label>
            <input
              type="number"
              class="form-input"
              v-model.number="withdrawShares"
              :placeholder="selectedWithdrawAsset?.code === 'CASH' ? '例如：1000' : '例如：1000'"
              :step="selectedWithdrawAsset?.code === 'CASH' ? 1 : 100"
              min="1"
            />
          </div>
          <div class="estimate-box">
            预估回笼资金：<strong>¥ {{ withdrawEstimate.toFixed(2) }}</strong>
            <span class="estimate-note">（价格待 API 同步）</span>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeModal">取消</button>
          <button class="btn btn-danger" @click="confirmWithdraw" :disabled="withdrawLoading">
            {{ withdrawLoading ? '处理中...' : '确认卖出' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Rebalance Modal -->
    <div class="modal-overlay" :class="{ active: activeModal === 'rebalance' }" @click.self="closeModal">
      <div class="modal" style="width:620px;">
        <div class="modal-header">
          <h3>⚖️ 再平衡建议</h3>
          <button class="modal-close" @click="closeModal">&times;</button>
        </div>
        <div class="modal-body">
          <p class="rebalance-intro">以下为各资产偏离目标的调整建议（不会自动执行）：</p>
          <table class="rebalance-table">
            <thead>
              <tr>
                <th class="text-left">操作</th>
                <th class="text-left">资产</th>
                <th class="text-right">建议数量</th>
                <th class="text-right">预估金额</th>
                <th class="text-right">实际成交股数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(op, i) in rebalanceOps" :key="i">
                <td>
                  <span :class="op.type === '卖出' ? 'op-sell' : 'op-buy'">{{ op.type }}</span>
                </td>
                <td>{{ op.name }}</td>
                <td class="text-right">{{ formatNumber(op.shares) }} 股</td>
                <td class="text-right">¥{{ formatNumber(op.amount) }}</td>
                <td class="text-right">
                  <input
                    type="number"
                    class="actual-shares-input"
                    v-model.number="op.actualShares"
                    :placeholder="op.shares"
                    min="0"
                    :step="100"
                    style="width:80px;text-align:right;"
                  />
                </td>
              </tr>
              <tr v-if="rebalanceOps.length === 0">
                <td colspan="5" class="text-center" style="padding:20px;color:#8d969e;">
                  当前无需再平衡，各资产已在目标范围内
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="rebalanceOps.length > 0" class="rebalance-note">
            交易均向下取整至 100 股 · 请在"实际成交股数"栏填写实际交易数量 · 留空则使用建议数量
          </div>
          <div class="form-item" style="margin-top:16px;">
            <label>实际执行备注（可选）</label>
            <textarea
              class="form-input"
              v-model="rebalanceNote"
              placeholder="记录实际卖出/买入的情况，例如：沪深300实际卖出 5000 股"
              rows="2"
            ></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeModal">关闭</button>
          <button
            class="btn btn-success"
            @click="confirmRebalance"
            :disabled="rebalanceLoading || rebalanceOps.length === 0"
          >
            {{ rebalanceLoading ? '记录中...' : '记录执行结果' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 重命名弹窗 -->
    <div class="modal-overlay" :class="{ active: activeModal === 'rename' }" @click.self="closeModal">
      <div class="modal" style="width:400px;">
        <div class="modal-header">
          <h3>🖊️ 重命名组合</h3>
          <button class="modal-close" @click="closeModal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>新名称</label>
            <input
              type="text"
              class="form-input"
              v-model="renameNewName"
              placeholder="输入新组合名称"
              @keydown.enter.prevent="confirmRename"
            />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="confirmRename" :disabled="!renameNewName.trim() || renameLoading">
            {{ renameLoading ? '保存中…' : '确认' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 资产分配器弹窗 -->
    <el-dialog
      v-model="showAllocator"
      title="调整配比"
      width="580px"
      :close-on-click-modal="true"
      destroy-on-close
    >
      <div class="allocator-body">

        <!-- 金条 + 分割线 -->
        <div class="allocator-bar-wrapper">
          <div class="allocator-bar" ref="allocBarRef">
            <template v-for="(seg, idx) in allocSegments" :key="idx">
              <div
                class="allocator-segment"
                :style="{ flex: seg.percent, background: seg.color }"
              >
                <span v-if="seg.percent >= 20" class="allocator-segment-label">{{ seg.percent }}%</span>
              </div>
              <div
                v-if="idx < allocSegments.length - 1"
                class="allocator-divider"
                @mousedown.prevent="onDividerMouseDown(idx, $event)"
              ></div>
            </template>
          </div>
        </div>

        <!-- 底部列表 -->
        <div class="allocator-list">
          <div
            v-for="(seg, idx) in allocSegments"
            :key="idx"
            class="allocator-row"
          >
            <span class="allocator-dot" :style="{ background: seg.color }"></span>
            <span class="allocator-name">{{ seg.name }}</span>
            <div class="allocator-input-wrap">
              <input
                class="allocator-input"
                type="number"
                :min="0"
                :max="100"
                :step="0.1"
                :value="seg.percent"
                @input="onPercentInput(idx, $event)"
              />
              <span class="allocator-unit">%</span>
            </div>
            <span class="allocator-value">¥{{ formatNumber(seg.value) }}</span>
          </div>
        </div>

        <!-- 仅在总和不为 100% 时提示 -->
        <div v-if="Math.abs(allocTotal - 100) > 0.01" class="allocator-warning">
          ⚠️ 当前配比总和为 {{ allocTotal.toFixed(1) }}%，{{ allocTotal > 100 ? '超出 100%，请调整' : '不足 100%，请调整' }}
        </div>
      </div>

      <template #footer>
        <div class="allocator-footer">
          <button class="btn" @click="showAllocator = false">取消</button>
          <button
            class="btn btn-primary"
            @click="saveAllocatorRatios"
            :disabled="allocatorSaving || Math.abs(allocTotal - 100) > 0.01"
          >
            {{ allocatorSaving ? '保存中…' : '保存' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

// ── Types ──
interface PortfolioSummary {
  portfolioName: string
  assetCount: number
  totalNav: number
}

interface Asset {
  allocationId: number
  code: string
  name: string
  assetType: string
  shares: number
  price: number | null
  marketValue: number
  actualRatio: number
  targetRatio: number
  deviation: number
}

interface PortfolioData {
  totalNav: number
  totalInvested: number
  totalReturn: number
  returnRate: number
  deviation: number
  deviationDetail: string
  assets: Asset[]
}

interface RebalanceOp {
  type: string
  name: string
  shares: number
  amount: number
  allocationId?: number
  action?: string
  actualShares?: number
}

// ── State ──
const currentPortfolioName = ref('投资组合 1')
const portfolios = ref<PortfolioSummary[]>([])
const showSelector = ref(false)
const selectorRef = ref<HTMLDivElement | null>(null)

const portfolio = reactive<PortfolioData>({
  totalNav: 0,
  totalInvested: 0,
  totalReturn: 0,
  returnRate: 0,
  deviation: 0,
  deviationDetail: '',
  assets: [],
})

// 配比内联编辑
const editingRatioId = ref<number | null>(null)
const editingRatioValue = ref(0)
const ratioInputRef = ref<HTMLInputElement | null>(null)
const ratioSaveStatus = ref<'success' | 'error' | 'saving' | null>(null)
const ratioSaveMessage = ref('')
let ratioBlurTimer: ReturnType<typeof setTimeout> | null = null

// 添加资产弹窗
const newAssetType = ref('ETF')
const newAssetCode = ref('')
const newAssetName = ref('')
const newAssetRatio = ref(0)

// 代码联想搜索
interface CodeSuggestion {
  code: string
  name: string
  type: string
}
const codeSuggestions = ref<CodeSuggestion[]>([])
const showCodeSuggestions = ref(false)
let codeSearchTimer: ReturnType<typeof setTimeout> | null = null
let codeSearchAbort: AbortController | null = null

// 编辑资产弹窗
const editingAsset = reactive({
  id: 0,
  name: '',
  code: '',
  targetRatioPercent: 0,
})

// 新建组合
const newPortfolioName = ref('')
const newPortfolioLoading = ref(false)

const chartRef = ref<HTMLDivElement | null>(null)
let chartInstance: any = null
let themeObserver: MutationObserver | null = null

function getTheme(): boolean {
  return document.documentElement.classList.contains('dark')
}

const navDates = ref<string[]>([])
const navValues = ref<number[]>([])

const activeModal = ref<string | null>(null)
const depositAmount = ref(20000)
const withdrawAssetId = ref<number | null>(null)
const withdrawShares = ref(1000)
const depositLoading = ref(false)
const withdrawLoading = ref(false)
const rebalanceLoading = ref(false)
const syncing = ref(false)
const addAssetLoading = ref(false)
const editAssetLoading = ref(false)

// 重命名
const renameNewName = ref('')
const renameLoading = ref(false)

// 饼图
const pieChartRef = ref<HTMLDivElement | null>(null)
let pieChartInstance: any = null

// 资产分配器
const showAllocator = ref(false)

const dotColors = ['blue', 'green', 'yellow', 'red', 'cyan', 'purple', 'orange', 'teal']

// ── Computed ──
const lastSync = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
})

const returnSign = computed(() => (portfolio.totalReturn >= 0 ? '+' : ''))

const totalTargetRatio = computed(() => {
  return portfolio.assets.reduce((sum, a) => sum + a.targetRatio, 0)
})

const totalRatioWarning = computed(() => {
  return Math.abs(totalTargetRatio.value - 1.0) > 0.001
})

const sellableAssets = computed(() => {
  return portfolio.assets.filter(a => a.shares > 0)
})

const selectedWithdrawAsset = computed(() => {
  return portfolio.assets.find(a => a.allocationId === withdrawAssetId.value) || null
})

const footerMessage = computed(() => {
  const overAsset = portfolio.assets.find((a) => a.deviation >= 0.05)
  if (overAsset) {
    return `${overAsset.name} 偏离目标配置超过 ${Math.abs((overAsset.deviation * 100).toFixed(1))}%，建议尽快执行再平衡`
  }
  return '各资产配置正常'
})

const nextRebalanceDate = computed(() => {
  const d = new Date()
  d.setMonth(d.getMonth() + 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-15`
})

const withdrawEstimate = computed(() => {
  const asset = selectedWithdrawAsset.value
  if (asset) {
    if (asset.code === 'CASH') return withdrawShares.value || 0
    if (asset.price) return asset.price * (withdrawShares.value || 0)
  }
  return 5.3 * (withdrawShares.value || 0)
})

const estimatedAllocation = computed(() => {
  const amount = depositAmount.value || 0
  return portfolio.assets.map((a) => {
    const alloc = amount * a.targetRatio
    const estPrice = a.price || (a.code === 'CASH' ? 1.0 : 5.0)
    const estShares = a.code === 'CASH' ? Math.round(alloc) : Math.floor(alloc / estPrice / 100) * 100
    return {
      name: a.name,
      targetRatio: a.targetRatio,
      amount: Math.round(alloc),
      estimatedShares: estShares,
    }
  })
})

const rebalanceOps = ref<RebalanceOp[]>([])
const rebalanceRemainder = ref(0)
const rebalanceNote = ref('')
const hasLoadedRebalance = ref(false)

// ── Helper functions ──
function formatNumber(n: number | null | undefined): string {
  if (n == null || isNaN(n)) return '0'
  return n.toLocaleString()
}

function formatPercent(n: number | null | undefined): string {
  if (n == null || isNaN(n)) return '0%'
  return (n * 100).toFixed(1) + '%'
}

function formatPercentSigned(n: number | null | undefined): string {
  if (n == null || isNaN(n)) return '0%'
  const v = n * 100
  return (v >= 0 ? '+' : '') + v.toFixed(1) + '%'
}

function deviationClass(d: number): string {
  return d >= 0.03 ? 'deviation-warn' : d <= -0.03 ? 'deviation-danger' : ''
}

function statusClass(d: number): string {
  if (Math.abs(d) >= 0.05) return d > 0 ? 'warn' : 'alert'
  if (Math.abs(d) >= 0.03) return d > 0 ? 'warn' : 'alert'
  return 'ok'
}

function statusText(d: number): string {
  if (Math.abs(d) >= 0.05) return d > 0 ? '需减持' : '需增持'
  if (Math.abs(d) >= 0.03) return d > 0 ? '偏多' : '偏少'
  return '正常'
}

// ── Portfolio Selector ──
function toggleSelector() {
  showSelector.value = !showSelector.value
}

function closeSelector(e: MouseEvent) {
  if (selectorRef.value && !selectorRef.value.contains(e.target as Node)) {
    showSelector.value = false
  }
}

async function switchPortfolio(name: string) {
  if (name === currentPortfolioName.value) {
    showSelector.value = false
    return
  }
  currentPortfolioName.value = name
  showSelector.value = false
  await fetchPortfolio()
  await fetchNavHistory()
}

async function openNewPortfolioDialog() {
  showSelector.value = false
  newPortfolioName.value = ''
  activeModal.value = 'newPortfolio'
}

async function confirmNewPortfolio() {
  const name = newPortfolioName.value.trim()
  if (!name) {
    ElMessage.warning('请输入组合名称')
    return
  }
  newPortfolioLoading.value = true
  try {
    await axios.post('/api/portfolios', { portfolioName: name })
    ElMessage.success('组合「' + name + '」创建成功')
    closeModal()
    await fetchPortfolios()
    currentPortfolioName.value = name
    await fetchPortfolio()
    await fetchNavHistory()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e.response?.data?.message || e.message))
  } finally {
    newPortfolioLoading.value = false
  }
}

async function deleteCurrentPortfolio() {
  if (portfolios.value.length <= 1) {
    ElMessage.warning('至少保留一个组合')
    return
  }
  showSelector.value = false
  try {
    await ElMessageBox.confirm(
      `确定要删除组合「${currentPortfolioName.value}」及其所有关联数据吗？此操作不可撤销。`,
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await axios.delete(`/api/portfolios/${encodeURIComponent(currentPortfolioName.value)}`)
    ElMessage.success('组合已删除')
    await fetchPortfolios()
    // 切换到第一个组合
    if (portfolios.value.length > 0) {
      currentPortfolioName.value = portfolios.value[0].portfolioName
      await fetchPortfolio()
      await fetchNavHistory()
    }
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('删除失败：' + (e.response?.data?.message || e.message))
  }
}

// ── Rename Portfolio ──
function openRenameDialog() {
  renameNewName.value = currentPortfolioName.value
  activeModal.value = 'rename'
  nextTick(() => {
    const input = document.querySelector('.modal-overlay.active .form-input') as HTMLInputElement
    if (input) { input.focus(); input.select() }
  })
}

async function confirmRename() {
  const newName = renameNewName.value.trim()
  if (!newName) {
    ElMessage.warning('请输入新名称')
    return
  }
  if (newName === currentPortfolioName.value) {
    closeModal()
    return
  }
  renameLoading.value = true
  try {
    await axios.put(`/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/rename`, {
      newName: newName,
    })
    ElMessage.success('组合已重命名为「' + newName + '」')
    closeModal()
    currentPortfolioName.value = newName
    await fetchPortfolios()
    await fetchPortfolio()
    await fetchNavHistory()
  } catch (e: any) {
    ElMessage.error('重命名失败：' + (e.response?.data?.message || e.message))
  } finally {
    renameLoading.value = false
  }
}

// ── Ratio Editing ──
function startEditingRatio(asset: Asset) {
  editingRatioId.value = asset.allocationId
  editingRatioValue.value = Math.round(asset.targetRatio * 1000) / 10
  ratioSaveStatus.value = null
  ratioSaveMessage.value = ''
  nextTick(() => {
    const input = document.querySelector('.ratio-input') as HTMLInputElement
    if (input) {
      input.focus()
      input.select()
    }
  })
}

async function saveRatio(asset: Asset) {
  const newRatio = (editingRatioValue.value || 0) / 100
  if (newRatio < 0 || newRatio > 1) {
    ElMessage.warning('配比需在 0%~100% 之间')
    return
  }
  ratioSaveStatus.value = 'saving'
  try {
    await axios.put(`/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/allocation/${asset.allocationId}`, {
      targetRatio: newRatio,
    })
    ratioSaveStatus.value = 'success'
    ratioSaveMessage.value = '已保存'
    await fetchPortfolio()
    editingRatioId.value = null
    setTimeout(() => { if (ratioSaveStatus.value === 'success') ratioSaveStatus.value = null }, 2000)
  } catch (e: any) {
    ratioSaveStatus.value = 'error'
    ratioSaveMessage.value = e.response?.data?.message || e.message
    setTimeout(() => { if (ratioSaveStatus.value === 'error') ratioSaveStatus.value = null }, 3000)
  }
}

function cancelRatioEdit() {
  if (ratioBlurTimer) {
    clearTimeout(ratioBlurTimer)
    ratioBlurTimer = null
  }
  editingRatioId.value = null
  ratioSaveStatus.value = null
}

function onRatioFocus() {
  if (ratioBlurTimer) {
    clearTimeout(ratioBlurTimer)
    ratioBlurTimer = null
  }
}

function onRatioBlur(asset: Asset) {
  ratioBlurTimer = setTimeout(() => {
    if (editingRatioId.value === asset.allocationId) {
      saveRatio(asset)
    }
  }, 150)
}

// ── Add Asset Dialog ──
function openAddAssetDialog() {
  newAssetType.value = 'ETF'
  newAssetCode.value = ''
  newAssetName.value = ''
  newAssetRatio.value = Math.round((1 - totalTargetRatio.value) * 1000) / 10
  if (newAssetRatio.value < 0) newAssetRatio.value = 0
  activeModal.value = 'addAsset'
}

async function confirmAddAsset() {
  const code = newAssetType.value === 'CASH' ? 'CASH' : newAssetCode.value.trim()
  const name = newAssetType.value === 'CASH' ? '现金' : newAssetName.value.trim()
  const ratio = (newAssetRatio.value || 0) / 100

  if (!code) {
    ElMessage.warning('请输入资产代码')
    return
  }
  if (!name) {
    ElMessage.warning('请输入资产名称')
    return
  }
  if (ratio <= 0 || ratio > 1) {
    ElMessage.warning('目标占比需在 0%~100% 之间')
    return
  }
  if (totalTargetRatio.value + ratio > 1.001) {
    ElMessage.warning('总配比不能超过 100%，当前总配比 ' + formatPercent(totalTargetRatio.value))
    return
  }

  addAssetLoading.value = true
  try {
    await axios.post(`/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/allocation`, {
      code,
      name,
      assetType: newAssetType.value,
      targetRatio: ratio,
    })
    ElMessage.success('资产「' + name + '」添加成功')
    closeModal()
    await fetchPortfolio()
  } catch (e: any) {
    ElMessage.error('添加失败：' + (e.response?.data?.message || e.message))
  } finally {
    addAssetLoading.value = false
  }
}

// ── Code Search (Autocomplete) ──
function onCodeInput() {
  if (newAssetType.value === 'CASH') {
    codeSuggestions.value = []
    showCodeSuggestions.value = false
    return
  }

  if (codeSearchTimer) clearTimeout(codeSearchTimer)
  if (codeSearchAbort) codeSearchAbort.abort()

  const keyword = newAssetCode.value.trim()
  if (keyword.length < 1) {
    codeSuggestions.value = []
    showCodeSuggestions.value = false
    return
  }

  codeSearchTimer = setTimeout(async () => {
    try {
      codeSearchAbort = new AbortController()
      const { data } = await axios.get<CodeSuggestion[]>('/api/tushare/search', {
        params: { keyword },
        signal: codeSearchAbort.signal,
      })
      codeSuggestions.value = data
      showCodeSuggestions.value = data.length > 0
    } catch (e: any) {
      if (e?.code !== 'ERR_CANCELED') {
        codeSuggestions.value = []
        showCodeSuggestions.value = false
      }
    }
  }, 300)
}

function onCodeFocus() {
  if (newAssetType.value !== 'CASH' && codeSuggestions.value.length > 0) {
    showCodeSuggestions.value = true
  }
}

function onCodeBlur() {
  // 延迟关闭，让 mousedown 先触发
  setTimeout(() => {
    showCodeSuggestions.value = false
  }, 200)
}

function selectCodeSuggestion(item: CodeSuggestion) {
  newAssetCode.value = item.code
  newAssetName.value = item.name
  codeSuggestions.value = []
  showCodeSuggestions.value = false
}

// ── Edit Asset Dialog ──
function openEditAssetDialog(asset: Asset) {
  editingAsset.id = asset.allocationId
  editingAsset.name = asset.name
  editingAsset.code = asset.code
  editingAsset.targetRatioPercent = Math.round(asset.targetRatio * 1000) / 10
  activeModal.value = 'editAsset'
}

async function confirmEditAsset() {
  const ratio = (editingAsset.targetRatioPercent || 0) / 100
  if (ratio <= 0 || ratio > 1) {
    ElMessage.warning('目标占比需在 0%~100% 之间')
    return
  }

  editAssetLoading.value = true
  try {
    await axios.put(`/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/allocation/${editingAsset.id}`, {
      name: editingAsset.name,
      code: editingAsset.code,
      targetRatio: ratio,
    })
    ElMessage.success('资产已更新')
    closeModal()
    await fetchPortfolio()
  } catch (e: any) {
    ElMessage.error('编辑失败：' + (e.response?.data?.message || e.message))
  } finally {
    editAssetLoading.value = false
  }
}

// ── 资产分配器 ──

interface AllocSegment {
  allocationId?: number
  name: string
  percent: number
  value: number
  color: string
}

const allocBarRef = ref<HTMLDivElement | null>(null)
const allocSegments = ref<AllocSegment[]>([])
const allocatorSaving = ref(false)
let allocDraggingIdx = -1
let allocBarRect: DOMRect | null = null

const allocTotal = computed(() =>
  allocSegments.value.reduce((s, seg) => s + seg.percent, 0)
)

function openAdjustRatioDrawer() {
  const colors = assetPieColors
  allocSegments.value = portfolio.assets.map((a, idx) => ({
    allocationId: (a as any).allocationId as number,
    name: a.name,
    color: colors[idx % colors.length],
    percent: Math.round(a.targetRatio * 1000) / 10,
    value: a.marketValue || 0,
  }))
  normalizeAllocPercent(0)
  showAllocator.value = true
}

// 标准化：以第 skip 个资产为调节池，使总和 = 100
function normalizeAllocPercent(skip: number) {
  const segs = allocSegments.value
  const total = segs.reduce((s, seg) => s + seg.percent, 0)
  if (Math.abs(total - 100) < 0.001) return
  // 将差值从非 skip 的资产中分摊
  const others = segs.filter((_, i) => i !== skip)
  const clamp = (v: number) => Math.max(0, Math.round(v * 10) / 10)
  if (others.length === 0) {
    segs[skip].percent = 100
    return
  }
  const diff = 100 - total
  let remaining = diff
  for (let i = 0; i < segs.length && Math.abs(remaining) > 0.001; i++) {
    if (i === skip) continue
    const share = clamp(diff / others.length)
    const newVal = clamp(segs[i].percent + (remaining > 0 ? Math.min(share, remaining) : Math.max(-share, remaining)))
    remaining -= (newVal - segs[i].percent)
    segs[i].percent = newVal
  }
  segs[skip].percent = clamp(segs[skip].percent + remaining)
}

// ── 拖拽分割线 ──
function onDividerMouseDown(idx: number, e: MouseEvent) {
  allocDraggingIdx = idx
  allocBarRect = (allocBarRef.value as HTMLElement).getBoundingClientRect()
  document.addEventListener('mousemove', onDividerMouseMove)
  document.addEventListener('mouseup', onDividerMouseUp)
  e.preventDefault()
}

function snapValue(val: number): number {
  const to1 = Math.round(val)
  if (Math.abs(val - to1) <= 0.25) return to1
  return val
}

function onDividerMouseMove(e: MouseEvent) {
  if (allocDraggingIdx < 0 || !allocBarRect) return
  const rect = allocBarRect
  const x = e.clientX - rect.left
  const pct = Math.max(0, Math.min(100, (x / rect.width) * 100))

  const segs = allocSegments.value
  const i = allocDraggingIdx

  const leftSum = segs.slice(0, i).reduce((s, seg) => s + seg.percent, 0)
  const rawLeft = Math.max(0, Math.round((pct - leftSum) * 10) / 10)
  const snappedLeft = snapValue(rawLeft)

  const othersSum = 100 - segs[i].percent - segs[i + 1].percent
  const newRight = Math.max(0, Math.round((100 - othersSum - snappedLeft) * 10) / 10)

  if (snappedLeft >= 0 && newRight >= 0) {
    segs[i].percent = snappedLeft
    segs[i + 1].percent = newRight
  }
}

function onDividerMouseUp() {
  allocDraggingIdx = -1
  allocBarRect = null
  document.removeEventListener('mousemove', onDividerMouseMove)
  document.removeEventListener('mouseup', onDividerMouseUp)
}

// ── 手动输入百分比 ──
function onPercentInput(idx: number, e: Event) {
  const raw = (e.target as HTMLInputElement).value
  if (raw === '' || raw === '-') return
  const val = parseFloat(raw)
  if (isNaN(val)) return

  const segs = allocSegments.value
  const old = segs[idx].percent
  const clamped = Math.max(0, Math.min(100, Math.round(val * 10) / 10))
  segs[idx].percent = clamped

  // 此消彼长：差值从其他资产分摊
  const diff = old - clamped
  const others = segs.filter((_, i) => i !== idx)
  if (others.length === 0) return

  let remaining = diff
  for (let i = 0; i < segs.length && Math.abs(remaining) > 0.001; i++) {
    if (i === idx) continue
    const share = remaining / segs.filter((_, j) => j !== idx && segs[j].percent + (remaining > 0 ? 1 : -1) >= 0).length
    // 简化：逐个消化
    if (remaining > 0) {
      const take = Math.min(remaining, segs[i].percent)
      segs[i].percent = Math.round((segs[i].percent - take) * 10) / 10
      remaining -= take
    } else {
      const give = Math.min(-remaining, 100 - segs[i].percent)
      segs[i].percent = Math.round((segs[i].percent + give) * 10) / 10
      remaining += give
    }
  }
  normalizeAllocPercent(idx)
}

async function saveAllocatorRatios() {
  // 保存前强制标准化：将浮点误差分摊到占比最大的资产，确保总和严格 = 100%
  const segs = allocSegments.value
  const total = segs.reduce((s, seg) => s + seg.percent, 0)
  if (Math.abs(total - 100) > 0.01) {
    const maxIdx = segs.reduce((maxI, seg, i) => seg.percent > segs[maxI].percent ? i : maxI, 0)
    segs[maxIdx].percent = Math.round((segs[maxIdx].percent + 100 - total) * 10) / 10
  }

  allocatorSaving.value = true
  try {
    const items = segs.map(seg => ({
      allocationId: seg.allocationId!,
      targetRatio: Math.round(seg.percent * 100) / 10000
    }))
    await axios.put(
      `/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/allocations`,
      { items }
    )
    ElMessage.success('配比已保存')
    showAllocator.value = false
    await fetchPortfolio()
  } catch (e: any) {
    ElMessage.error('保存配比失败：' + (e.response?.data?.message || e.message))
  } finally {
    allocatorSaving.value = false
  }
}

// ── Delete Allocation ──
async function deleteAllocation(asset: Asset) {
  if (asset.code === 'CASH') {
    ElMessage.warning('现金资产不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除资产「${asset.name}」（${asset.code}）吗？对应持仓数据也将被删除。`,
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    await axios.delete(`/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/allocation/${asset.allocationId}`)
    ElMessage.success('资产已删除')
    await fetchPortfolio()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('删除失败：' + (e.response?.data?.message || e.message))
  }
}

// ── Modal ──
function openModal(name: string) {
  activeModal.value = name
  if (name === 'rebalance') {
    loadRebalancePreview()
  }
}

function closeModal() {
  activeModal.value = null
}

// ── API calls ──
async function fetchPortfolios() {
  try {
    const { data } = await axios.get<PortfolioSummary[]>('/api/portfolios')
    portfolios.value = data
  } catch (e: any) {
    ElMessage.error('获取组合列表失败：' + (e.response?.data?.message || e.message))
  }
}

async function syncPrices() {
  if (syncing.value) return
  syncing.value = true
  try {
    await axios.post(`/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/sync-prices`)
    ElMessage.success('价格同步成功')
    await fetchPortfolio()
    await fetchNavHistory()
  } catch (e: any) {
    ElMessage.error('同步失败：' + (e.response?.data?.message || e.message))
  } finally {
    syncing.value = false
  }
}

async function fetchPortfolio() {
  try {
    const { data } = await axios.get<PortfolioData>(
      `/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}`
    )
    Object.assign(portfolio, data)
    if (!withdrawAssetId.value && data.assets.length > 0) {
      const sellable = data.assets.find((a: Asset) => a.shares > 0)
      if (sellable) withdrawAssetId.value = sellable.allocationId
    }
    nextTick(() => initPieChart())
  } catch (e: any) {
    ElMessage.error('获取投资组合数据失败：' + (e.response?.data?.message || e.message))
  }
}

async function fetchNavHistory() {
  try {
    const { data } = await axios.get(
      `/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/snapshot`,
      { params: { days: 60 } }
    )
    if (data && data.dates && data.values && data.dates.length > 0) {
      navDates.value = data.dates
      navValues.value = data.values
    } else if (Array.isArray(data) && data.length > 0) {
      // snapshot API returns raw records: [{snapshotTime, totalNav}, ...]
      navDates.value = data.map((r: any) => {
        const d = new Date(r.snapshotTime)
        return (d.getMonth() + 1) + '/' + d.getDate()
      })
      navValues.value = data.map((r: any) => r.totalNav)
    } else {
      // 空数据，生成 mock
      generateMockNavData()
    }
    initChart()
  } catch (e: any) {
    // Fallback to mock data
    generateMockNavData()
    nextTick(() => initChart())
  }
}

function generateMockNavData() {
  const today = new Date()
  const dates: string[] = []
  const values: number[] = []
  const base = portfolio.totalNav || 300000
  const end = base * 1.095

  for (let i = 0; i < 60; i++) {
    const d = new Date(today)
    d.setDate(d.getDate() - (59 - i))
    dates.push((d.getMonth() + 1) + '/' + d.getDate())
    const t = i / 59
    const noise = (Math.random() - 0.5) * (base * 0.008)
    values.push(Math.round(base + (end - base) * t + noise))
  }

  navDates.value = dates
  navValues.value = values
}

function initChart() {
  if (!chartRef.value) return
  if (chartInstance) {
    chartInstance.dispose()
  }

  const echarts = (window as any).echarts
  if (!echarts) {
    console.warn('ECharts 未加载')
    return
  }

  chartInstance = echarts.init(chartRef.value)
  const isDark = getTheme()

  const accent = isDark ? '#7B93E0' : '#5470C6'
  const accentGlow = isDark ? 'rgba(123,147,224,0.25)' : 'rgba(84,112,198,0.18)'
  const accentFade = isDark ? 'rgba(123,147,224,0.02)' : 'rgba(84,112,198,0.01)'
  const tickColor = isDark ? '#5A5F66' : '#b0b7c0'
  const gridColor = isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'
  const tooltipBg = isDark ? '#1F2227' : '#fff'
  const tooltipBorder = isDark ? '#363940' : '#e8eaed'

  const navMin = Math.min(...navValues.value)
  const navMax = Math.max(...navValues.value)
  const navRange = navMax - navMin || 1
  const yMin = Math.floor((navMin - navRange * 0.1) / 10000) * 10000
  const yMax = Math.ceil((navMax + navRange * 0.1) / 10000) * 10000

  chartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      borderWidth: 1,
      textStyle: { fontSize: 12, color: isDark ? '#e8eaed' : '#191c1f' },
      axisPointer: {
        type: 'cross',
        crossStyle: { color: isDark ? '#5A5F66' : '#b0b7c0' },
        label: {
          backgroundColor: accent,
          color: '#fff',
          fontSize: 11,
          formatter: (p: any) => {
            if (p.axisDimension === 'y') return '¥' + (p.value as number / 10000).toFixed(1) + '万'
            return p.value
          },
        },
      },
      valueFormatter: (value: number) => '¥' + value.toLocaleString(),
    },
    grid: { left: 16, right: 24, top: 24, bottom: 36 },
    xAxis: {
      type: 'category',
      data: navDates.value,
      axisLabel: {
        interval: Math.max(1, Math.floor(navDates.value.length / 7) - 1),
        fontSize: 11,
        color: tickColor,
        margin: 10,
      },
      axisLine: { lineStyle: { color: gridColor } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      min: yMin,
      max: yMax,
      splitNumber: 4,
      axisLabel: {
        formatter: (v: number) => '¥' + (v / 10000).toFixed(0) + '万',
        fontSize: 11,
        color: tickColor,
        margin: 10,
      },
      splitLine: { lineStyle: { color: gridColor, type: 'dashed' } },
      axisLine: { show: false },
    },
    series: [
      {
        type: 'line',
        data: navValues.value,
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        showSymbol: false,
        emphasis: { focus: 'series' },
        lineStyle: { color: accent, width: 2.5, cap: 'round' },
        itemStyle: {
          color: accent,
          borderColor: isDark ? '#0d0f10' : '#fff',
          borderWidth: 2,
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: accentGlow },
            { offset: 1, color: accentFade },
          ]),
        },
      },
    ],
    animationDuration: 800,
    animationEasing: 'cubicOut',
  })
}

// ── Pie Chart ──
const assetPieColors = ['#5470C6', '#91CC75', '#FAC858', '#EE6666', '#73C0DE', '#9A60B4', '#FC8452', '#3BA272']

function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r},${g},${b},${alpha})`
}

function initPieChart() {
  if (!pieChartRef.value) return
  if (pieChartInstance) {
    pieChartInstance.dispose()
  }

  const echarts = (window as any).echarts
  if (!echarts) {
    console.warn('ECharts 未加载')
    return
  }

  pieChartInstance = echarts.init(pieChartRef.value)

  const assets = portfolio.assets
  if (assets.length === 0) {
    pieChartInstance.setOption({
      title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { fontSize: 14, color: '#999' } }
    })
    return
  }

  const isDark = getTheme()
  const hasPrice = assets.some(a => a.price != null)

  // 内环：目标配比 — 过滤掉比例为 0 的资产
  const targetData = assets
    .map((a, idx) => {
      const val = Math.round(a.targetRatio * 10000) / 100
      const solidColor = assetPieColors[idx % assetPieColors.length]
      return {
        name: a.name,
        value: val > 0 ? val : null as any,
        itemStyle: {
          color: hexToRgba(solidColor, 0.25),
          borderColor: '#fff',
          borderWidth: 2,
          borderRadius: 10,
        },
      }
    })
    .filter(d => d.value != null)

  // 外环：实际配比（实色）
  const actualData = assets
    .map((a, idx) => {
      const val = hasPrice
        ? Math.round(a.actualRatio * 10000) / 100
        : Math.round(a.targetRatio * 10000) / 100
      return {
        name: a.name,
        value: val > 0 ? val : null as any,
        itemStyle: {
          color: hasPrice ? assetPieColors[idx % assetPieColors.length] : '#b0b0b0',
          borderColor: '#fff',
          borderWidth: 2,
          borderRadius: 10,
        },
      }
    })
    .filter(d => d.value != null)

  pieChartInstance.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: '#fff',
      borderColor: '#e8e8e8',
      borderWidth: 1,
      padding: [10, 14],
      textStyle: { fontSize: 12, color: '#333' },
      formatter: (params: any) => {
        const dot = `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${params.color};margin-right:6px;"></span>`
        return `${dot}${params.seriesName}<br/>&nbsp;&nbsp;${params.name}: <strong>${params.value}%</strong>`
      },
    },
    legend: {
      type: 'scroll',
      bottom: 8,
      data: Array.from(new Set([...targetData, ...actualData].map(d => d.name))),
      textStyle: { fontSize: 12, color: '#333', fontWeight: 500 },
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 20,
      icon: 'circle',
      selectedMode: true,
      inactiveColor: '#ccc',
    },
    series: [
      {
        name: '目标配比',
        type: 'pie',
        radius: ['42%', '58%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: false,
        minAngle: 3,
        padAngle: 1,
        label: { show: false },
        emphasis: {
          scale: false,
          label: { show: false },
        },
        labelLine: { show: false },
        data: targetData,
        animationType: 'scale',
        animationEasing: 'elasticOut',
        animationDuration: 600,
        animationDelay: (idx: number) => idx * 60,
      },
      {
        name: '实际配比',
        type: 'pie',
        radius: ['64%', '82%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        minAngle: 3,
        padAngle: 1,
        label: {
          show: true,
          position: 'outside',
          formatter: (p: any) => `{name|${p.name}}\n{val|${p.value}%}`,
          rich: {
            name: {
              fontSize: 12,
              fontWeight: 'bold',
              color: '#333',
              lineHeight: 18,
            },
            val: {
              fontSize: 11,
              color: '#999',
              lineHeight: 16,
            },
          },
        },
        labelLine: {
          length: 24,
          length2: 32,
          lineStyle: { color: '#d0d0d0', width: 1 },
          smooth: true,
        },
        emphasis: {
          scale: true,
          scaleSize: 8,
          focus: 'self',
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold',
          },
        },
        data: actualData,
        animationType: 'scale',
        animationEasing: 'elasticOut',
        animationDuration: 700,
        animationDelay: (idx: number) => idx * 80 + 200,
      },
    ],
    graphic: hasPrice
      ? [
          {
            type: 'text',
            left: 'center',
            top: '38%',
            style: {
              text: currentPortfolioName.value,
              textAlign: 'center',
              fill: '#333',
              fontSize: 16,
              fontWeight: 'bold',
            },
          },
          {
            type: 'text',
            left: 'center',
            top: '48%',
            style: {
              text: '内环目标 · 外环实际',
              textAlign: 'center',
              fill: '#999',
              fontSize: 11,
            },
          },
        ]
      : [
          {
            type: 'text',
            left: 'center',
            top: '42%',
            style: {
              text: currentPortfolioName.value,
              textAlign: 'center',
              fill: '#333',
              fontSize: 16,
              fontWeight: 'bold',
            },
          },
        ],
  })
}

async function confirmDeposit() {
  if (!depositAmount.value || depositAmount.value <= 0) {
    ElMessage.warning('请输入有效的入金金额')
    return
  }
  depositLoading.value = true
  try {
    await axios.post(`/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/deposit`, {
      amount: depositAmount.value,
    })
    ElMessage.success('✅ 成功加钱 ¥' + depositAmount.value.toLocaleString() + '，已加入现金账户')
    closeModal()
    await fetchPortfolio()
    await fetchNavHistory()
  } catch (e: any) {
    ElMessage.error('加钱失败：' + (e.response?.data?.message || e.message))
  } finally {
    depositLoading.value = false
  }
}

async function confirmWithdraw() {
  if (!withdrawAssetId.value || !withdrawShares.value || withdrawShares.value <= 0) {
    ElMessage.warning('请选择资产并输入有效的数量')
    return
  }
  withdrawLoading.value = true
  try {
    await axios.post(`/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/withdraw`, {
      allocationId: withdrawAssetId.value,
      shares: withdrawShares.value,
    })
    ElMessage.success('✅ 已卖出 ' + withdrawShares.value + '，资金已回笼')
    closeModal()
    await fetchPortfolio()
    await fetchNavHistory()
  } catch (e: any) {
    ElMessage.error('减钱失败：' + (e.response?.data?.message || e.message))
  } finally {
    withdrawLoading.value = false
  }
}

async function handleRebalance() {
  openModal('rebalance')
}

async function loadRebalancePreview() {
  if (hasLoadedRebalance.value) return
  rebalanceNote.value = ''
  try {
    const { data } = await axios.post(
      `/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/rebalance`
    )
    // 转换后端返回的 action 字段: BUY→买入, SELL→卖出
    rebalanceOps.value = (data.operations || []).map((op: any) => ({
      type: op.action === 'BUY' ? '买入' : '卖出',
      name: op.name,
      shares: op.shares,
      amount: op.amount,
      allocationId: op.allocationId,
      action: op.action,
      actualShares: undefined,
    }))
    rebalanceRemainder.value = data.remainder || 0
  } catch {
    computeLocalRebalance()
  }
  hasLoadedRebalance.value = true
}

function computeLocalRebalance() {
  const ops: RebalanceOp[] = []
  const totalNav = portfolio.totalNav || 1

  for (const asset of portfolio.assets) {
    if (asset.code === 'CASH') continue
    const targetValue = totalNav * asset.targetRatio
    const diff = targetValue - asset.marketValue
    const price = asset.price || 5.0

    if (Math.abs(diff) < price * 50) continue

    if (diff > 0) {
      const shares = Math.floor(diff / price / 100) * 100
      if (shares > 0) {
        ops.push({ type: '买入', name: asset.name, shares, amount: Math.round(shares * price), allocationId: asset.allocationId, action: 'BUY', actualShares: undefined })
      }
    } else {
      const shares = Math.floor(Math.abs(diff) / price / 100) * 100
      if (shares > 0) {
        ops.push({ type: '卖出', name: asset.name, shares, amount: Math.round(shares * price), allocationId: asset.allocationId, action: 'SELL', actualShares: undefined })
      }
    }
  }

  rebalanceOps.value = ops
  rebalanceRemainder.value = 0
}

async function confirmRebalance() {
  rebalanceLoading.value = true
  try {
    // 构建操作列表，使用用户填写的实际成交股数（若留空则用建议数量）
    const operations = rebalanceOps.value
      .filter(op => (op as any).allocationId)
      .map(op => ({
        allocationId: (op as any).allocationId,
        type: (op as any).action,
        shares: op.actualShares != null && op.actualShares > 0 ? op.actualShares : op.shares,
        note: rebalanceNote.value || null,
      }))
    await axios.post(
      `/api/portfolio/${encodeURIComponent(currentPortfolioName.value)}/rebalance/execute`,
      { operations }
    )
    ElMessage.success('✅ 再平衡执行结果已记录')
    closeModal()
    hasLoadedRebalance.value = false
    await fetchPortfolio()
    await fetchNavHistory()
  } catch (e: any) {
    ElMessage.error('记录失败：' + (e.response?.data?.message || e.message))
  } finally {
    rebalanceLoading.value = false
  }
}

// ── Lifecycle ──
onMounted(async () => {
  await fetchPortfolios()
  if (portfolios.value.length > 0) {
    currentPortfolioName.value = portfolios.value[0].portfolioName
  }
  await fetchPortfolio()
  await fetchNavHistory()
  window.addEventListener('resize', handleResize)
  document.addEventListener('click', closeSelector)

  // 监听主题切换，重新渲染图表
  themeObserver = new MutationObserver(() => {
    if (navValues.value.length > 0) {
      nextTick(() => initChart())
    }
    nextTick(() => initPieChart())
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('click', closeSelector)
  themeObserver?.disconnect()
  if (chartInstance) {
    chartInstance.dispose()
  }
  if (pieChartInstance) {
    pieChartInstance.dispose()
  }
})

function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
  if (pieChartInstance) {
    pieChartInstance.resize()
  }
}

// 监听 nav 数据变化重新渲染图表
watch([navDates, navValues], () => {
  nextTick(() => initChart())
})
</script>

<style scoped>
/* ── CSS Variables (scoped) ── */
.portfolio-page {
  --bg: #f8f9fa;
  --card: #ffffff;
  --text-primary: #191c1f;
  --text-secondary: #8d969e;
  --accent: #5470C6;
  --success: #00a87e;
  --warning: #ec7e00;
  --danger: #e5484d;
  --border: #e8eaed;

  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: var(--bg);
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.5;
  min-height: 100vh;
  -webkit-font-smoothing: antialiased;
}

/* ── Dark Mode ── */
html.dark .portfolio-page {
  --bg: #0d0f10;
  --card: #1a1d20;
  --text-primary: #e8eaed;
  --text-secondary: #8d969e;
  --accent: #7B93E0;
  --border: #2a2d30;
}
html.dark .sidebar-card .sc-value {
  color: var(--text-primary);
}
html.dark .sidebar-card .sc-value.warn {
  color: var(--warning);
}
html.dark .form-input {
  background: var(--card);
  color: var(--text-primary);
}
html.dark select.form-input {
  background: var(--card) url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath d='M6 8L1 3h10z' fill='%238d969e'/%3E%3C/svg%3E") no-repeat right 12px center;
}
html.dark .action-btn {
  background: var(--card);
  color: var(--text-primary);
}
html.dark .btn {
  background: var(--card);
  color: var(--text-primary);
}
html.dark .modal {
  background: var(--card);
}
html.dark table thead {
  background: var(--bg);
}
html.dark .footer-bar {
  background: var(--card);
}
html.dark .toast-success { background: var(--success); }
html.dark .toast-info { background: var(--accent); }
html.dark .toast-warning { background: var(--warning); }
html.dark .selector-dropdown {
  background: var(--card);
  border-color: var(--border);
}
html.dark .selector-item:hover {
  background: var(--bg);
}
html.dark .selector-actions {
  border-top-color: var(--border);
}
html.dark .ratio-input {
  background: var(--card);
  color: var(--text-primary);
  border-color: var(--accent);
}
html.dark .ratio-slider {
  background: var(--border);
}
html.dark .ratio-slider::-webkit-slider-thumb {
  border-color: var(--card);
}
html.dark .allocation-preview {
  background: #1a1d30;
}

/* ── Top Bar ── */
.top-bar {
  background: var(--card);
  border-bottom: 1px solid var(--border);
  padding: 0 32px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.top-bar-left {
  display: flex;
  align-items: center;
}
.top-bar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ── Top Bar Actions ── */
.top-bar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 20px;
  padding-left: 20px;
  border-left: 1px solid var(--border);
}
.top-bar-actions .action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  background: var(--card);
  color: var(--text-primary);
  transition: border-color 0.15s, color 0.15s;
}
.top-bar-actions .action-sm {
  font-size: 16px;
  padding: 4px 8px;
}
.top-bar-actions .action-lg {
  font-size: 13px;
  padding: 6px 12px;
}
.top-bar-actions .action-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}

/* ── Portfolio Selector ── */
.portfolio-selector {
  position: relative;
}
.portfolio-selector-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: none;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.15s;
}
.portfolio-selector-btn:hover {
  border-color: var(--accent);
}
.rename-portfolio-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  background: var(--card);
  color: var(--text-secondary);
  font-family: inherit;
  transition: all 0.15s;
  margin-left: 8px;
  padding: 0;
}
.rename-portfolio-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.selector-label {
  font-size: 16px;
}

.selector-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 6px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  min-width: 280px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  z-index: 200;
  overflow: hidden;
}
.selector-item {
  padding: 12px 16px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
  transition: background 0.15s;
}
.selector-item:hover {
  background: var(--bg);
}
.selector-item.active {
  background: rgba(73, 79, 223, 0.06);
}
.selector-item-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.selector-item-meta {
  font-size: 12px;
  color: var(--text-secondary);
}
.selector-actions {
  display: flex;
  border-top: 1px solid var(--border);
  padding: 8px;
  gap: 8px;
}
.selector-action-btn {
  flex: 1;
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  background: var(--card);
  color: var(--text-primary);
  font-family: inherit;
  transition: border-color 0.15s, color 0.15s;
}
.selector-action-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.selector-action-btn.danger:hover {
  border-color: var(--danger);
  color: var(--danger);
}
.selector-action-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}


.container {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 32px;
}

/* ── Chart Section ── */
.chart-section {
  display: flex;
  gap: 20px;
  align-items: stretch;
  margin-bottom: 24px;
}
.chart-left {
  flex: 1;
  min-width: 0;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 20px 20px 12px 20px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.03);
  transition: box-shadow 0.2s;
}
.chart-left:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.06), 0 2px 4px rgba(0,0,0,0.04);
}
.chart-left .chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}
.chart-container {
  width: 100%;
  height: 340px;
  flex: 1;
}

/* ── Sidebar ── */
.chart-right {
  width: 260px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex-shrink: 0;
}

.sidebar-networth {
  background: linear-gradient(135deg, var(--accent), rgba(84,112,198,0.8));
  border-radius: 14px;
  padding: 20px 18px;
  color: #fff;
  box-shadow: 0 2px 8px rgba(84,112,198,0.25);
}
.sidebar-networth .sn-label {
  font-size: 11px;
  opacity: 0.75;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin-bottom: 2px;
}
.sidebar-networth .sn-value {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.5px;
  line-height: 1.2;
}
.sidebar-networth .sn-meta {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 12px;
  opacity: 0.82;
}
.sidebar-networth .sn-meta span {
  display: flex;
  gap: 4px;
}

.sidebar-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.sidebar-card .sc-label {
  font-size: 11px;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 3px;
}
.sidebar-card .sc-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}
.sidebar-card .sc-value.warn {
  color: var(--warning);
}
.sidebar-card .sc-sub {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 3px;
  line-height: 1.4;
}


/* ── Section Title ── */
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.add-asset-btn {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  border: 1px solid var(--accent);
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  background: var(--card);
  color: var(--accent);
  transition: all 0.15s;
}
.add-asset-btn:hover {
  background: var(--accent);
  color: #fff;
}

.section-title-actions {
  display: flex;
  gap: 8px;
}

/* ── Pie Chart ── */
.pie-chart-wrapper {
  background: #fff;
  border: none;
  border-radius: 14px;
  padding: 20px 16px 8px 16px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06), 0 0 0 1px rgba(0,0,0,0.04);
  transition: box-shadow 0.2s;
}
.pie-chart-wrapper:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.08), 0 0 0 1px rgba(0,0,0,0.05);
}
.pie-chart-container {
  width: 100%;
  height: 400px;
}

/* ── Edit Icon Button ── */
.edit-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 11px;
  cursor: pointer;
  background: var(--bg);
  color: var(--text-secondary);
  font-family: inherit;
  transition: all 0.15s;
  margin-left: 4px;
  vertical-align: middle;
}
.edit-icon-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--card);
}

/* ── Ratio Warning ── */
.ratio-warning {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  padding: 10px 16px;
  font-size: 12px;
  color: var(--warning);
  margin-bottom: 16px;
  margin-top: -12px;
}

/* ── Asset Cards Grid ── */
.asset-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 28px;
}
.asset-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.asset-card:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  border-color: rgba(84,112,198,0.2);
}
.asset-card .card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.asset-card .card-header .name {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
}
.asset-card .card-header .code {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg);
  padding: 2px 8px;
  border-radius: 4px;
}
.asset-card .card-price {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 10px;
}
.asset-card .card-metrics {
  display: flex;
  gap: 28px;
  margin-bottom: 10px;
}
.asset-card .card-metrics .metric .metric-label {
  font-size: 11px;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}
.asset-card .card-metrics .metric .metric-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 1px;
}
.asset-card .card-ratio {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
  padding: 2px 0;
}
.asset-card .card-ratio:hover {
  background: rgba(99, 102, 241, 0.05);
}

/* ── Inline Ratio Edit ── */
.ratio-edit-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.ratio-edit-label {
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
}
.ratio-slider {
  flex: 1;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: var(--border);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
  accent-color: var(--accent);
}
.ratio-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--accent);
  cursor: pointer;
  border: 2px solid var(--card);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}
.ratio-slider::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--accent);
  cursor: pointer;
  border: 2px solid var(--card);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}
.ratio-input {
  width: 56px;
  padding: 2px 6px;
  border: 1px solid var(--accent);
  border-radius: 4px;
  font-size: 12px;
  font-family: inherit;
  font-weight: 600;
  text-align: right;
  outline: none;
  background: var(--card);
  color: var(--text-primary);
  box-sizing: border-box;
}
.ratio-save-ok {
  color: var(--success);
  font-size: 14px;
  font-weight: 700;
}
.ratio-save-err {
  color: var(--danger);
  font-size: 14px;
  font-weight: 700;
  cursor: help;
}

.deviation-tag {
  display: inline-block;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: 6px;
  align-self: flex-start;
}
.deviation-tag.over {
  background: #fff3e8;
  color: var(--warning);
}
.deviation-tag.under {
  color: var(--text-secondary);
  background: var(--bg);
}

/* ── Holdings Table ── */
.compact-table-wrap {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 28px;
}
.compact-table-wrap .table-header {
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.compact-table-wrap .table-header h3 {
  font-size: 14px;
  font-weight: 600;
}
.compact-table-wrap .table-subtitle {
  font-size: 12px;
  color: var(--text-secondary);
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
thead {
  background: var(--bg);
}
th {
  text-align: left;
  padding: 10px 18px;
  font-weight: 500;
  color: var(--text-secondary);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--border);
}
td {
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
}
tr:last-child td {
  border-bottom: none;
}
tr.warn-row {
  background: #fffdf5;
}
html.dark tr.warn-row {
  background: #1a1a12;
}

.asset-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}
.asset-dot.blue   { background: #5470C6; }
.asset-dot.green  { background: #91CC75; }
.asset-dot.yellow { background: #FAC858; }
.asset-dot.red    { background: #EE6666; }
.asset-dot.cyan   { background: #73C0DE; }
.asset-dot.purple { background: #9A60B4; }
.asset-dot.orange { background: #FC8452; }
.asset-dot.teal   { background: #3BA272; }

.asset-code-sub {
  color: var(--text-secondary);
  font-size: 12px;
  margin-left: 4px;
}

.price-ph {
  color: var(--text-secondary);
  font-size: 12px;
}

.table-action-btn {
  padding: 2px 10px;
  border: 1px solid var(--border);
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  background: var(--card);
  color: var(--text-secondary);
  font-family: inherit;
  transition: all 0.15s;
}
.table-action-btn:hover:not(:disabled) {
  border-color: var(--danger);
  color: var(--danger);
}
.table-action-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.status-tag {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}
.status-tag.warn {
  background: #fff3e8;
  color: var(--warning);
}
.status-tag.alert {
  background: #fef0f0;
  color: var(--danger);
}
.status-tag.ok {
  background: #eafaf4;
  color: var(--success);
}

.deviation-warn {
  font-weight: 600;
  color: var(--warning);
}
.deviation-danger {
  font-weight: 600;
  color: var(--danger);
}

/* ── Footer Bar ── */
.footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 14px 20px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 32px;
}
.footer-bar strong {
  color: var(--text-primary);
  font-weight: 500;
}

.footer-sync {
  font-size: 11px;
  color: var(--text-secondary);
}
.footer-sync .sync-link {
  color: var(--accent);
  text-decoration: none;
  font-weight: 500;
}
.footer-sync .sync-link:hover {
  text-decoration: underline;
}
.footer-sync .sync-link.disabled {
  color: var(--text-secondary);
  pointer-events: none;
  text-decoration: none;
}

/* ── Modal ── */
.modal-overlay {
  display: none;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 1000;
  align-items: center;
  justify-content: center;
}
.modal-overlay.active {
  display: flex;
}
.modal {
  background: var(--card);
  border-radius: 14px;
  padding: 0;
  width: 480px;
  max-height: 85vh;
  overflow: auto;
  animation: modalIn 0.2s ease;
  border: 1px solid var(--border);
}
@keyframes modalIn {
  from {
    opacity: 0;
    transform: scale(0.96);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
.modal-header {
  padding: 18px 24px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.modal-header h3 {
  font-size: 16px;
  font-weight: 600;
}
.modal-close {
  background: var(--bg);
  border: none;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: 16px;
  cursor: pointer;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal-close:hover {
  background: var(--border);
  color: var(--text-primary);
}
.modal-body {
  padding: 20px 24px;
}
.modal-footer {
  padding: 14px 24px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.form-item {
  margin-bottom: 16px;
}
.form-item label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 5px;
}
.form-input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s;
  background: var(--card);
  color: var(--text-primary);
  box-sizing: border-box;
}
.form-input:focus {
  border-color: var(--accent);
}
.form-input:disabled {
  opacity: 0.5;
  background: var(--bg);
  cursor: not-allowed;
}
select.form-input {
  appearance: none;
  background: var(--card)
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath d='M6 8L1 3h10z' fill='%238d969e'/%3E%3C/svg%3E")
    no-repeat right 12px center;
  padding-right: 32px;
}
.form-hint {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 5px;
}

.allocation-preview {
  background: #f0f2ff;
  border-radius: 10px;
  padding: 14px;
  font-size: 12px;
  margin-top: 10px;
}
.alloc-title {
  color: var(--accent);
}
.allocation-preview table {
  width: 100%;
  margin-top: 6px;
  font-size: 12px;
}
.alloc-right {
  text-align: right;
  font-weight: 600;
}
.alloc-note {
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-secondary);
}

.estimate-box {
  background: #fff8f0;
  border-radius: 10px;
  padding: 12px;
  font-size: 13px;
}
.estimate-note {
  font-size: 11px;
  color: var(--text-secondary);
}

.rebalance-intro {
  font-size: 13px;
  margin-bottom: 14px;
  color: var(--text-secondary);
}
.rebalance-table {
  width: 100%;
  font-size: 13px;
}
.text-left {
  text-align: left;
}
.text-right {
  text-align: right;
}
.text-center {
  text-align: center;
}
.op-sell {
  color: var(--success);
  font-weight: 600;
}
.op-buy {
  color: var(--danger);
  font-weight: 600;
}
.rebalance-note {
  margin-top: 10px;
  padding: 10px;
  background: var(--bg);
  border-radius: 8px;
  font-size: 11px;
  color: var(--text-secondary);
}

.actual-shares-input {
  padding: 4px 6px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  background: var(--card);
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.15s;
}
.actual-shares-input:focus {
  border-color: var(--accent);
}

/* ── Code Search Dropdown ── */
.code-search-wrapper {
  position: relative;
}
.code-search-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 2px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  z-index: 300;
  max-height: 220px;
  overflow-y: auto;
}
html.dark .code-search-dropdown {
  background: var(--card);
  border-color: var(--border);
}
.code-suggestion-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  gap: 8px;
  transition: background 0.12s;
}
.code-suggestion-item:hover {
  background: var(--bg);
}
.code-suggestion-code {
  font-weight: 600;
  color: var(--accent);
  min-width: 60px;
}
.code-suggestion-name {
  flex: 1;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.code-suggestion-type {
  font-size: 11px;
  color: var(--text-secondary);
  background: var(--bg);
  padding: 2px 6px;
  border-radius: 4px;
}

/* ── Buttons ── */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 18px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  background: var(--card);
  color: var(--text-primary);
  transition: all 0.15s;
}
.btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-primary {
  background: var(--accent);
  color: #fff;
  border-color: var(--accent);
}
.btn-primary:hover {
  opacity: 0.9;
  color: #fff;
}
.btn-success {
  background: var(--success);
  color: #fff;
  border-color: var(--success);
}
.btn-success:hover {
  opacity: 0.9;
  color: #fff;
}
.btn-danger {
  background: var(--danger);
  color: #fff;
  border-color: var(--danger);
}
.btn-danger:hover {
  opacity: 0.9;
  color: #fff;
}

/* ── 资产分配器 ── */
.allocator-body {
  padding: 0 4px;
}

/* 金条 */
.allocator-bar-wrapper {
  padding: 0 20px 16px 20px;
  margin-bottom: 8px;
}
.allocator-bar {
  display: flex;
  height: 32px;
  border-radius: 8px;
  overflow: visible;
  background: #f0f1f3;
  gap: 1px;
}
.allocator-segment {
  height: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: flex 0.12s ease;
}
.allocator-segment-label {
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0,0,0,0.25);
  user-select: none;
  white-space: nowrap;
}
.allocator-segment:first-child {
  border-radius: 8px 0 0 8px;
}
.allocator-segment:last-child {
  border-radius: 0 8px 8px 0;
}

/* 分割线 — 0 宽度 flex 子元素，突出杆样式 */
.allocator-divider {
  width: 0;
  position: relative;
  z-index: 10;
  flex-shrink: 0;
}
.allocator-divider::before {
  /* 透明拖拽热区 */
  content: '';
  position: absolute;
  left: -10px;
  top: -12px;
  width: 20px;
  height: 56px;
  cursor: col-resize;
  z-index: 1;
}
.allocator-divider::after {
  /* 突出杆 */
  content: '';
  position: absolute;
  left: -3px;
  top: -8px;
  width: 6px;
  height: 48px;
  background: #fff;
  border: 1.5px solid #d0d5dd;
  border-radius: 4px;
  pointer-events: none;
  z-index: 2;
  transition: background 0.15s, border-color 0.15s;
}
.allocator-divider:hover::after {
  background: #9ca3af;
  border-color: #9ca3af;
}
.allocator-divider:active::after {
  background: #6b7280;
  border-color: #6b7280;
}

/* 底部列表 */
.allocator-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.allocator-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f8f9fa;
  border-radius: 8px;
  transition: background 0.15s;
}
.allocator-row:hover {
  background: #f0f2f5;
}
.allocator-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.allocator-name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: #333;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.allocator-input-wrap {
  display: flex;
  align-items: center;
  gap: 2px;
}
.allocator-input {
  width: 64px;
  padding: 4px 6px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  text-align: right;
  font-family: inherit;
  outline: none;
  color: #333;
  transition: border-color 0.15s;
}
.allocator-input:focus {
  border-color: #5470C6;
  box-shadow: 0 0 0 2px rgba(84,112,198,0.12);
}
.allocator-unit {
  font-size: 12px;
  color: #999;
}
.allocator-value {
  font-size: 12px;
  color: #999;
  min-width: 80px;
  text-align: right;
}

/* 配比偏离警告 */
.allocator-warning {
  padding: 10px 14px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  font-size: 13px;
  color: #c2410c;
}

.allocator-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .chart-section {
    flex-direction: column;
  }
  .chart-right {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
  }
  .chart-right > * {
    flex: 1;
    min-width: 180px;
  }
  .asset-grid {
    grid-template-columns: 1fr;
  }
  .footer-bar {
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }
}
</style>
