"use client"

import { useState, useEffect, useCallback } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Input } from "@/components/ui/input"
import { Loader2, Clock, Users, TrendingUp, AlertCircle } from "lucide-react"
import { cn } from "@/lib/utils"

interface OfferingData {
  id: string
  name: string
  symbol: string
  targetAmount: number
  currentAmount: number
  minInvestment: number
  maxInvestment: number
  tokenPrice: number
  endTime: Date
  totalInvestors: number
  status: "upcoming" | "active" | "completed" | "oversubscribed"
}

interface OfferingDashboardProps {
  offering?: OfferingData
  userBalance?: number
}

// Simulated real-time data
const mockOffering: OfferingData = {
  id: "1",
  name: "서울 강남 프라임 오피스 빌딩",
  symbol: "GNPM",
  targetAmount: 50000000000,
  currentAmount: 35750000000,
  minInvestment: 100000,
  maxInvestment: 10000000,
  tokenPrice: 10000,
  endTime: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000 + 4 * 60 * 60 * 1000 + 32 * 60 * 1000 + 15 * 1000),
  totalInvestors: 1847,
  status: "active",
}

export function OfferingDashboard({ 
  offering = mockOffering, 
  userBalance = 5000000 
}: OfferingDashboardProps) {
  const [data, setData] = useState(offering)
  const [timeLeft, setTimeLeft] = useState({ days: 0, hours: 0, minutes: 0, seconds: 0 })
  const [investmentAmount, setInvestmentAmount] = useState("")
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [balance] = useState(userBalance)

  const progressPercent = (data.currentAmount / data.targetAmount) * 100

  // Calculate time remaining
  useEffect(() => {
    const calculateTimeLeft = () => {
      const difference = data.endTime.getTime() - Date.now()
      if (difference > 0) {
        setTimeLeft({
          days: Math.floor(difference / (1000 * 60 * 60 * 24)),
          hours: Math.floor((difference / (1000 * 60 * 60)) % 24),
          minutes: Math.floor((difference / 1000 / 60) % 60),
          seconds: Math.floor((difference / 1000) % 60),
        })
      }
    }

    calculateTimeLeft()
    const timer = setInterval(calculateTimeLeft, 1000)
    return () => clearInterval(timer)
  }, [data.endTime])

  // Simulate real-time WebSocket updates
  useEffect(() => {
    const interval = setInterval(() => {
      setData(prev => ({
        ...prev,
        currentAmount: Math.min(
          prev.currentAmount + Math.floor(Math.random() * 50000000),
          prev.targetAmount * 1.2
        ),
        totalInvestors: prev.totalInvestors + Math.floor(Math.random() * 3),
      }))
    }, 3000)

    return () => clearInterval(interval)
  }, [])

  const handleSubscribe = useCallback(async () => {
    const amount = parseInt(investmentAmount.replace(/,/g, ""))
    if (isNaN(amount) || amount < data.minInvestment) return

    setIsSubmitting(true)
    // Simulate API call
    await new Promise(resolve => setTimeout(resolve, 2000))
    setIsSubmitting(false)
    setInvestmentAmount("")
  }, [investmentAmount, data.minInvestment])

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

  const investAmount = parseInt(investmentAmount.replace(/,/g, "")) || 0
  const tokenCount = Math.floor(investAmount / data.tokenPrice)
  const insufficientBalance = investAmount > balance

  const getStatusBadge = () => {
    if (progressPercent >= 100) {
      return <Badge className="bg-gain/20 text-gain border-gain/30">초과 달성</Badge>
    }
    if (data.status === "active") {
      return <Badge className="bg-primary/20 text-primary border-primary/30">청약 진행중</Badge>
    }
    return <Badge variant="secondary">대기중</Badge>
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-3 mb-2">
            <h1 className="text-2xl font-bold text-foreground">{data.name}</h1>
            {getStatusBadge()}
          </div>
          <p className="text-muted-foreground font-mono text-sm">{data.symbol}</p>
        </div>
        <div className="text-right">
          <p className="text-sm text-muted-foreground">토큰 가격</p>
          <p className="text-xl font-bold text-foreground">{data.tokenPrice.toLocaleString()}원</p>
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
          {/* Animated Progress Bar */}
          <div className="relative">
            <Progress 
              value={Math.min(progressPercent, 100)} 
              className="h-4 bg-secondary"
            />
            {progressPercent > 100 && (
              <div 
                className="absolute top-0 left-0 h-4 bg-gain/30 rounded-full transition-all duration-500"
                style={{ width: `${Math.min((progressPercent / 120) * 100, 100)}%` }}
              />
            )}
            {/* Milestone markers */}
            <div className="absolute top-0 left-1/2 h-4 w-0.5 bg-muted-foreground/30" />
            <div className="absolute top-0 left-full -translate-x-0.5 h-4 w-0.5 bg-muted-foreground/50" />
          </div>

          <div className="flex justify-between text-sm">
            <div>
              <p className="text-muted-foreground">현재 청약액</p>
              <p className="text-lg font-semibold text-foreground">{formatKRW(data.currentAmount)}</p>
            </div>
            <div className="text-right">
              <p className="text-muted-foreground">목표 금액</p>
              <p className="text-lg font-semibold text-foreground">{formatKRW(data.targetAmount)}</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Timer & Stats */}
      <div className="grid grid-cols-2 gap-4">
        {/* Countdown Timer */}
        <Card className="bg-card border-border">
          <CardContent className="pt-6">
            <div className="flex items-center gap-2 mb-4">
              <Clock className="h-4 w-4 text-warning" />
              <span className="text-sm text-muted-foreground">청약 마감까지</span>
            </div>
            <div className="grid grid-cols-4 gap-2 text-center">
              {[
                { value: timeLeft.days, label: "일" },
                { value: timeLeft.hours, label: "시" },
                { value: timeLeft.minutes, label: "분" },
                { value: timeLeft.seconds, label: "초" },
              ].map((item) => (
                <div key={item.label} className="bg-secondary rounded-lg p-2">
                  <p className="text-xl font-mono font-bold text-foreground">
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
              <span className="font-semibold text-foreground">{data.totalInvestors.toLocaleString()}명</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-gain" />
                <span className="text-sm text-muted-foreground">최소 투자금</span>
              </div>
              <span className="font-semibold text-foreground">{formatKRW(data.minInvestment)}</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <AlertCircle className="h-4 w-4 text-muted-foreground" />
                <span className="text-sm text-muted-foreground">최대 투자금</span>
              </div>
              <span className="font-semibold text-foreground">{formatKRW(data.maxInvestment)}</span>
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
              <span className="text-muted-foreground">
                보유 예치금: <span className="text-foreground font-medium">{formatKRW(balance)}</span>
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
              <p className="text-sm text-muted-foreground mt-2">
                예상 토큰 수량: <span className="text-primary font-medium">{tokenCount.toLocaleString()} {data.symbol}</span>
              </p>
            )}
          </div>

          {insufficientBalance ? (
            <Button 
              className="w-full bg-warning text-warning-foreground hover:bg-warning/90"
              onClick={() => {/* Navigate to deposit */}}
            >
              예치금 충전하기
            </Button>
          ) : (
            <Button
              className="w-full bg-primary text-primary-foreground hover:bg-primary/90"
              disabled={isSubmitting || investAmount < data.minInvestment}
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

          {investAmount > 0 && investAmount < data.minInvestment && (
            <p className="text-sm text-destructive text-center">
              최소 투자금액은 {formatKRW(data.minInvestment)}입니다
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
