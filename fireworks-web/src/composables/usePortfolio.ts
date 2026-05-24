import { useLazyQuery, useMutation } from '@vue/apollo-composable'
import {
  Portfolios as PORTFOLIOS,
  Portfolio as PORTFOLIO,
  NavHistory as NAV_HISTORY,
  TushareSearch as TUSHARE_SEARCH,
  CreatePortfolio as CREATE_PORTFOLIO,
  DeletePortfolio as DELETE_PORTFOLIO,
  RenamePortfolio as RENAME_PORTFOLIO,
  Allocation as ALLOCATION,
  AddAllocation as ADD_ALLOCATION,
  UpdateAllocation as UPDATE_ALLOCATION,
  DeleteAllocation as DELETE_ALLOCATION,
  Deposit as DEPOSIT,
  Withdraw as WITHDRAW,
  RebalancePreview as REBALANCE_PREVIEW,
  RebalanceExecute as REBALANCE_EXECUTE,
  SyncPrices as SYNC_PRICES,
} from '@/graphql/portfolio.gql'

// ── 类型定义 ──

export interface PortfolioSummary {
  id: string
  name: string
  totalNav: number
  assetCount: number
}

export interface PortfolioHolding {
  allocationId: string
  code: string
  name: string
  assetType: string
  targetRatio: number
  actualRatio: number
  shares: number
  avgCost: number
  currentPrice: number | null
  marketValue: number
  profit: number | null
  profitRate: number | null
}

export interface PortfolioOverview {
  id: string
  name: string
  totalNav: number
  totalCost: number
  totalProfit: number
  totalProfitRate: number
  cash: number
  holdings: PortfolioHolding[]
}

export interface NavPoint {
  date: string
  nav: number
}

export interface NavHistoryResult {
  portfolioName: string
  data: NavPoint[]
}

export interface TushareSearchResult {
  code: string
  name: string
  assetType: string
}

export interface AllocationItem {
  id: string
  code: string
  name: string
  assetType: string
  targetRatio: number
}

export interface RebalanceOp {
  allocationId: string
  code: string
  name: string
  assetType: string
  targetRatio: number
  currentRatio: number
  diff: number
  action: string
  shares: number
}

export interface RebalanceResult {
  portfolioName: string
  targetNav: number
  operations: RebalanceOp[]
  executed: boolean
}

export interface PriceSyncItem {
  code: string
  name: string
  price: number | null
  error: string | null
}

export interface SyncPricesResult {
  portfolioName: string
  successCount: number
  totalCount: number
  results: PriceSyncItem[]
}

export interface RebalanceOpInput {
  allocationId: string
  shares: number
}

// ── Composable ──

export function usePortfolio() {
  // —— Queries ——
  const { load: loadPortfolios } = useLazyQuery<{ portfolios: PortfolioSummary[] }>(PORTFOLIOS, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadPortfolio } = useLazyQuery<{ portfolio: PortfolioOverview }>(PORTFOLIO, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadNavHistory } = useLazyQuery<{ navHistory: NavHistoryResult }>(NAV_HISTORY, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadTushareSearch } = useLazyQuery<{ tushareSearch: TushareSearchResult[] }>(TUSHARE_SEARCH, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadAllocation } = useLazyQuery<{ allocation: AllocationItem[] }>(ALLOCATION, undefined, {
    fetchPolicy: 'network-only',
  })
  const { load: loadRebalancePreview } = useLazyQuery<{ rebalancePreview: RebalanceResult }>(REBALANCE_PREVIEW, undefined, {
    fetchPolicy: 'network-only',
  })

  // —— Mutations ——
  const { mutate: createPortfolioMutate } = useMutation<{ createPortfolio: PortfolioSummary }>(CREATE_PORTFOLIO)
  const { mutate: deletePortfolioMutate } = useMutation<{ deletePortfolio: boolean }>(DELETE_PORTFOLIO)
  const { mutate: renamePortfolioMutate } = useMutation<{ renamePortfolio: boolean }>(RENAME_PORTFOLIO)
  const { mutate: addAllocationMutate } = useMutation<{ addAllocation: AllocationItem }>(ADD_ALLOCATION)
  const { mutate: updateAllocationMutate } = useMutation<{ updateAllocation: boolean }>(UPDATE_ALLOCATION)
  const { mutate: deleteAllocationMutate } = useMutation<{ deleteAllocation: boolean }>(DELETE_ALLOCATION)
  const { mutate: depositMutate } = useMutation<{ deposit: PortfolioOverview }>(DEPOSIT)
  const { mutate: withdrawMutate } = useMutation<{ withdraw: PortfolioOverview }>(WITHDRAW)
  const { mutate: rebalanceExecuteMutate } = useMutation<{ rebalanceExecute: RebalanceResult }>(REBALANCE_EXECUTE)
  const { mutate: syncPricesMutate } = useMutation<{ syncPrices: SyncPricesResult }>(SYNC_PRICES)

  // ===== 封装方法 =====

  /** 获取所有组合列表 */
  async function fetchPortfolios(): Promise<PortfolioSummary[]> {
    const result = await loadPortfolios()
    return result!.portfolios
  }

  /** 获取单个组合详情 */
  async function fetchPortfolio(name: string): Promise<PortfolioOverview> {
    const result = await loadPortfolio(undefined, { name })
    return result!.portfolio
  }

  /** 获取 NAV 历史 */
  async function fetchNavHistory(name: string, days: number = 60): Promise<NavHistoryResult> {
    const result = await loadNavHistory(undefined, { name, days })
    return result!.navHistory
  }

  /** Tushare 搜索 */
  async function searchTushare(keyword: string): Promise<TushareSearchResult[]> {
    const result = await loadTushareSearch(undefined, { keyword })
    return result!.tushareSearch
  }

  /** 创建组合 */
  async function createPortfolio(name: string): Promise<PortfolioSummary> {
    const result = await createPortfolioMutate({ name })
    return result!.data!.createPortfolio
  }

  /** 删除组合 */
  async function deletePortfolio(name: string): Promise<boolean> {
    const result = await deletePortfolioMutate({ name })
    return result!.data!.deletePortfolio
  }

  /** 重命名组合 */
  async function renamePortfolio(name: string, newName: string): Promise<boolean> {
    const result = await renamePortfolioMutate({ name, newName })
    return result!.data!.renamePortfolio
  }

  /** 获取资产配置 */
  async function fetchAllocation(name: string): Promise<AllocationItem[]> {
    const result = await loadAllocation(undefined, { name })
    return result!.allocation
  }

  /** 添加资产配置 */
  async function addAllocation(
    name: string,
    code: string,
    assetName: string,
    assetType: string,
    targetRatio: number,
  ): Promise<AllocationItem> {
    const result = await addAllocationMutate({ name, code, assetName, assetType, targetRatio })
    return result!.data!.addAllocation
  }

  /** 更新资产配置比例 */
  async function updateAllocation(name: string, allocationId: string, targetRatio: number): Promise<boolean> {
    const result = await updateAllocationMutate({ name, allocationId, targetRatio })
    return result!.data!.updateAllocation
  }

  /** 删除资产配置 */
  async function deleteAllocation(name: string, allocationId: string): Promise<boolean> {
    const result = await deleteAllocationMutate({ name, allocationId })
    return result!.data!.deleteAllocation
  }

  /** 入金 */
  async function deposit(name: string, amount: number): Promise<PortfolioOverview> {
    const result = await depositMutate({ name, amount })
    return result!.data!.deposit
  }

  /** 出金 */
  async function withdraw(name: string, allocationId: string, shares: number): Promise<PortfolioOverview> {
    const result = await withdrawMutate({ name, allocationId, shares })
    return result!.data!.withdraw
  }

  /** 再平衡预览 */
  async function rebalancePreview(name: string): Promise<RebalanceResult> {
    const result = await loadRebalancePreview(undefined, { name })
    return result!.rebalancePreview
  }

  /** 执行再平衡 */
  async function rebalanceExecute(name: string, operations: RebalanceOpInput[]): Promise<RebalanceResult> {
    const result = await rebalanceExecuteMutate({ name, operations })
    return result!.data!.rebalanceExecute
  }

  /** 同步价格 */
  async function syncPrices(name: string, codes?: string[]): Promise<SyncPricesResult> {
    const result = await syncPricesMutate({ name, codes: codes ?? null })
    return result!.data!.syncPrices
  }

  return {
    fetchPortfolios,
    fetchPortfolio,
    fetchNavHistory,
    searchTushare,
    createPortfolio,
    deletePortfolio,
    renamePortfolio,
    fetchAllocation,
    addAllocation,
    updateAllocation,
    deleteAllocation,
    deposit,
    withdraw,
    rebalancePreview,
    rebalanceExecute,
    syncPrices,
  }
}
