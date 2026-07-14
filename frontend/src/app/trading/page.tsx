"use client"

import { useState, useEffect, Suspense, useCallback } from "react"
import { useSearchParams } from "next/navigation"
import { Orderbook } from "@/components/sto/orderbook"
import { OrderForm } from "@/components/sto/order-form"
import { CandlestickChart } from "@/components/sto/candlestick-chart"
import { cn } from "@/lib/utils"
import { TrendingUp, TrendingDown, Activity, ChevronDown, MessageSquare, ChevronRight } from "lucide-react"

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
  
  // 토론방 탭 관련 상태 및 비동기 패치
  const [activeRightTab, setActiveRightTab] = useState<'orderbook' | 'discussion'>('orderbook')
  const [discussionPosts, setDiscussionPosts] = useState<any[]>([])
  const [discussionLoading, setDiscussionLoading] = useState(false)
  const [quickTitle, setQuickTitle] = useState('')
  const [quickContent, setQuickContent] = useState('')

  const loadDiscussionPosts = useCallback(async (assetId: number) => {
    setDiscussionLoading(true)
    try {
      const pageData = await fetchApi<any>(`/api/posts?assetId=${assetId}&size=50`)
      setDiscussionPosts(pageData.content || [])
    } catch (e) {
      console.error("Failed to load asset discussions:", e)
    } finally {
      setDiscussionLoading(false)
    }
  }, [])

  const [showTokenSelector, setShowTokenSelector] = useState(false)
  const [searchTerm, setSearchTerm] = useState("")
  const [activeCategory, setActiveCategory] = useState<"ALL" | "REAL_ESTATE" | "INFRA" | "ART_OTHER">("ALL")

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

  // 자동 데이터 로드 이펙트
  useEffect(() => {
    if (currentAsset && activeRightTab === 'discussion') {
      loadDiscussionPosts(currentAsset.id)
    }
  }, [currentAsset, activeRightTab, loadDiscussionPosts])

  const handleCreateQuickPost = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!currentAsset || !quickTitle.trim() || !quickContent.trim()) return

    const idempotencyKey = crypto.randomUUID()
    try {
      await fetchApi<any>('/api/posts', {
        method: 'POST',
        headers: {
          'X-Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          title: quickTitle,
          content: quickContent,
          userId: userId,
          assetId: currentAsset.id,
        }),
      })

      setQuickTitle('')
      setQuickContent('')
      loadDiscussionPosts(currentAsset.id)
    } catch (err: any) {
      alert(err.message || 'Failed to post discussion.')
    }
  }

  const filteredAssets = assets.filter((asset) => {
    const matchesSearch = asset.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
                          asset.name.toLowerCase().includes(searchTerm.toLowerCase());
    
    if (!matchesSearch) return false;
    if (activeCategory === "ALL") return true;

    const name = asset.name.toLowerCase();
    const isRealEstate = name.includes("빌딩") || name.includes("오피스") || name.includes("주택") ||
                         name.includes("리조트") || name.includes("콘도") || name.includes("호텔") ||
                         name.includes("스페이스") || name.includes("코워킹");
    
    const isInfra = name.includes("발전소") || name.includes("발전기") || name.includes("데이터센터") ||
                    name.includes("물류") || name.includes("창고");

    if (activeCategory === "REAL_ESTATE") return isRealEstate;
    if (activeCategory === "INFRA") return isInfra;
    if (activeCategory === "ART_OTHER") return !isRealEstate && !isInfra;

    return true;
  });

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
                <div className="absolute top-full left-0 mt-2 w-80 bg-card border border-outline-variant rounded shadow-lg z-50 overflow-hidden">
                  {/* Search and Category Header */}
                  <div className="p-3 border-b border-outline-variant bg-card sticky top-0 z-10 space-y-2">
                    <input 
                      type="text"
                      placeholder="자산명 또는 심볼 검색..."
                      value={searchTerm}
                      onChange={(e) => setSearchTerm(e.target.value)}
                      className="w-full px-3 py-1.5 text-xs bg-surface-container border border-outline-variant rounded focus:outline-none focus:border-secondary text-foreground placeholder:text-muted-foreground"
                    />
                    <div className="flex gap-1 text-[10px] font-medium overflow-x-auto pb-1 scrollbar-none">
                      <button 
                        onClick={() => setActiveCategory("ALL")}
                        className={cn("px-2 py-1 rounded-full whitespace-nowrap transition-colors", activeCategory === "ALL" ? "bg-secondary text-secondary-foreground" : "bg-surface-container-high text-muted-foreground hover:text-foreground")}
                      >전체</button>
                      <button 
                        onClick={() => setActiveCategory("REAL_ESTATE")}
                        className={cn("px-2 py-1 rounded-full whitespace-nowrap transition-colors", activeCategory === "REAL_ESTATE" ? "bg-secondary text-secondary-foreground" : "bg-surface-container-high text-muted-foreground hover:text-foreground")}
                      >부동산</button>
                      <button 
                        onClick={() => setActiveCategory("INFRA")}
                        className={cn("px-2 py-1 rounded-full whitespace-nowrap transition-colors", activeCategory === "INFRA" ? "bg-secondary text-secondary-foreground" : "bg-surface-container-high text-muted-foreground hover:text-foreground")}
                      >에너지/인프라</button>
                      <button 
                        onClick={() => setActiveCategory("ART_OTHER")}
                        className={cn("px-2 py-1 rounded-full whitespace-nowrap transition-colors", activeCategory === "ART_OTHER" ? "bg-secondary text-secondary-foreground" : "bg-surface-container-high text-muted-foreground hover:text-foreground")}
                      >미술품/기타</button>
                    </div>
                  </div>

                  {/* Scrollable List */}
                  <div className="max-h-72 overflow-y-auto scrollbar-thin scrollbar-thumb-outline-variant">
                    {filteredAssets.length === 0 ? (
                      <div className="p-4 text-xs text-center text-muted-foreground">검색 결과가 없습니다.</div>
                    ) : (
                      filteredAssets.map((asset) => (
                        <button
                          key={asset.symbol}
                          onClick={() => {
                            setSelectedSymbol(asset.symbol)
                            setShowTokenSelector(false)
                            setSearchTerm("") // Reset search term
                          }}
                          className={cn(
                            "w-full flex items-center gap-3 px-4 py-2.5 hover:bg-surface-container-low transition-colors",
                            asset.symbol === selectedSymbol && "bg-surface-container"
                          )}
                        >
                          <div className="w-8 h-8 rounded bg-secondary/10 flex items-center justify-center shrink-0">
                            <span className="text-secondary font-bold text-xs">{asset.symbol.slice(0, 2)}</span>
                          </div>
                          <div className="flex-1 text-left min-w-0">
                            <p className="font-semibold text-foreground text-xs truncate">{asset.symbol}</p>
                            <p className="text-xs text-muted-foreground truncate">{asset.name}</p>
                          </div>
                        </button>
                      ))
                    )}
                  </div>
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

        {/* Right Section: Orderbook or Discussion (4 cols) */}
        <div className="lg:col-span-4 flex flex-col bg-card border border-outline-variant rounded shadow-sm overflow-hidden h-[600px]">
          {/* Tab Header */}
          <div className="flex border-b border-outline-variant bg-surface-container-low shrink-0">
            <button
              onClick={() => setActiveRightTab('orderbook')}
              className={cn(
                "flex-1 py-3 text-sm font-bold text-center border-b-2 transition-all cursor-pointer",
                activeRightTab === 'orderbook'
                  ? "border-primary text-primary bg-card"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              )}
            >
              호가 분석
            </button>
            <button
              onClick={() => setActiveRightTab('discussion')}
              className={cn(
                "flex-1 py-3 text-sm font-bold text-center border-b-2 transition-all cursor-pointer",
                activeRightTab === 'discussion'
                  ? "border-primary text-primary bg-card"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              )}
            >
              실시간 토론
            </button>
          </div>

          {/* Tab Content */}
          <div className="flex-1 overflow-y-auto">
            {activeRightTab === 'orderbook' ? (
              <Orderbook 
                symbol={selectedSymbol} 
                lastPrice={tokenStats.currentPrice}
                priceChange={tokenStats.change}
                priceChangePercent={tokenStats.changePercent}
                onPriceSelect={setSelectedPrice}
              />
            ) : (
              <div className="p-4 flex flex-col h-full gap-4">
                {/* Community Direct Link */}
                <div className="flex justify-between items-center bg-primary/5 border border-primary/10 rounded-xl p-3 shrink-0">
                  <span className="text-xs text-muted-foreground">더 많은 의견을 보려면?</span>
                  <a
                    href="/community"
                    className="text-xs text-primary font-bold hover:underline flex items-center gap-0.5"
                  >
                    커뮤니티 바로가기
                    <ChevronRight size={14} />
                  </a>
                </div>

                {/* Quick Write Form */}
                <form onSubmit={handleCreateQuickPost} className="flex flex-col gap-2 p-3 bg-surface-container-low border border-outline-variant rounded-xl shrink-0">
                  <input
                    type="text"
                    required
                    placeholder="토론 제목..."
                    value={quickTitle}
                    onChange={e => setQuickTitle(e.target.value)}
                    className="w-full px-3 py-1.5 bg-background border border-outline-variant rounded-lg text-xs focus:outline-none focus:border-primary text-foreground placeholder:text-muted-foreground"
                  />
                  <div className="flex gap-2">
                    <input
                      type="text"
                      required
                      placeholder="의견을 자유롭게 입력하세요..."
                      value={quickContent}
                      onChange={e => setQuickContent(e.target.value)}
                      className="flex-1 px-3 py-1.5 bg-background border border-outline-variant rounded-lg text-xs focus:outline-none focus:border-primary text-foreground placeholder:text-muted-foreground"
                    />
                    <button
                      type="submit"
                      className="px-3 py-1.5 bg-primary text-primary-foreground rounded-lg text-xs font-bold hover:opacity-90 transition-all cursor-pointer"
                    >
                      등록
                    </button>
                  </div>
                </form>

                {/* Discussion List */}
                <div className="flex-1 flex flex-col gap-3 min-h-0">
                  {discussionLoading ? (
                    <div className="flex-grow flex flex-col justify-center items-center gap-2 py-10">
                      <div className="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin"></div>
                      <span className="text-xs text-muted-foreground">로딩 중...</span>
                    </div>
                  ) : discussionPosts.length === 0 ? (
                    <div className="flex-grow flex flex-col justify-center items-center py-10 text-center">
                      <MessageSquare className="text-muted-foreground mb-2" size={24} />
                      <p className="text-xs text-muted-foreground">첫 의견을 작성해 보세요!</p>
                    </div>
                  ) : (
                    <div className="flex-grow overflow-y-auto space-y-3 pr-1 max-h-[300px]">
                      {discussionPosts.map(post => (
                        <div key={post.id} className="p-3 bg-surface-container-low border border-outline-variant rounded-xl flex flex-col gap-1.5 hover:border-primary/30 transition-all">
                          <div className="flex justify-between items-center text-[10px] text-muted-foreground">
                            <span className="font-semibold text-foreground">{post.userName}</span>
                            <span>{new Date(post.createdAt).toLocaleDateString()}</span>
                          </div>
                          <h4 className="text-xs font-bold text-foreground line-clamp-1">{post.title}</h4>
                          <p className="text-[11px] text-muted-foreground line-clamp-2 leading-relaxed">{post.content}</p>
                          <div className="flex justify-end pt-1 border-t border-outline-variant/30 text-[9px] text-primary font-bold">
                            댓글 {post.commentsCount}개
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
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
