"use client"

import { useState, useEffect, useCallback } from "react"
import { cn } from "@/lib/utils"
import { Wallet, Lock, ArrowUpRight, ArrowDownRight, X, ArrowRight, Building2, Loader2 } from "lucide-react"
import { fetchApi } from "@/lib/api"
import { toast } from "sonner"

interface AssetData {
  totalBalance: number
  availableBalance: number
  lockedBalance: number
  totalTokenValue: number
  tokens: TokenHolding[]
}

interface TokenHolding {
  symbol: string
  name: string
  quantity: number
  averagePrice: number
  currentPrice: number
  value: number
  change: number
  changePercent: number
}

interface OrderHistory {
  id: string
  symbol: string
  side: "buy" | "sell"
  type: "limit" | "market"
  price: number
  quantity: number
  filledQuantity: number
  status: "pending" | "partial" | "filled" | "cancelled"
  createdAt: Date
}

const mockAssets: AssetData = {
  totalBalance: 15000000,
  availableBalance: 5000000,
  lockedBalance: 10000000,
  totalTokenValue: 45000000,
  tokens: [
    {
      symbol: "GNPM",
      name: "서울 강남 프라임 오피스",
      quantity: 2500,
      averagePrice: 10000,
      currentPrice: 12500,
      value: 31250000,
      change: 6250000,
      changePercent: 25.0,
    },
    {
      symbol: "BSND",
      name: "부산 해운대 리조트",
      quantity: 1200,
      averagePrice: 8500,
      currentPrice: 7800,
      value: 9360000,
      change: -840000,
      changePercent: -8.24,
    },
    {
      symbol: "JJIS",
      name: "제주 물류센터",
      quantity: 450,
      averagePrice: 12000,
      currentPrice: 12300,
      value: 5535000,
      change: 135000,
      changePercent: 2.5,
    },
  ],
}

const mockOrders: OrderHistory[] = [
  {
    id: "1",
    symbol: "GNPM",
    side: "buy",
    type: "limit",
    price: 12500,
    quantity: 100,
    filledQuantity: 100,
    status: "filled",
    createdAt: new Date(Date.now() - 1000 * 60 * 30),
  },
  {
    id: "2",
    symbol: "GNPM",
    side: "buy",
    type: "limit",
    price: 12400,
    quantity: 200,
    filledQuantity: 120,
    status: "partial",
    createdAt: new Date(Date.now() - 1000 * 60 * 15),
  },
  {
    id: "3",
    symbol: "BSND",
    side: "sell",
    type: "market",
    price: 7800,
    quantity: 50,
    filledQuantity: 0,
    status: "pending",
    createdAt: new Date(Date.now() - 1000 * 60 * 5),
  },
  {
    id: "4",
    symbol: "JJIS",
    side: "buy",
    type: "limit",
    price: 12100,
    quantity: 100,
    filledQuantity: 0,
    status: "cancelled",
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 2),
  },
]

const tokenMetadata: Record<string, { name: string; currentPrice: number; averagePrice: number }> = {
  HDYT: { name: "홍대 청년주택 제1호", currentPrice: 5000, averagePrice: 5000 },
  GNPM: { name: "서울 강남 프라임 오피스", currentPrice: 10000, averagePrice: 10000 },
  BSND: { name: "부산 해운대 리조트", currentPrice: 7800, averagePrice: 8500 },
  JJIS: { name: "제주 물류센터", currentPrice: 12300, averagePrice: 12000 },
}

export function WalletHistory() {
  const [assets, setAssets] = useState<AssetData>(mockAssets)
  const [orders, setOrders] = useState<OrderHistory[]>(mockOrders)
  const [activeTab, setActiveTab] = useState<"assets" | "orders">("assets")
  const [loading, setLoading] = useState(true)

  // 가스리스 이체(무상 양도) 모달 관련 상태
  const [isTransferModalOpen, setIsTransferModalOpen] = useState(false)
  const [transferToken, setTransferToken] = useState<TokenHolding | null>(null)
  const [toAddress, setToAddress] = useState('')
  const [transferAmount, setTransferAmount] = useState('')
  const [transferLoading, setTransferLoading] = useState(false)
  const [currentUserAddress, setCurrentUserAddress] = useState('0x70997970C51812dc3A010C7d01b50e0d17dc79C8') // default

  const loadWalletData = useCallback(async (savedId: number) => {
    setLoading(true)
    try {
      const res = await fetchApi<any>(`/api/users/${savedId}/mypage`)
      if (res && res.user && res.user.walletAddress) {
        setCurrentUserAddress(res.user.walletAddress)
      }
      if (res && res.wallets) {
        const krwWallet = res.wallets.find((w: any) => w.assetSymbol === "KRW" || !w.assetSymbol)
        const availableBalance = krwWallet ? krwWallet.balance : 0
        const lockedBalance = krwWallet ? krwWallet.lockedBalance : 0
        
        const tokenHoldings: TokenHolding[] = res.wallets
          .filter((w: any) => w.assetSymbol && w.assetSymbol !== "KRW")
          .map((w: any) => {
            const symbol = w.assetSymbol
            const meta = tokenMetadata[symbol] || { name: `${symbol} 토큰증권`, currentPrice: 10000, averagePrice: 10000 }
            const quantity = w.balance + w.lockedBalance
            const value = quantity * meta.currentPrice
            const changePercent = symbol === "BSND" ? -8.24 : symbol === "HDYT" ? 1.2 : symbol === "JJIS" ? 2.5 : symbol === "GNPM" ? 25.0 : 0.0
            const change = value * (changePercent / (100 + changePercent))
            
            return {
              symbol,
              name: meta.name,
              quantity,
              averagePrice: meta.averagePrice,
              currentPrice: meta.currentPrice,
              value,
              change,
              changePercent
            }
          })

        const totalTokenValue = tokenHoldings.reduce((sum, t) => sum + t.value, 0)
        
        setAssets({
          totalBalance: availableBalance + lockedBalance,
          availableBalance,
          lockedBalance,
          totalTokenValue,
          tokens: tokenHoldings
        })
      }
      
      if (res && res.orders) {
        const mappedOrders: OrderHistory[] = res.orders.map((o: any) => ({
          id: o.id.toString(),
          symbol: o.assetSymbol,
          side: o.orderType.toLowerCase() as "buy" | "sell",
          type: "limit",
          price: o.price,
          quantity: o.quantity,
          filledQuantity: o.quantity - o.remainingQuantity,
          status: o.status === "OPEN" ? "pending" : o.status === "PARTIAL" ? "partial" : o.status === "FILLED" ? "filled" : "cancelled",
          createdAt: new Date(o.createdAt)
        }))
        setOrders(mappedOrders)
      }
    } catch (e: any) {
      console.error("Failed to fetch wallet page data:", e)
      if (e.message && (
        e.message.includes("not found") || 
        e.message.includes("NOT_FOUND") || 
        e.message.includes("User not found")
      )) {
        try {
          const signupRes = await fetchApi<any>("/api/users/signup", {
            method: "POST",
            body: JSON.stringify({
              email: "test-investor@tokit.com",
              name: "김토킷",
              walletAddress: "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
            })
          })
          const newId = signupRes.id
          if (typeof window !== "undefined") {
            localStorage.setItem("tokit_userId", newId.toString())
          }
          // Recursive retry
          loadWalletData(newId)
        } catch (signupErr: any) {
          console.error("Auto signup inside wallet page failed", signupErr)
          toast.error("지갑용 계정 자동 생성 실패: " + signupErr.message)
        }
      } else {
        toast.error("지갑 데이터 로드 실패: " + e.message)
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let savedId = 1
    if (typeof window !== "undefined") {
      const raw = localStorage.getItem("tokit_userId")
      if (raw) savedId = parseInt(raw, 10)
    }
    loadWalletData(savedId)
  }, [loadWalletData])

  const handleRelayTransfer = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!transferToken || !toAddress.trim() || !transferAmount.trim()) return

    const amountVal = parseFloat(transferAmount)
    if (isNaN(amountVal) || amountVal <= 0) {
      alert("올바른 이체 수량을 입력해주세요.")
      return
    }

    if (amountVal > transferToken.quantity) {
      alert("보유 수량이 부족합니다.")
      return
    }

    setTransferLoading(true)
    try {
      // 1. Get Nonce from Backend
      const nonceRes = await fetchApi<any>(`/api/relayer/nonce/${currentUserAddress}`)
      const nonce = nonceRes.nonce

      // 2. Format plain message
      const plainMessage = currentUserAddress.toLowerCase() + ":" +
                           toAddress.toLowerCase() + ":" +
                           transferToken.symbol + ":" +
                           amountVal.toString() + ":" +
                           nonce

      // 3. Request personal_sign from MetaMask
      let signature = ""
      if (typeof window !== "undefined" && (window as any).ethereum) {
        try {
          await (window as any).ethereum.request({ method: 'eth_requestAccounts' })
          signature = await (window as any).ethereum.request({
            method: 'personal_sign',
            params: [plainMessage, currentUserAddress],
          })
        } catch (signErr: any) {
          throw new Error("지갑 서명 승인이 취소되었거나 실패했습니다: " + signErr.message)
        }
      } else {
        throw new Error("MetaMask 등 Web3 지갑이 감지되지 않았습니다. 브라우저 지갑 확장을 설치한 후 이체를 승인해주세요.")
      }

      // 4. Send relay transfer request to backend
      await fetchApi<any>('/api/relayer/transfer', {
        method: 'POST',
        body: JSON.stringify({
          fromAddress: currentUserAddress,
          toAddress: toAddress,
          assetSymbol: transferToken.symbol,
          amount: amountVal,
          nonce: nonce,
          signature: signature
        })
      })

      alert("가스비 대납 이체(무상 양도)가 온체인에서 성공적으로 완료되었습니다!")
      setIsTransferModalOpen(false)
      setToAddress('')
      setTransferAmount('')
      
      let savedId = 1
      if (typeof window !== "undefined") {
        const raw = localStorage.getItem("tokit_userId")
        if (raw) savedId = parseInt(raw, 10)
      }
      loadWalletData(savedId)
    } catch (err: any) {
      alert(err.message || "이체 요청 중 오류가 발생했습니다.")
    } finally {
      setTransferLoading(false)
    }
  }

  const formatKRW = (value: number) => {
    if (Math.abs(value) >= 100000000) {
      return `${(value / 100000000).toFixed(2)}억원`
    }
    if (Math.abs(value) >= 10000) {
      return `${(value / 10000).toFixed(0)}만원`
    }
    return `${value.toLocaleString()}원`
  }

  const formatTime = (date: Date) => {
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    const minutes = Math.floor(diff / (1000 * 60))
    const hours = Math.floor(diff / (1000 * 60 * 60))

    if (minutes < 60) return `${minutes}분 전`
    if (hours < 24) return `${hours}시간 전`
    return date.toLocaleDateString("ko-KR")
  }

  if (loading) {
    return (
      <div className="flex-grow flex flex-col items-center justify-center py-20 text-slate-400 gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-secondary" />
        <p className="text-sm font-semibold font-sans">자산 및 보유 토큰 로딩 중...</p>
      </div>
    )
  }

  const statusConfig = {
    pending: { label: "미체결", className: "border-warning text-warning bg-warning/10" },
    partial: { label: "부분 체결", className: "border-secondary text-secondary bg-secondary/10" },
    filled: { label: "완전 체결", className: "border-green-600 text-green-600 bg-green-50" },
    cancelled: { label: "취소됨", className: "border-outline text-muted-foreground bg-surface-container" },
  }

  return (
    <div className="space-y-6">
      {/* Balance Overview */}
      <div className="bg-card border border-outline-variant rounded shadow-sm p-4 md:p-6">
        <div className="flex items-center gap-2 mb-4">
          <Wallet className="h-5 w-5 text-secondary" />
          <h2 className="font-semibold text-foreground">자산 현황</h2>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Total Assets */}
          <div className="bg-surface-container-low rounded p-4">
            <p className="text-label-caps text-muted-foreground mb-1">총 보유 자산</p>
            <p className="text-2xl font-bold text-foreground font-mono">
              {formatKRW(assets.totalBalance + assets.totalTokenValue)}
            </p>
          </div>

          {/* Available Balance */}
          <div className="bg-surface-container-low rounded p-4">
            <p className="text-label-caps text-muted-foreground mb-1">사용 가능 금액</p>
            <p className="text-2xl font-bold text-secondary font-mono">
              {formatKRW(assets.availableBalance)}
            </p>
          </div>

          {/* Locked Balance */}
          <div className="bg-surface-container-low rounded p-4">
            <div className="flex items-center gap-1 mb-1">
              <Lock className="h-3 w-3 text-muted-foreground" />
              <p className="text-label-caps text-muted-foreground">주문 중 홀딩</p>
            </div>
            <p className="text-2xl font-bold text-warning font-mono">
              {formatKRW(assets.lockedBalance)}
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              미체결 주문으로 인해 잠김
            </p>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="bg-card border border-outline-variant rounded shadow-sm overflow-hidden">
        <div className="flex border-b border-outline-variant">
          <button
            onClick={() => setActiveTab("assets")}
            className={cn(
              "flex-1 px-4 py-3 text-sm font-medium transition-colors",
              activeTab === "assets"
                ? "text-secondary border-b-2 border-secondary bg-surface-container-low"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            보유 토큰
          </button>
          <button
            onClick={() => setActiveTab("orders")}
            className={cn(
              "flex-1 px-4 py-3 text-sm font-medium transition-colors",
              activeTab === "orders"
                ? "text-secondary border-b-2 border-secondary bg-surface-container-low"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            체결 내역
          </button>
        </div>

        {/* Token Holdings */}
        {activeTab === "assets" && (
          <div className="divide-y divide-surface-container-highest">
            {assets.tokens.map((token) => (
              <div key={token.symbol} className="p-4 hover:bg-surface-container-low transition-colors cursor-pointer">
                <div className="flex items-start justify-between">
                  <div className="flex items-start gap-3">
                    <div className="w-10 h-10 rounded bg-surface-container flex items-center justify-center border border-surface-variant">
                      <Building2 className="w-5 h-5 text-muted-foreground" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-foreground">{token.symbol}</span>
                        <span className="text-sm text-muted-foreground">{token.name}</span>
                      </div>
                      <p className="text-sm text-muted-foreground mt-1">
                        {token.quantity.toLocaleString()}주 · 평균 {token.averagePrice.toLocaleString()}원
                      </p>
                    </div>
                  </div>
                  <div className="text-right flex flex-col items-end gap-2 shrink-0">
                    <p className="font-semibold text-foreground font-mono">{formatKRW(token.value)}</p>
                    <div className={cn(
                      "flex items-center justify-end gap-1 text-sm",
                      token.changePercent >= 0 ? "text-gain" : "text-loss"
                    )}>
                      {token.changePercent >= 0 ? (
                        <ArrowUpRight className="h-3 w-3" />
                      ) : (
                        <ArrowDownRight className="h-3 w-3" />
                      )}
                      <span>{token.changePercent >= 0 ? "+" : ""}{token.changePercent.toFixed(2)}%</span>
                      <span className="text-muted-foreground">
                        ({token.change >= 0 ? "+" : ""}{formatKRW(token.change)})
                      </span>
                    </div>
                    {token.quantity > 0 && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          setTransferToken(token)
                          setIsTransferModalOpen(true)
                        }}
                        className="mt-1 px-3 py-1 bg-secondary/15 hover:bg-secondary/25 text-secondary border border-secondary/25 hover:border-secondary/40 rounded-lg text-xs font-bold transition-all cursor-pointer"
                      >
                        무상 양도
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
            
            {/* View All Link */}
            <div className="p-4 text-center">
              <button className="text-secondary text-sm font-medium hover:underline inline-flex items-center gap-1">
                모든 보유 토큰 보기
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}

        {/* Order History */}
        {activeTab === "orders" && (
          <>
            {/* Header - Hidden on mobile, visible on md+ */}
            <div className="hidden md:grid grid-cols-6 gap-4 text-label-caps text-muted-foreground px-4 py-3 border-b border-surface-container-highest bg-surface-container-low">
              <span>종목</span>
              <span>구분</span>
              <span className="text-right">가격</span>
              <span className="text-right">수량</span>
              <span className="text-center">상태</span>
              <span className="text-right">시간</span>
            </div>

            <div className="divide-y divide-surface-container-highest">
              {orders.map((order) => (
                <div 
                  key={order.id} 
                  className="p-4 hover:bg-surface-container-low transition-colors"
                >
                  {/* Mobile Layout */}
                  <div className="md:hidden space-y-2">
                    <div className="flex justify-between items-start">
                      <div>
                        <span className="font-medium text-foreground">{order.symbol}</span>
                        <span className={cn(
                          "ml-2 text-sm font-medium",
                          order.side === "buy" ? "text-gain" : "text-loss"
                        )}>
                          {order.side === "buy" ? "매수" : "매도"}
                        </span>
                      </div>
                      <span className={cn(
                        "px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border",
                        statusConfig[order.status].className
                      )}>
                        {statusConfig[order.status].label}
                      </span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-muted-foreground">{order.price.toLocaleString()}원 × {order.filledQuantity}/{order.quantity}</span>
                      <span className="text-muted-foreground">{formatTime(order.createdAt)}</span>
                    </div>
                  </div>

                  {/* Desktop Layout */}
                  <div className="hidden md:grid grid-cols-6 gap-4 items-center">
                    <span className="font-medium text-foreground">{order.symbol}</span>
                    <span className={cn(
                      "text-sm font-medium",
                      order.side === "buy" ? "text-gain" : "text-loss"
                    )}>
                      {order.side === "buy" ? "매수" : "매도"}
                      <span className="text-muted-foreground font-normal ml-1">
                        {order.type === "limit" ? "지정가" : "시장가"}
                      </span>
                    </span>
                    <span className="text-right font-mono text-foreground">
                      {order.price.toLocaleString()}
                    </span>
                    <span className="text-right font-mono">
                      <span className="text-foreground">{order.filledQuantity.toLocaleString()}</span>
                      <span className="text-muted-foreground">/{order.quantity.toLocaleString()}</span>
                    </span>
                    <div className="text-center">
                      <span className={cn(
                        "px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border inline-block",
                        statusConfig[order.status].className
                      )}>
                        {statusConfig[order.status].label}
                      </span>
                    </div>
                    <div className="flex items-center justify-end gap-2">
                      <span className="text-sm text-muted-foreground">{formatTime(order.createdAt)}</span>
                      {(order.status === "pending" || order.status === "partial") && (
                        <button className="p-1 rounded hover:bg-surface-container transition-colors text-muted-foreground hover:text-destructive">
                          <X className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* View All Link */}
            <div className="p-4 text-center border-t border-surface-container-highest">
              <button className="text-secondary text-sm font-medium hover:underline inline-flex items-center gap-1">
                모든 체결 내역 보기
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </>
        )}
      </div>

      {/* Gasless Transfer Modal */}
      {isTransferModalOpen && transferToken && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-card border border-outline-variant rounded-2xl w-full max-w-md shadow-2xl overflow-hidden flex flex-col animate-in fade-in zoom-in-95 duration-200">
            {/* Modal Header */}
            <div className="p-4 border-b border-outline-variant flex justify-between items-center bg-surface-container-low">
              <div className="flex items-center gap-2">
                <Building2 className="w-5 h-5 text-secondary" />
                <h3 className="font-bold text-foreground text-sm">토큰증권 안전 이체 (수수료 면제)</h3>
              </div>
              <button
                onClick={() => {
                  setIsTransferModalOpen(false)
                  setToAddress('')
                  setTransferAmount('')
                }}
                className="p-1 rounded-lg hover:bg-surface-container transition-colors text-muted-foreground hover:text-foreground cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Modal Body */}
            <form onSubmit={handleRelayTransfer} className="p-5 flex flex-col gap-4">
              {/* Asset Info Card */}
              <div className="p-3 bg-surface-container-low border border-outline-variant rounded-xl flex justify-between items-center">
                <div>
                  <p className="text-xs text-muted-foreground">양도 종목</p>
                  <p className="text-sm font-bold text-foreground">{transferToken.name} ({transferToken.symbol})</p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-muted-foreground">보유 수량</p>
                  <p className="text-sm font-bold text-secondary font-mono">{transferToken.quantity.toLocaleString()} 주</p>
                </div>
              </div>

              {/* To Address Input */}
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-foreground">수신자 지갑 주소</label>
                <input
                  type="text"
                  required
                  placeholder="0x로 시작하는 수신자 주소 입력..."
                  value={toAddress}
                  onChange={e => setToAddress(e.target.value)}
                  className="w-full px-3 py-2 bg-surface-container-low border border-outline-variant rounded-xl text-sm focus:outline-none focus:border-secondary text-foreground font-mono placeholder:text-muted-foreground placeholder:font-sans"
                />
              </div>

              {/* Amount Input */}
              <div className="flex flex-col gap-1.5">
                <label className="text-xs font-bold text-foreground">양도 수량 (주)</label>
                <div className="relative">
                  <input
                    type="number"
                    required
                    min="1"
                    step="1"
                    placeholder="양도할 주식 수량..."
                    value={transferAmount}
                    onChange={e => setTransferAmount(e.target.value)}
                    className="w-full px-3 py-2 pr-16 bg-surface-container-low border border-outline-variant rounded-xl text-sm focus:outline-none focus:border-secondary text-foreground font-mono placeholder:text-muted-foreground placeholder:font-sans"
                  />
                  <button
                    type="button"
                    onClick={() => setTransferAmount(transferToken.quantity.toString())}
                    className="absolute right-2 top-1.5 px-2 py-1 bg-surface-container hover:bg-surface-container-high rounded text-[10px] font-bold text-secondary border border-outline-variant hover:border-secondary transition-all cursor-pointer"
                  >
                    최대
                  </button>
                </div>
              </div>

              {/* Compliance Warning */}
              <div className="p-3 bg-secondary/5 border border-secondary/10 rounded-xl text-[11px] text-muted-foreground leading-relaxed">
                📢 **수수료 전액 면제 안내**: 본 토큰증권 이체 서비스는 거래 수수료가 전액 면제되며, 지갑 서명 승인 즉시 블록체인 분산원장에 실시간으로 안전하게 기록됩니다.
              </div>

              {/* Action Buttons */}
              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => {
                    setIsTransferModalOpen(false)
                    setToAddress('')
                    setTransferAmount('')
                  }}
                  className="flex-1 py-2.5 bg-surface-container hover:bg-surface-container-high rounded-xl text-sm font-bold text-foreground transition-all cursor-pointer"
                >
                  취소
                </button>
                <button
                  type="submit"
                  disabled={transferLoading}
                  className="flex-1 py-2.5 bg-primary hover:opacity-90 disabled:opacity-50 rounded-xl text-sm font-bold text-primary-foreground transition-all flex items-center justify-center gap-2 cursor-pointer"
                >
                  {transferLoading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      이체 처리 중...
                    </>
                  ) : (
                    "이체 승인"
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
