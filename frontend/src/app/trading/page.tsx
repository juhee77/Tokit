"use client"

import { useState, useEffect, Suspense, useCallback } from "react"
import { useSearchParams } from "next/navigation"
import { Orderbook } from "@/components/sto/orderbook"
import { OrderForm } from "@/components/sto/order-form"
import { CandlestickChart } from "@/components/sto/candlestick-chart"
import { cn } from "@/lib/utils"
import { TrendingUp, TrendingDown, Activity, ChevronDown } from "lucide-react"

import { useTradeStore } from "@/stores/useTradeStore"
import { useOrderBookStream } from "@/hooks/useOrderBookStream"
import { useTradeStream } from "@/hooks/useTradeStream"
import { fetchApi } from "@/lib/api"
import { Asset, Trade } from "@/types"

interface TokenInfo {
  symbol: string
  name: string
  currentPrice: number
  change: number
  changePercent: number
  high24h: number
  low24h: number
  volume24h: number
}

const tokens: TokenInfo[] = [
  {
    symbol: "GNPM",
    name: "서울 강남 프라임 오피스",
    currentPrice: 12500,
    change: 150,
    changePercent: 1.21,
    high24h: 12700,
    low24h: 12200,
    volume24h: 1250000000,
  },
  {
    symbol: "BSND",
    name: "부산 해운대 리조트",
    currentPrice: 7800,
    change: -320,
    changePercent: -3.94,
    high24h: 8200,
    low24h: 7750,
    volume24h: 580000000,
  },
  {
    symbol: "JJIS",
    name: "제주 물류센터",
    currentPrice: 12300,
    change: 80,
    changePercent: 0.65,
    high24h: 12400,
    low24h: 12100,
    volume24h: 320000000,
  },
]

interface RecentTrade {
  id: string
  price: number
  quantity: number
  side: "buy" | "sell"
  time: Date
}

function TradingContent() {
  const searchParams = useSearchParams()
  const querySymbol = searchParams.get("symbol")

  const selectedSymbol = useTradeStore((state) => state.selectedSymbol)
  const assets = useTradeStore((state) => state.assets)
  const recentTrades = useTradeStore((state) => state.recentTrades)
  const setSelectedSymbol = useTradeStore((state) => state.setSelectedSymbol)
  const setAssets = useTradeStore((state) => state.setAssets)
  const setRecentTrades = useTradeStore((state) => state.setRecentTrades)

  // 실시간 스트리밍 가동
  useOrderBookStream(selectedSymbol)
  useTradeStream(selectedSymbol)

  const [initializing, setInitializing] = useState(true)
  const [selectedPrice, setSelectedPrice] = useState<number | undefined>()
  const [showTokenSelector, setShowTokenSelector] = useState(false)

  const [wallets, setWallets] = useState<any[]>([])
  const [userId, setUserId] = useState<number>(1)

  const loadWallets = useCallback(async (currentUserId: number) => {
    try {
      const res = await fetchApi<any>(`/api/users/${currentUserId}/mypage`)
      setWallets(res.wallets || [])
    } catch (e) {
      console.error("Failed to load user wallets:", e)
    }
  }, [])

  useEffect(() => {
    let savedId = 1
    if (typeof window !== "undefined") {
      const raw = localStorage.getItem("tokit_userId")
      if (raw) savedId = parseInt(raw, 10)
    }
    setUserId(savedId)
    loadWallets(savedId)
  }, [loadWallets])

  const krwWallet = wallets.find(w => w.assetSymbol === null || w.assetSymbol === "KRW" || !w.assetSymbol)
  const tokenWallet = wallets.find(w => w.assetSymbol === selectedSymbol)
  const availableBalance = krwWallet ? krwWallet.balance : 0
  const availableTokens = tokenWallet ? tokenWallet.balance : 0

  // Sync query parameter symbol to store
  useEffect(() => {
    if (querySymbol && querySymbol !== selectedSymbol) {
      setSelectedSymbol(querySymbol)
    }
  }, [querySymbol, selectedSymbol, setSelectedSymbol])

  // 초기 자산 목록 로드
  useEffect(() => {
    async function initData() {
      try {
        const loadedAssets = await fetchApi<Asset[]>('/api/assets');
        setAssets(loadedAssets);
        if (loadedAssets.length > 0 && !loadedAssets.some(a => a.symbol === selectedSymbol)) {
          setSelectedSymbol(loadedAssets[0].symbol);
        }
      } catch (e) {
        console.error('Failed to load initial assets list:', e);
        // Fallback for UI testing
        setAssets([
          { id: 1, symbol: "GNPM", name: "서울 강남 프라임 오피스", contractAddress: "0x11...", totalSupply: 1250000 },
          { id: 2, symbol: "APPL-STO", name: "Apple Security Token", contractAddress: "0x22...", totalSupply: 500000 }
        ] as Asset[]);
        setSelectedSymbol("GNPM");
      }

      try {
        const loadedTrades = await fetchApi<Trade[]>(`/api/trades/asset/${selectedSymbol}`);
        setRecentTrades(loadedTrades);
      } catch (e) {
        console.error(`Failed to load trades:`, e);
      } finally {
        setInitializing(false);
      }
    }
    initData();
  }, [selectedSymbol, setAssets, setRecentTrades, setSelectedSymbol]);

  const currentAsset = assets.find((a) => a.symbol === selectedSymbol);
  const currentPrice = recentTrades.length > 0
    ? recentTrades[0].price
    : (currentAsset?.issuePrice || 12500)

  // MVP UI를 위한 임시 매핑 데이터 및 실시간 데이터 바인딩
  const tokenStats = {
    currentPrice: currentPrice,
    change: currentAsset?.issuePrice ? currentPrice - currentAsset.issuePrice : 0,
    changePercent: currentAsset?.issuePrice && currentAsset.issuePrice > 0 
      ? ((currentPrice - currentAsset.issuePrice) / currentAsset.issuePrice) * 100 
      : 0,
    high24h: currentPrice * 1.05,
    low24h: currentPrice * 0.95,
    volume24h: 1250000000,
  };

  const formatKRW = (value: number) => {
    if (value >= 100000000) return `${(value / 100000000).toFixed(2)}억`;
    if (value >= 10000) return `${(value / 10000).toFixed(0)}만`;
    return value.toLocaleString();
  }

  if (initializing) {
    return <div className="flex-1 flex items-center justify-center p-20 text-slate-400">Loading STO Core...</div>;
  }

  return (
    <div className="max-w-[1440px] mx-auto space-y-4">
      {/* Token Selector & Info Header */}
      <div className="bg-card border border-outline-variant rounded p-4 shadow-sm">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          {/* Token Selector */}
          <div className="flex items-center gap-4">
            <div className="relative">
              <button 
                onClick={() => setShowTokenSelector(!showTokenSelector)}
                className="flex items-center gap-3 px-4 py-2 bg-surface-container rounded border border-outline-variant hover:border-secondary transition-colors"
              >
                <div className="w-8 h-8 rounded bg-secondary/10 flex items-center justify-center">
                  <span className="text-secondary font-bold text-sm">{selectedSymbol.slice(0, 2)}</span>
                </div>
                <div className="text-left">
                  <p className="font-semibold text-foreground">{selectedSymbol}</p>
                  <p className="text-xs text-muted-foreground">{currentAsset?.name}</p>
                </div>
                <ChevronDown className={cn(
                  "w-4 h-4 text-muted-foreground transition-transform",
                  showTokenSelector && "rotate-180"
                )} />
              </button>

              {/* Dropdown */}
              {showTokenSelector && (
                <div className="absolute top-full left-0 mt-2 w-72 bg-card border border-outline-variant rounded shadow-lg z-50">
                  {assets.map((asset) => (
                    <button
                      key={asset.symbol}
                      onClick={() => {
                        setSelectedSymbol(asset.symbol)
                        setShowTokenSelector(false)
                      }}
                      className={cn(
                        "w-full flex items-center gap-3 px-4 py-3 hover:bg-surface-container-low transition-colors",
                        asset.symbol === selectedSymbol && "bg-surface-container"
                      )}
                    >
                      <div className="w-8 h-8 rounded bg-secondary/10 flex items-center justify-center">
                        <span className="text-secondary font-bold text-sm">{asset.symbol.slice(0, 2)}</span>
                      </div>
                      <div className="flex-1 text-left">
                        <p className="font-semibold text-foreground">{asset.symbol}</p>
                        <p className="text-xs text-muted-foreground">{asset.name}</p>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Price Display */}
            <div>
              <div className="flex items-baseline gap-2">
                <span className={cn(
                  "text-2xl md:text-3xl font-bold font-mono",
                  tokenStats.changePercent >= 0 ? "text-gain" : "text-loss"
                )}>
                  {tokenStats.currentPrice.toLocaleString()}원
                </span>
                <div className={cn(
                  "flex items-center gap-1 text-sm",
                  tokenStats.changePercent >= 0 ? "text-gain" : "text-loss"
                )}>
                  {tokenStats.changePercent >= 0 ? (
                    <TrendingUp className="h-4 w-4" />
                  ) : (
                    <TrendingDown className="h-4 w-4" />
                  )}
                  <span className="font-medium">
                    {tokenStats.change >= 0 ? "+" : ""}{tokenStats.change.toLocaleString()}
                  </span>
                  <span>
                    ({tokenStats.changePercent >= 0 ? "+" : ""}{tokenStats.changePercent.toFixed(2)}%)
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-3 gap-4 md:gap-8 text-sm">
            <div>
              <p className="text-label-caps text-muted-foreground mb-1">24h High</p>
              <p className="font-mono text-gain font-semibold">{tokenStats.high24h.toLocaleString()}</p>
            </div>
            <div>
              <p className="text-label-caps text-muted-foreground mb-1">24h Low</p>
              <p className="font-mono text-loss font-semibold">{tokenStats.low24h.toLocaleString()}</p>
            </div>
            <div>
              <p className="text-label-caps text-muted-foreground mb-1">24h Volume</p>
              <p className="font-mono text-foreground font-semibold">{formatKRW(tokenStats.volume24h)}원</p>
            </div>
          </div>
        </div>
      </div>

      {/* Main Trading Area */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* Left Section (8 cols) */}
        <div className="lg:col-span-8 space-y-4">
          {/* Candlestick Chart */}
          <CandlestickChart symbol={selectedSymbol} currentPrice={tokenStats.currentPrice} />
          
          {/* Sub-grid: OrderForm & Recent Trades */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <OrderForm
              symbol={selectedSymbol}
              currentPrice={tokenStats.currentPrice}
              selectedPrice={selectedPrice}
              availableBalance={availableBalance}
              availableTokens={availableTokens}
              onOrderSubmit={() => loadWallets(userId)}
            />
            
            <div className="bg-card border border-outline-variant rounded shadow-sm h-full flex flex-col">
              <div className="p-4 border-b border-outline-variant flex items-center gap-2">
                <Activity className="h-4 w-4 text-secondary" />
                <h3 className="font-semibold text-foreground">최근 체결</h3>
              </div>
              
              {/* Header */}
              <div className="grid grid-cols-3 text-label-caps text-muted-foreground px-4 py-2 border-b border-surface-container-highest">
                <span>가격</span>
                <span className="text-right">수량</span>
                <span className="text-right">시간</span>
              </div>

              {/* Trades */}
              <div className="max-h-[300px] overflow-auto flex-1">
                {recentTrades.map((trade) => {
                  const date = new Date(trade.createdAt);
                  const isGain = trade.price >= tokenStats.currentPrice;
                  return (
                    <div
                      key={trade.id}
                      className="grid grid-cols-3 text-sm px-4 py-1.5 hover:bg-surface-container-low transition-colors"
                    >
                      <span className={cn(
                        "font-mono",
                        isGain ? "text-gain" : "text-loss"
                      )}>
                        {trade.price.toLocaleString()}
                      </span>
                      <span className="text-right font-mono text-foreground">
                        {trade.quantity.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}
                      </span>
                      <span className="text-right text-muted-foreground text-xs">
                        {date.toLocaleTimeString("ko-KR", {
                          hour: "2-digit",
                          minute: "2-digit",
                          second: "2-digit",
                        })}
                      </span>
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        </div>

        {/* Right Section: Orderbook (4 cols) */}
        <div className="lg:col-span-4">
          <Orderbook 
            symbol={selectedSymbol} 
            lastPrice={tokenStats.currentPrice}
            priceChange={tokenStats.change}
            priceChangePercent={tokenStats.changePercent}
            onPriceSelect={setSelectedPrice}
          />
        </div>
      </div>
    </div>
  )
}

export default function TradingPage() {
  return (
    <Suspense fallback={<div className="flex-1 flex items-center justify-center p-20 text-slate-400">Loading STO Core...</div>}>
      <TradingContent />
    </Suspense>
  )
}
