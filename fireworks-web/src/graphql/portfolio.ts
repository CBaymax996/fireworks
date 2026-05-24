import { gql } from '@apollo/client/core'

export const PORTFOLIOS = gql`
  query Portfolios {
    portfolios {
      id
      name
      totalNav
      assetCount
    }
  }
`

export const PORTFOLIO = gql`
  query Portfolio($name: String!) {
    portfolio(name: $name) {
      id
      name
      totalNav
      totalCost
      totalProfit
      totalProfitRate
      cash
      holdings {
        allocationId
        code
        name
        assetType
        targetRatio
        actualRatio
        shares
        avgCost
        currentPrice
        marketValue
        profit
        profitRate
      }
    }
  }
`

export const NAV_HISTORY = gql`
  query NavHistory($name: String!, $days: Int) {
    navHistory(name: $name, days: $days) {
      portfolioName
      data {
        date
        nav
      }
    }
  }
`

export const TUSHARE_SEARCH = gql`
  query TushareSearch($keyword: String!) {
    tushareSearch(keyword: $keyword) {
      code
      name
      assetType
    }
  }
`

export const CREATE_PORTFOLIO = gql`
  mutation CreatePortfolio($name: String!) {
    createPortfolio(name: $name) {
      id
      name
      totalNav
      assetCount
    }
  }
`

export const DELETE_PORTFOLIO = gql`
  mutation DeletePortfolio($name: String!) {
    deletePortfolio(name: $name)
  }
`

export const RENAME_PORTFOLIO = gql`
  mutation RenamePortfolio($name: String!, $newName: String!) {
    renamePortfolio(name: $name, newName: $newName)
  }
`

export const ALLOCATION = gql`
  query Allocation($name: String!) {
    allocation(name: $name) {
      id
      code
      name
      assetType
      targetRatio
    }
  }
`

export const ADD_ALLOCATION = gql`
  mutation AddAllocation($name: String!, $code: String!, $assetName: String!, $assetType: String, $targetRatio: Float!) {
    addAllocation(name: $name, code: $code, assetName: $assetName, assetType: $assetType, targetRatio: $targetRatio) {
      id
      code
      name
      assetType
      targetRatio
    }
  }
`

export const UPDATE_ALLOCATION = gql`
  mutation UpdateAllocation($name: String!, $allocationId: Int!, $targetRatio: Float!) {
    updateAllocation(name: $name, allocationId: $allocationId, targetRatio: $targetRatio)
  }
`

export const DELETE_ALLOCATION = gql`
  mutation DeleteAllocation($name: String!, $allocationId: Int!) {
    deleteAllocation(name: $name, allocationId: $allocationId)
  }
`

export const DEPOSIT = gql`
  mutation Deposit($name: String!, $amount: Float!) {
    deposit(name: $name, amount: $amount) {
      id
      name
      totalNav
      totalCost
      totalProfit
      totalProfitRate
      cash
      holdings {
        allocationId
        code
        name
        assetType
        targetRatio
        actualRatio
        shares
        avgCost
        currentPrice
        marketValue
        profit
        profitRate
      }
    }
  }
`

export const WITHDRAW = gql`
  mutation Withdraw($name: String!, $allocationId: Int!, $shares: Int!) {
    withdraw(name: $name, allocationId: $allocationId, shares: $shares) {
      id
      name
      totalNav
      totalCost
      totalProfit
      totalProfitRate
      cash
      holdings {
        allocationId
        code
        name
        assetType
        targetRatio
        actualRatio
        shares
        avgCost
        currentPrice
        marketValue
        profit
        profitRate
      }
    }
  }
`

export const REBALANCE_PREVIEW = gql`
  mutation RebalancePreview($name: String!) {
    rebalancePreview(name: $name) {
      portfolioName
      targetNav
      operations {
        allocationId
        code
        name
        assetType
        targetRatio
        currentRatio
        diff
        action
        shares
      }
      executed
    }
  }
`

export const REBALANCE_EXECUTE = gql`
  mutation RebalanceExecute($name: String!, $operations: [RebalanceOpInput!]!) {
    rebalanceExecute(name: $name, operations: $operations) {
      portfolioName
      targetNav
      operations {
        allocationId
        code
        name
        assetType
        targetRatio
        currentRatio
        diff
        action
        shares
      }
      executed
    }
  }
`

export const SYNC_PRICES = gql`
  mutation SyncPrices($name: String!, $codes: [String!]) {
    syncPrices(name: $name, codes: $codes) {
      portfolioName
      successCount
      totalCount
      results {
        code
        name
        price
        error
      }
    }
  }
`
