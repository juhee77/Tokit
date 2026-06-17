"use client"

import { useState, useEffect, useCallback } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Input } from "@/components/ui/input"
import { Loader2, Clock, Users, TrendingUp, AlertCircle, ArrowLeft } from "lucide-react"
import { cn } from "@/lib/utils"
import { fetchApi } from "@/lib/api"
import { toast } from "sonner"

interface AssetResponse {
  id: number
  symbol: string
  name: string
  contractAddress: string
  totalSupply: number
  status: string
  issuePrice: number
  currentAmount: number
  totalInvestors: number
}

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

interface MyPageResponse {
  user: UserResponse
  wallets: WalletResponse[]
}

interface OfferingDashboardProps {
  symbol: string
  onBack?: () => void
}

export function OfferingDashboard({ symbol, onBack }: OfferingDashboardProps) {
  const [asset, setAsset] = useState<AssetResponse | null>(null)
  const [user, setUser] = useState<UserResponse | null>(null)
  const [krwBalance, setKrwBalance] = useState<number>(0)
  const [loading, setLoading] = useState(true)
  const [timeLeft, setTimeLeft] = useState({ days: 3, hours: 4, minutes: 32, seconds: 15 })
  const [investmentAmount, setInvestmentAmount] = useState("")
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [userId, setUserId] = useState<number>(1)

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

  const loadData = useCallback(async (currentUserId: number) => {
    try {
      // 1. Fetch asset details
      const assetData = await fetchApi<AssetResponse>(`/api/assets/${symbol}`)
      setAsset(assetData)

      // 2. Fetch user & wallet details
      const mypageData = await fetchApi<MyPageResponse>(`/api/users/${currentUserId}/mypage`)
      setUser(mypageData.user)
      
      const krwWallet = mypageData.wallets.find(w => w.assetSymbol === null || w.assetSymbol === "KRW" || !w.assetSymbol)
      setKrwBalance(krwWallet ? krwWallet.balance : 0)
    } catch (err: any) {
      console.error(err)
      toast.error("데이터를 불러오는 데 실패했습니다: " + err.message)
    } finally {
      setLoading(false)
    }
  }, [symbol])

  useEffect(() => {
    let savedId = 1
    if (typeof window !== "undefined") {
      const raw = localStorage.getItem("tokit_userId")
      if (raw) savedId = parseInt(raw, 10)
    }
    setUserId(savedId)
    loadData(savedId)
  }, [loadData])

  const handleSubscribe = useCallback(async () => {
    const amount = parseInt(investmentAmount.replace(/,/g, ""))
    if (isNaN(amount) || amount <= 0) {
      toast.error("투자할 금액을 올바르게 입력해 주세요.")
      return
    }

    if (!user?.kycStatus) {
      toast.error("KYC 신원인증이 완료되지 않은 사용자입니다. 마이페이지에서 먼저 KYC 인증을 완료해 주세요.")
      return
    }

    const minInvestment = 100000 // 10만원 최소 투자
    if (amount < minInvestment) {
      toast.error(`최소 투자 금액은 ${minInvestment.toLocaleString()}원입니다.`)
      return
    }

    if (amount > krwBalance) {
      toast.error("예치금 잔액이 부족합니다.")
      return
    }

    setIsSubmitting(true)
    try {
      const key = generateUUID()
      await fetchApi(`/api/assets/${symbol}/subscribe`, {
        method: "POST",
        headers: {
          "X-Idempotency-Key": key
        },
        body: JSON.stringify({
          userId: userId,
          amount: amount
        })
      })
      toast.success(`${amount.toLocaleString()}원 청약 신청이 성공적으로 완료되었습니다.`)
      setInvestmentAmount("")
      loadData(userId)
    } catch (err: any) {
      console.error(err)
      toast.error("청약 실패: " + err.message)
    } finally {
      setIsSubmitting(false)
    }
  }, [investmentAmount, user, krwBalance, symbol, userId, loadData])

  const formatKRW = (value: number) => {
    if (value >= 100000000) {
      return `${(value / 100000000).toFixed(1)}억원`
    }
    if (value >= 10000) {
      return `${(value / 10000).toFixed(0)}만원`
    }
    return `${value.toLocaleString()}원`
  }

  const formatNumber = (value: string) => {
    const num = value.replace(/[^\d]/g, "")
    return num ? parseInt(num).toLocaleString() : ""
  }

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center p-20 text-muted-foreground">
        Loading Subscription Dashboard...
      </div>
    )
  }

  if (!asset) {
    return (
      <div className="p-8 text-center text-destructive">
        자산 정보를 불러올 수 없습니다.
      </div>
    )
  }

  const targetAmount = asset.totalSupply * asset.issuePrice
  const currentAmount = asset.currentAmount || 0
  const progressPercent = (currentAmount / targetAmount) * 100

  const investAmount = parseInt(investmentAmount.replace(/,/g, "")) || 0
  const tokenCount = Math.floor(investAmount / asset.issuePrice)
  const insufficientBalance = investAmount > krwBalance

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-3 mb-2">
            {onBack && (
              <button onClick={onBack} className="p-1 hover:bg-surface-container rounded mr-1 cursor-pointer">
                <ArrowLeft className="h-5 w-5 text-foreground" />
              </button>
            )}
            <h1 className="text-2xl font-bold text-foreground">{asset.name}</h1>
            <Badge className="bg-primary/20 text-primary border-primary/30">청약 진행중</Badge>
          </div>
          <p className="text-muted-foreground font-mono text-sm">{asset.symbol} • {asset.contractAddress}</p>
        </div>
        <div className="text-right">
          <p className="text-sm text-muted-foreground">토큰 가격</p>
          <p className="text-xl font-bold text-foreground">{asset.issuePrice.toLocaleString()}원</p>
        </div>
      </div>

      {/* Progress Section */}
      <Card className="bg-card border-border">
        <CardHeader className="pb-2">
          <CardTitle className="text-base font-medium flex items-center justify-between">
            <span>청약 달성률</span>
            <span className={cn(
              "text-2xl font-bold",
              progressPercent >= 100 ? "text-gain" : "text-primary"
            )}>
              {progressPercent.toFixed(1)}%
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="relative">
            <Progress 
              value={Math.min(progressPercent, 100)} 
              className="h-4 bg-secondary"
            />
          </div>

          <div className="flex justify-between text-sm">
            <div>
              <p className="text-muted-foreground">현재 청약액</p>
              <p className="text-lg font-semibold text-foreground">{formatKRW(currentAmount)}</p>
            </div>
            <div className="text-right">
              <p className="text-muted-foreground">목표 금액</p>
              <p className="text-lg font-semibold text-foreground">{formatKRW(targetAmount)}</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Timer & Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Countdown Timer */}
        <Card className="bg-card border-border">
          <CardContent className="pt-6">
            <div className="flex items-center gap-2 mb-4">
              <Clock className="h-4 w-4 text-warning" />
              <span className="text-sm text-muted-foreground">청약 마감까지</span>
            </div>
            <div className="grid grid-cols-4 gap-2 text-center font-mono">
              {[
                { value: timeLeft.days, label: "일" },
                { value: timeLeft.hours, label: "시" },
                { value: timeLeft.minutes, label: "분" },
                { value: timeLeft.seconds, label: "초" },
              ].map((item) => (
                <div key={item.label} className="bg-secondary rounded-lg p-2">
                  <p className="text-xl font-bold text-foreground">
                    {item.value.toString().padStart(2, "0")}
                  </p>
                  <p className="text-xs text-muted-foreground">{item.label}</p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Stats */}
        <Card className="bg-card border-border">
          <CardContent className="pt-6 space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Users className="h-4 w-4 text-primary" />
                <span className="text-sm text-muted-foreground">참여 투자자</span>
              </div>
              <span className="font-semibold text-foreground">{asset.totalInvestors.toLocaleString()}명</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-gain" />
                <span className="text-sm text-muted-foreground">최소 투자금</span>
              </div>
              <span className="font-semibold text-foreground">100,000원</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <AlertCircle className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm text-muted-foreground">총 발행량</span>
              </div>
              <span className="font-semibold text-foreground">{asset.totalSupply.toLocaleString()} STO</span>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Subscription Form */}
      <Card className="bg-card border-border">
        <CardHeader>
          <CardTitle className="text-base font-medium">청약 신청</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <div className="flex justify-between text-sm mb-2">
              <span className="text-muted-foreground">투자 금액</span>
              <span className="text-muted-foreground font-mono">
                보유 예치금: <span className="text-foreground font-medium">{formatKRW(krwBalance)}</span>
              </span>
            </div>
            <div className="relative">
              <Input
                type="text"
                placeholder="투자 금액 입력"
                value={investmentAmount}
                onChange={(e) => setInvestmentAmount(formatNumber(e.target.value))}
                className="pr-12 bg-input border-border text-foreground"
                disabled={isSubmitting}
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">원</span>
            </div>
            {investAmount > 0 && (
              <p className="text-sm text-muted-foreground mt-2 font-mono">
                예상 토큰 수량: <span className="text-primary font-medium">{tokenCount.toLocaleString()} {asset.symbol}</span>
              </p>
            )}
          </div>

          {insufficientBalance ? (
            <Button 
              className="w-full bg-warning text-warning-foreground hover:bg-warning/90"
              onClick={() => {
                window.location.href = "/mypage"
              }}
            >
              예치금 충전하러 가기
            </Button>
          ) : (
            <Button
              className="w-full bg-primary text-primary-foreground hover:bg-primary/90"
              disabled={isSubmitting || investAmount < 100000}
              onClick={handleSubscribe}
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  주문 전송 중...
                </>
              ) : (
                "청약하기"
              )}
            </Button>
          )}

          {investAmount > 0 && investAmount < 100000 && (
            <p className="text-sm text-destructive text-center">
              최소 투자금액은 100,000원입니다
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
