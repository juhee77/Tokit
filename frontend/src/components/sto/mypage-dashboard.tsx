"use client"

import React, { useState, useEffect, useCallback } from "react"
import { 
  User, 
  Wallet as WalletIcon, 
  ArrowUpRight, 
  ArrowDownRight, 
  Copy, 
  Check, 
  RefreshCw, 
  History, 
  ShieldCheck, 
  ShieldAlert,
  Loader2
} from "lucide-react"
import { toast } from "sonner"
import { fetchApi } from "@/lib/api"
import { cn } from "@/lib/utils"

interface UserResponse {
  id: number
  email: string
  name: string
  walletAddress: string
  kycStatus: boolean
}

interface WalletResponse {
  id: number
  userId: number
  assetSymbol: string
  balance: number
  lockedBalance: number
}

interface OrderResponse {
  id: number
  userId: number
  assetSymbol: string
  orderType: "BUY" | "SELL"
  price: number
  quantity: number
  remainingQuantity: number
  status: "OPEN" | "PARTIAL" | "FILLED" | "CANCELED"
  createdAt: string
}

interface TradeResponse {
  id: number
  buyOrderId: number
  sellOrderId: number
  assetSymbol: string
  price: number
  quantity: number
  createdAt: string
}

interface MyPageResponse {
  user: UserResponse
  wallets: WalletResponse[]
  orders: OrderResponse[]
  trades: TradeResponse[]
}

const statusConfig = {
  OPEN: { label: "대기중", className: "border-warning text-warning bg-warning/10" },
  PARTIAL: { label: "부분체결", className: "border-secondary text-secondary bg-secondary/10" },
  FILLED: { label: "체결완료", className: "border-green-500 text-green-500 bg-green-50" },
  CANCELED: { label: "취소됨", className: "border-outline text-muted-foreground bg-surface-container" },
}

const formatKoreanTextAmount = (amountStr: string | number): string => {
  const cleanStr = typeof amountStr === "number" ? Math.floor(amountStr).toString() : amountStr.replace(/[^0-9]/g, "")
  if (!cleanStr) return ""
  const num = parseInt(cleanStr, 10)
  if (num === 0) return "0원"

  const units = ["", "만", "억", "조", "경"]
  let result: string[] = []
  let temp = num

  let unitIndex = 0
  while (temp > 0) {
    const chunk = temp % 10000
    if (chunk > 0) {
      result.push(`${chunk.toLocaleString()}${units[unitIndex]}`)
    }
    temp = Math.floor(temp / 10000)
    unitIndex++
  }

  return result.reverse().join(" ") + "원"
}

export function MyPageDashboard() {
  const [data, setData] = useState<MyPageResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [userId, setUserId] = useState<number>(1)
  const [copied, setCopied] = useState<boolean>(false)
  const [kycLoading, setKycLoading] = useState<boolean>(false)

  // Transaction state
  const [depositAmount, setDepositAmount] = useState<string>("")
  const [withdrawAmount, setWithdrawAmount] = useState<string>("")
  const [actionLoading, setActionLoading] = useState<"deposit" | "withdraw" | null>(null)

  // Tabs state
  const [activeTab, setActiveTab] = useState<"wallets" | "orders" | "trades">("wallets")

  // Generate simple UUID helper
  const generateUUID = () => {
    if (typeof window !== "undefined" && window.crypto && window.crypto.randomUUID) {
      return window.crypto.randomUUID()
    }
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
      const r = (Math.random() * 16) | 0
      const v = c === "x" ? r : (r & 0x3) | 0x8
      return v.toString(16)
    })
  }

  const loadMyPageData = useCallback(async (id: number) => {
    setLoading(true)
    try {
      const res = await fetchApi<MyPageResponse>(`/api/users/${id}/mypage`)
      setData(res)
    } catch (err: any) {
      console.error(err)
      
      // Auto-signup default testing user if not found
      if (err.message && (
        err.message.includes("not found") || 
        err.message.includes("NOT_FOUND") || 
        err.message.includes("M001") ||
        err.message.includes("User not found")
      )) {
        try {
          toast.info("마이페이지 테스트용 계정을 생성하고 있습니다...")
          const signupRes = await fetchApi<any>("/api/users/signup", {
            method: "POST",
            body: JSON.stringify({
              email: "test-investor@tokit.com",
              name: "김토킷",
              walletAddress: "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
            })
          })
          
          const newId = signupRes.id
          setUserId(newId)
          if (typeof window !== "undefined") {
            localStorage.setItem("tokit_userId", newId.toString())
          }
          
          // Retry fetching
          const retryRes = await fetchApi<MyPageResponse>(`/api/users/${newId}/mypage`)
          setData(retryRes)
          toast.success("테스트용 계정(김토킷)이 생성되었습니다.")
        } catch (signupErr: any) {
          console.error("Auto signup failed", signupErr)
          toast.error("계정 자동 생성 실패: " + signupErr.message)
        }
      } else {
        toast.error("데이터 조회 실패: " + err.message)
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
    setUserId(savedId)
    loadMyPageData(savedId)
  }, [loadMyPageData])

  const handleCopyWalletAddress = () => {
    if (!data?.user.walletAddress) return
    navigator.clipboard.writeText(data.user.walletAddress)
    setCopied(true)
    toast.success("지갑 주소가 클립보드에 복사되었습니다.")
    setTimeout(() => setCopied(false), 2000)
  }

  const handleKycToggle = async () => {
    if (!data) return
    const nextKyc = !data.user.kycStatus
    setKycLoading(true)
    try {
      await fetchApi(`/api/users/${userId}/kyc?kycStatus=${nextKyc}`, {
        method: "PUT"
      })
      toast.success(`KYC 인증 상태가 ${nextKyc ? "인증 완료" : "미인증"} 상태로 변경되었습니다.`)
      loadMyPageData(userId)
    } catch (err: any) {
      console.error(err)
      toast.error("KYC 변경 실패: " + err.message)
    } finally {
      setKycLoading(false)
    }
  }

  const handleDeposit = async (e: React.FormEvent) => {
    e.preventDefault()
    const amountNum = parseFloat(depositAmount.replace(/,/g, ""))
    if (isNaN(amountNum) || amountNum <= 0) {
      toast.error("충전할 금액을 올바르게 입력해 주세요.")
      return
    }

    setActionLoading("deposit")
    try {
      const key = generateUUID()
      await fetchApi("/api/wallets/deposit", {
        method: "POST",
        headers: {
          "X-Idempotency-Key": key
        },
        body: JSON.stringify({
          userId: userId,
          amount: amountNum
        })
      })
      toast.success(`${amountNum.toLocaleString()}원 충전이 완료되었습니다. (멱등성 키: ${key.slice(0, 8)}...)`)
      setDepositAmount("")
      loadMyPageData(userId)
    } catch (err: any) {
      console.error(err)
      toast.error("충전 실패: " + err.message)
    } finally {
      setActionLoading(null)
    }
  }

  const handleWithdraw = async (e: React.FormEvent) => {
    e.preventDefault()
    const amountNum = parseFloat(withdrawAmount.replace(/,/g, ""))
    if (isNaN(amountNum) || amountNum <= 0) {
      toast.error("출금할 금액을 올바르게 입력해 주세요.")
      return
    }

    const krwWallet = data?.wallets.find(w => w.assetSymbol === "KRW")
    if (krwWallet && krwWallet.balance < amountNum) {
      toast.error("출금 가능 잔액이 부족합니다.")
      return
    }

    setActionLoading("withdraw")
    try {
      const key = generateUUID()
      await fetchApi("/api/wallets/withdraw", {
        method: "POST",
        headers: {
          "X-Idempotency-Key": key
        },
        body: JSON.stringify({
          userId: userId,
          amount: amountNum
        })
      })
      toast.success(`${amountNum.toLocaleString()}원 출금 신청이 완료되었습니다. (멱등성 키: ${key.slice(0, 8)}...)`)
      setWithdrawAmount("")
      loadMyPageData(userId)
    } catch (err: any) {
      console.error(err)
      toast.error("출금 실패: " + err.message)
    } finally {
      setActionLoading(null)
    }
  }

  const formatKRW = (value: number) => {
    return `${value.toLocaleString("ko-KR")} 원`
  }

  const formatDateTime = (dateStr: string) => {
    try {
      const d = new Date(dateStr)
      return d.toLocaleString("ko-KR")
    } catch {
      return dateStr
    }
  }

  // Calculate Asset Values
  const krwWallet = data?.wallets.find(w => w.assetSymbol === "KRW")
  const krwBalance = krwWallet ? krwWallet.balance : 0
  const krwLocked = krwWallet ? krwWallet.lockedBalance : 0
  
  // Display loading spinner
  if (loading && !data) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center py-20 text-slate-400 gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-secondary" />
        <p className="text-sm font-semibold">마이페이지 정보 로딩 중...</p>
      </div>
    )
  }

  // Display error state if no data could be loaded
  if (!data) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center py-20 text-slate-400 gap-3">
        <p className="text-sm font-semibold text-destructive">마이페이지 데이터를 불러오지 못했습니다.</p>
        <button 
          onClick={() => loadMyPageData(userId)}
          className="mt-2 px-4 py-2 text-xs font-semibold bg-surface border border-outline-variant rounded hover:border-secondary transition-colors"
        >
          다시 시도
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Page Title & Refresh */}
      <div className="flex justify-between items-center bg-card border border-outline-variant p-4 rounded shadow-sm">
        <div>
          <h2 className="text-headline-md font-bold text-foreground">마이페이지 (My Page)</h2>
          <p className="text-xs text-muted-foreground mt-1">계정 정보 관리 및 예치금 충전/출금을 관리합니다.</p>
        </div>
        <button 
          onClick={() => loadMyPageData(userId)}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border border-outline-variant rounded bg-surface hover:border-secondary hover:text-secondary transition-colors"
          disabled={loading}
        >
          <RefreshCw className={cn("w-3.5 h-3.5", loading && "animate-spin")} />
          새로고침
        </button>
      </div>

      {/* Profile & KYC Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Profile Details Card */}
        <div className="lg:col-span-7 bg-card border border-outline-variant rounded p-6 flex flex-col justify-between shadow-sm relative overflow-hidden">
          <div className="absolute top-0 right-0 w-24 h-24 bg-secondary/5 rounded-full -mr-8 -mt-8 pointer-events-none" />
          <div className="space-y-6">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-full bg-secondary/15 flex items-center justify-center text-secondary">
                <User className="w-6 h-6" />
              </div>
              <div>
                <h3 className="font-bold text-lg text-foreground">{data?.user.name} <span className="text-xs text-muted-foreground font-normal">(고객 ID: {data?.user.id})</span></h3>
                <p className="text-sm text-muted-foreground">{data?.user.email}</p>
              </div>
            </div>

            {/* Wallet Address */}
            <div className="bg-surface-container-low rounded p-3 border border-outline-variant">
              <p className="text-[10px] uppercase font-bold tracking-wider text-muted-foreground mb-1">블록체인 지갑 주소</p>
              <div className="flex justify-between items-center gap-4">
                <code className="text-xs font-mono break-all text-foreground select-all bg-surface px-2 py-1 rounded border border-outline-variant/50">
                  {data?.user.walletAddress}
                </code>
                <button 
                  onClick={handleCopyWalletAddress}
                  className="p-1.5 rounded border border-outline-variant bg-surface hover:border-secondary hover:text-secondary transition-colors flex-shrink-0"
                  title="지갑주소 복사"
                >
                  {copied ? <Check className="w-4 h-4 text-green-500" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>
            </div>
          </div>

          {/* KYC Certification Panel */}
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center border-t border-outline-variant/60 pt-4 mt-6 gap-4">
            <div className="flex items-center gap-2">
              {data?.user.kycStatus ? (
                <>
                  <ShieldCheck className="w-5 h-5 text-green-500" />
                  <div>
                    <p className="text-sm font-semibold text-foreground">신원인증 완료 (KYC Verified)</p>
                    <p className="text-xs text-muted-foreground">토큰증권 청약 및 거래가 가능합니다.</p>
                  </div>
                </>
              ) : (
                <>
                  <ShieldAlert className="w-5 h-5 text-warning" />
                  <div>
                    <p className="text-sm font-semibold text-foreground">신원인증 필요 (KYC Required)</p>
                    <p className="text-xs text-muted-foreground">청약 및 거래를 위해 KYC 인증이 요구됩니다.</p>
                  </div>
                </>
              )}
            </div>

            <button 
              onClick={handleKycToggle}
              className={cn(
                "px-3 py-1.5 text-xs font-bold rounded border transition-colors flex items-center gap-1",
                data?.user.kycStatus
                  ? "border-warning/50 text-warning bg-warning/5 hover:bg-warning/10"
                  : "border-green-600/50 text-green-600 bg-green-50 hover:bg-green-100"
              )}
              disabled={kycLoading}
            >
              {kycLoading && <Loader2 className="w-3 h-3 animate-spin" />}
              {data?.user.kycStatus ? "인증 해제 시뮬레이션" : "KYC 간편 인증하기"}
            </button>
          </div>
        </div>

        {/* Deposit/Withdraw Panel */}
        <div className="lg:col-span-5 bg-card border border-outline-variant rounded p-6 shadow-sm flex flex-col justify-between">
          <div className="flex items-center gap-2 mb-4">
            <WalletIcon className="h-5 w-5 text-secondary" />
            <h3 className="font-semibold text-foreground">예치금 입출금 (원화 KRW)</h3>
          </div>

          <div className="grid grid-cols-1 gap-4">
            {/* Deposit Form */}
            <form onSubmit={handleDeposit} className="space-y-2">
              <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block">예치금 충전 (입금)</label>
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <input 
                    type="text" 
                    placeholder="충전 금액 입력"
                    value={depositAmount}
                    onChange={(e) => setDepositAmount(e.target.value.replace(/[^0-9]/g, "").replace(/\B(?=(\d{3})+(?!\d))/g, ","))}
                    className="w-full px-3 py-2 pr-8 bg-surface border border-outline-variant rounded text-sm text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20 font-mono"
                    disabled={actionLoading !== null}
                  />
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">원</span>
                </div>
                <button 
                  type="submit"
                  className="px-4 py-2 text-xs font-semibold text-white bg-gain hover:bg-gain/95 rounded shadow-sm transition-colors flex-shrink-0 flex items-center justify-center gap-1 disabled:opacity-50"
                  disabled={actionLoading !== null || !depositAmount}
                >
                  {actionLoading === "deposit" && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  충전
                </button>
              </div>
              {depositAmount && (
                <p className="text-xs text-secondary font-mono mt-1 pl-1">
                  입금 금액: {formatKoreanTextAmount(depositAmount)}
                </p>
              )}
            </form>

            <hr className="border-outline-variant/60" />

            {/* Withdraw Form */}
            <form onSubmit={handleWithdraw} className="space-y-2">
              <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block">예치금 출금 (출금)</label>
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <input 
                    type="text" 
                    placeholder="출금 금액 입력"
                    value={withdrawAmount}
                    onChange={(e) => setWithdrawAmount(e.target.value.replace(/[^0-9]/g, "").replace(/\B(?=(\d{3})+(?!\d))/g, ","))}
                    className="w-full px-3 py-2 pr-8 bg-surface border border-outline-variant rounded text-sm text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20 font-mono"
                    disabled={actionLoading !== null}
                  />
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">원</span>
                </div>
                <button 
                  type="submit"
                  className="px-4 py-2 text-xs font-semibold text-white bg-loss hover:bg-loss/95 rounded shadow-sm transition-colors flex-shrink-0 flex items-center justify-center gap-1 disabled:opacity-50"
                  disabled={actionLoading !== null || !withdrawAmount}
                >
                  {actionLoading === "withdraw" && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                  출금
                </button>
              </div>
              {withdrawAmount && (
                <p className="text-xs text-secondary font-mono mt-1 pl-1">
                  출금 금액: {formatKoreanTextAmount(withdrawAmount)}
                </p>
              )}
            </form>
          </div>
        </div>
      </div>

      {/* Wallets, Orders, Trades Unified Tabs Panel */}
      <div className="bg-card border border-outline-variant rounded shadow-sm overflow-hidden">
        {/* Navigation Tabs */}
        <div className="flex border-b border-outline-variant bg-surface">
          <button 
            onClick={() => setActiveTab("wallets")}
            className={cn(
              "flex-1 px-4 py-3 text-sm font-semibold border-b-2 transition-all flex items-center justify-center gap-2",
              activeTab === "wallets"
                ? "border-secondary text-secondary bg-surface-container-low"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            <WalletIcon className="w-4 h-4" />
            자산 및 보유 토큰
          </button>
          <button 
            onClick={() => setActiveTab("orders")}
            className={cn(
              "flex-1 px-4 py-3 text-sm font-semibold border-b-2 transition-all flex items-center justify-center gap-2",
              activeTab === "orders"
                ? "border-secondary text-secondary bg-surface-container-low"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            <History className="w-4 h-4" />
            주문 내역 ({data?.orders.length || 0})
          </button>
          <button 
            onClick={() => setActiveTab("trades")}
            className={cn(
              "flex-1 px-4 py-3 text-sm font-semibold border-b-2 transition-all flex items-center justify-center gap-2",
              activeTab === "trades"
                ? "border-secondary text-secondary bg-surface-container-low"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            <History className="w-4 h-4" />
            체결 내역 ({data?.trades.length || 0})
          </button>
        </div>

        {/* Tab Contents: 1. Wallets */}
        {activeTab === "wallets" && (
          <div className="p-4 md:p-6 space-y-6">
            {/* Wallet Balances Summary Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="bg-surface-container rounded p-4 border border-outline-variant">
                <span className="text-xs text-muted-foreground font-semibold">사용 가능 예치금</span>
                <p className="text-xl font-bold font-mono text-secondary mt-1">{formatKRW(krwBalance)}</p>
                <p className="text-xs text-muted-foreground mt-1 font-mono">{formatKoreanTextAmount(krwBalance)}</p>
              </div>
              <div className="bg-surface-container rounded p-4 border border-outline-variant">
                <span className="text-xs text-muted-foreground font-semibold">주문 거래용 홀딩 (락)</span>
                <p className="text-xl font-bold font-mono text-warning mt-1">{formatKRW(krwLocked)}</p>
                <p className="text-xs text-muted-foreground mt-1 font-mono">{formatKoreanTextAmount(krwLocked)}</p>
              </div>
              <div className="bg-surface-container rounded p-4 border border-outline-variant">
                <span className="text-xs text-muted-foreground font-semibold">총 자산 평가액</span>
                <p className="text-xl font-bold font-mono text-foreground mt-1">{formatKRW(krwBalance + krwLocked)}</p>
                <p className="text-xs text-muted-foreground mt-1 font-mono">{formatKoreanTextAmount(krwBalance + krwLocked)}</p>
              </div>
            </div>

            {/* Token Wallets List */}
            <div className="border border-outline-variant rounded overflow-hidden">
              <div className="bg-surface-container-low px-4 py-3 border-b border-outline-variant">
                <h4 className="font-semibold text-sm text-foreground">보유 토큰증권 목록</h4>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[500px]">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface text-label-caps text-muted-foreground text-xs">
                      <th className="py-2.5 px-4 font-semibold">자산 심볼</th>
                      <th className="py-2.5 px-4 font-semibold text-right">보유 수량</th>
                      <th className="py-2.5 px-4 font-semibold text-right">거래 대기 중 홀딩</th>
                      <th className="py-2.5 px-4 font-semibold text-right">총 수량</th>
                    </tr>
                  </thead>
                  <tbody className="text-sm divide-y divide-outline-variant/60">
                    {data?.wallets.filter(w => w.assetSymbol !== "KRW").length === 0 ? (
                      <tr>
                        <td colSpan={4} className="py-8 text-center text-muted-foreground">보유 중인 토큰증권이 없습니다.</td>
                      </tr>
                    ) : (
                      data?.wallets.filter(w => w.assetSymbol !== "KRW").map(wallet => (
                        <tr key={wallet.id} className="hover:bg-surface-container-low transition-colors">
                          <td className="py-3 px-4 font-semibold text-foreground">{wallet.assetSymbol}</td>
                          <td className="py-3 px-4 text-right font-mono text-foreground">{wallet.balance.toLocaleString()}</td>
                          <td className="py-3 px-4 text-right font-mono text-warning">{wallet.lockedBalance.toLocaleString()}</td>
                          <td className="py-3 px-4 text-right font-mono font-semibold text-foreground">
                            {(wallet.balance + wallet.lockedBalance).toLocaleString()}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Tab Contents: 2. Orders */}
        {activeTab === "orders" && (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse min-w-[700px]">
              <thead>
                <tr className="border-b border-outline-variant bg-surface text-label-caps text-muted-foreground text-xs">
                  <th className="py-3 px-4 font-semibold">주문 ID</th>
                  <th className="py-3 px-4 font-semibold">종목 심볼</th>
                  <th className="py-3 px-4 font-semibold">구분</th>
                  <th className="py-3 px-4 font-semibold text-right">주문 가격</th>
                  <th className="py-3 px-4 font-semibold text-right">체결 / 총 수량</th>
                  <th className="py-3 px-4 font-semibold text-center">주문 상태</th>
                  <th className="py-3 px-4 font-semibold text-right">주문 일시</th>
                </tr>
              </thead>
              <tbody className="text-sm divide-y divide-outline-variant/60">
                {data?.orders.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-8 text-center text-muted-foreground">주문 내역이 없습니다.</td>
                  </tr>
                ) : (
                  data?.orders.map(order => {
                    const filledQty = order.quantity - order.remainingQuantity
                    return (
                      <tr key={order.id} className="hover:bg-surface-container-low transition-colors">
                        <td className="py-3 px-4 font-mono text-xs text-muted-foreground">{order.id}</td>
                        <td className="py-3 px-4 font-semibold text-foreground">{order.assetSymbol}</td>
                        <td className="py-3 px-4">
                          <span className={cn(
                            "px-2 py-0.5 rounded text-xs font-bold",
                            order.orderType === "BUY" ? "bg-gain/10 text-gain" : "bg-loss/10 text-loss"
                          )}>
                            {order.orderType === "BUY" ? "매수" : "매도"}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right font-mono text-foreground">{order.price.toLocaleString()}원</td>
                        <td className="py-3 px-4 text-right font-mono">
                          <span className="text-foreground">{filledQty.toLocaleString()}</span>
                          <span className="text-muted-foreground"> / {order.quantity.toLocaleString()}</span>
                        </td>
                        <td className="py-3 px-4 text-center">
                          <span className={cn(
                            "px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border inline-block",
                            statusConfig[order.status]?.className || ""
                          )}>
                            {statusConfig[order.status]?.label || order.status}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-right text-xs text-muted-foreground">{formatDateTime(order.createdAt)}</td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* Tab Contents: 3. Trades */}
        {activeTab === "trades" && (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse min-w-[600px]">
              <thead>
                <tr className="border-b border-outline-variant bg-surface text-label-caps text-muted-foreground text-xs">
                  <th className="py-3 px-4 font-semibold">체결 ID</th>
                  <th className="py-3 px-4 font-semibold">종목 심볼</th>
                  <th className="py-3 px-4 font-semibold text-right">체결 가격</th>
                  <th className="py-3 px-4 font-semibold text-right">체결 수량</th>
                  <th className="py-3 px-4 font-semibold text-right">총 금액</th>
                  <th className="py-3 px-4 font-semibold text-right">체결 일시</th>
                </tr>
              </thead>
              <tbody className="text-sm divide-y divide-outline-variant/60">
                {data?.trades.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-muted-foreground">체결 거래 내역이 없습니다.</td>
                  </tr>
                ) : (
                  data?.trades.map(trade => (
                    <tr key={trade.id} className="hover:bg-surface-container-low transition-colors">
                      <td className="py-3 px-4 font-mono text-xs text-muted-foreground">{trade.id}</td>
                      <td className="py-3 px-4 font-semibold text-foreground">{trade.assetSymbol}</td>
                      <td className="py-3 px-4 text-right font-mono text-foreground">{trade.price.toLocaleString()}원</td>
                      <td className="py-3 px-4 text-right font-mono text-foreground">{trade.quantity.toLocaleString()}</td>
                      <td className="py-3 px-4 text-right font-mono font-semibold text-foreground">
                        {(trade.price * trade.quantity).toLocaleString()}원
                      </td>
                      <td className="py-3 px-4 text-right text-xs text-muted-foreground">{formatDateTime(trade.createdAt)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
