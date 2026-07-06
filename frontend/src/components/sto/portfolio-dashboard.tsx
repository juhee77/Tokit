'use client'

import { useState, useEffect, useMemo } from 'react'
import Link from 'next/link'
import { TrendingUp, ArrowRight, Building2, Factory, Leaf, Briefcase, RefreshCw, Loader2, Send, ShieldCheck } from 'lucide-react'
import { cn } from '@/lib/utils'
import { fetchApi } from '@/lib/api'
import { toast } from 'sonner'

interface RangeConfig {
  mockValue: number
  change: number
  changePeriod: string
  trendPath: string
  trendFillPath: string
  labels: string[]
}

const rangeConfigs: Record<string, RangeConfig> = {
  '1D': {
    mockValue: 12450890000, // ₩12,450,890,000 (124.5억 원)
    change: 0.35,
    changePeriod: '24시간',
    trendPath: 'M0,120 Q100,130 200,105 T400,115 T600,85 T800,60',
    trendFillPath: 'M0,120 Q100,130 200,105 T400,115 T600,85 T800,60 L800,160 L0,160 Z',
    labels: ['09:00', '12:00', '15:00', '18:00', '21:00']
  },
  '30D': {
    mockValue: 12450890000,
    change: 4.2,
    changePeriod: '30일',
    trendPath: 'M0,140 Q100,130 200,90 T400,100 T600,40 T800,20',
    trendFillPath: 'M0,140 Q100,130 200,90 T400,100 T600,40 T800,20 L800,160 L0,160 Z',
    labels: ['6월 1일', '6월 8일', '6월 15일', '6월 22일', '6월 29일']
  },
  'YTD': {
    mockValue: 12450890000,
    change: 12.8,
    changePeriod: '연초 대비 (YTD)',
    trendPath: 'M0,150 Q100,110 300,80 T600,50 T800,15',
    trendFillPath: 'M0,150 Q100,110 300,80 T600,50 T800,15 L800,160 L0,160 Z',
    labels: ['1월', '2월', '3월', '4월', '5월', '6월']
  },
  'ALL': {
    mockValue: 12450890000,
    change: 45.2,
    changePeriod: '누적 수익률',
    trendPath: 'M0,158 Q100,140 300,100 T600,30 T800,5',
    trendFillPath: 'M0,158 Q100,140 300,100 T600,30 T800,5 L800,160 L0,160 Z',
    labels: ['2024년', '2025년', '2026년']
  }
}

const timeRanges = ['1D', '30D', 'YTD', 'ALL']

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: 'KRW',
    minimumFractionDigits: 0,
  }).format(value)
}

function getAssetIcon(type: string) {
  switch (type) {
    case 'Commercial Real Estate':
    case 'Real Estate':
    case '부동산 (Real Estate)':
    case '상업용 빌딩 (CRE)':
      return Building2
    case 'Infrastructure':
    case '친환경 에너지 (Eco)':
      return Leaf
    case 'Venture Capital':
    case '기술 벤처 (Tech VC)':
      return Factory
    default:
      return Briefcase
  }
}

export function PortfolioDashboard() {
  const [selectedRange, setSelectedRange] = useState('30D')
  const [realWallets, setRealWallets] = useState<any[]>([])
  const [realUser, setRealUser] = useState<any>(null)
  const [loading, setLoading] = useState(true)

  // Gasless transfer form states
  const [transferAsset, setTransferAsset] = useState('')
  const [transferTo, setTransferTo] = useState('')
  const [transferAmount, setTransferAmount] = useState('')
  const [transferLoading, setTransferLoading] = useState(false)

  // Dividend simulator state
  const [simulatedTotalBudget, setSimulatedTotalBudget] = useState('')

  const dividendSimulations = useMemo(() => {
    const budget = parseFloat(simulatedTotalBudget) || 0
    if (budget <= 0) return []

    return realWallets
      .filter(w => w.assetSymbol && w.assetSymbol !== "KRW" && w.balance > 0)
      .map(wallet => {
        // total supply mapping from asset or default fallback
        const supply = wallet.assetTotalSupply || 100000 
        const ratio = (wallet.balance / supply) * 100
        const expectedPayout = budget * (wallet.balance / supply)
        return {
          symbol: wallet.assetSymbol,
          name: wallet.assetName || wallet.assetSymbol,
          balance: wallet.balance,
          ratio: ratio.toFixed(4),
          payout: expectedPayout
        }
      })
  }, [simulatedTotalBudget, realWallets])

  const loadRealData = async () => {
    setLoading(true)
    let savedId = 1
    if (typeof window !== "undefined") {
      const raw = localStorage.getItem("tokit_userId")
      if (raw) savedId = parseInt(raw, 10)
    }
    try {
      const res = await fetchApi<any>(`/api/users/${savedId}/mypage`)
      setRealUser(res.user)
      setRealWallets(res.wallets || [])
    } catch (e) {
      console.error("Failed to load real portfolio data:", e)
    } finally {
      setLoading(false)
    }
  }

  const handleGaslessTransfer = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!transferAsset || !transferTo || !transferAmount) {
      toast.error("모든 이체 정보를 채워주세요.")
      return
    }

    if (typeof window === "undefined" || !(window as any).ethereum) {
      toast.error("MetaMask 지갑이 감지되지 않았습니다. 브라우저 확장을 설치해 주세요.")
      return
    }

    setTransferLoading(true)
    try {
      const accounts = await (window as any).ethereum.request({ method: 'eth_requestAccounts' })
      const fromAddress = accounts[0]

      const nonceRes = await fetchApi<any>(`/api/relayer/nonce/${fromAddress}`)
      const nonce = nonceRes.nonce

      const amountStr = parseFloat(transferAmount).toString()
      const plainMessage = `${fromAddress.toLowerCase()}:${transferTo.toLowerCase()}:${transferAsset}:${amountStr}:${nonce}`

      const signature = await (window as any).ethereum.request({
        method: 'personal_sign',
        params: [plainMessage, fromAddress]
      })

      const response = await fetch(`http://localhost:8080/api/relayer/transfer`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          fromAddress,
          toAddress: transferTo,
          assetSymbol: transferAsset,
          amount: parseFloat(transferAmount),
          nonce,
          signature
        })
      })

      const json = await response.json()
      if (!response.ok) {
        throw new Error(json.message || "Relayer transfer failed")
      }

      toast.success("가스비 대납 이체가 완료되었습니다! (Gas paid by Relayer)")
      setTransferTo('')
      setTransferAmount('')
      loadRealData()
    } catch (err: any) {
      console.error("Gasless transfer failed:", err)
      toast.error("이체 실패: " + err.message)
    } finally {
      setTransferLoading(false)
    }
  }

  useEffect(() => {
    loadRealData()
  }, [])

  // 1. Calculate Real Balances
  const krwWallet = realWallets.find(w => w.assetSymbol === "KRW" || !w.assetSymbol || w.assetSymbol === null)
  const krwBal = krwWallet ? krwWallet.balance + krwWallet.lockedBalance : 0
  
  const hdytWallet = realWallets.find(w => w.assetSymbol === "HDYT")
  const hdytQty = hdytWallet ? hdytWallet.balance + hdytWallet.lockedBalance : 0
  const hdytVal = hdytQty * 5000 // ₩5,000 per token
  
  const gnpmWallet = realWallets.find(w => w.assetSymbol === "GNPM")
  const gnpmQty = gnpmWallet ? gnpmWallet.balance + gnpmWallet.lockedBalance : 0
  const gnpmVal = gnpmQty * 10000 // ₩10,000 per token

  const realTotalValue = krwBal + hdytVal + gnpmVal

  // Get current active range configs
  const currentConfig = rangeConfigs[selectedRange]

  // Render correct total value
  const totalValue = realTotalValue > 0 ? realTotalValue : currentConfig.mockValue

  // 2. Generate holdings list dynamically based on database wallets
  const holdings = useMemo(() => {
    const list = []
    
    // real cash holding
    if (krwBal > 0) {
      list.push({
        id: "cash",
        name: "원화 예치금 (Cash)",
        type: "원화 예치금",
        symbol: "KRW",
        holdings: krwBal.toLocaleString(),
        price: "₩1",
        priceChange: 0,
        totalValue: formatCurrency(krwBal),
        status: "Active"
      })
    }
    
    // real HDYT holding
    if (hdytQty > 0) {
      list.push({
        id: "hdyt",
        name: "홍대 청년주택 제1호",
        type: "부동산 (Real Estate)",
        symbol: "HDYT",
        holdings: hdytQty.toLocaleString(),
        price: "₩5,000",
        priceChange: 1.2,
        totalValue: formatCurrency(hdytVal),
        status: "Active"
      })
    }

    // real GNPM holding
    if (gnpmQty > 0) {
      list.push({
        id: "gnpm",
        name: "서울 강남 프라임 오피스 빌딩",
        type: "Commercial Real Estate",
        symbol: "GNPM",
        holdings: gnpmQty.toLocaleString(),
        price: "₩10,000",
        priceChange: 0,
        totalValue: formatCurrency(gnpmVal),
        status: "Active"
      })
    }

    // Fallback to beautiful mock portfolio if they don't have any real balances yet
    if (list.length === 0) {
      return [
        { id: 1, name: 'Hudson Yards Tower C', type: 'Commercial Real Estate', symbol: 'HYT-C', holdings: '12,500', price: '₩140,000', priceChange: 2.3, totalValue: '₩1,750,000,000', status: 'Active' },
        { id: 2, name: 'Green Energy Fund I', type: '친환경 에너지 (Eco)', symbol: 'GEF-I', holdings: '8,200', price: '₩110,000', priceChange: -1.2, totalValue: '₩902,000,000', status: 'Active' },
        { id: 3, name: 'Quantum Tech Growth II', type: '기술 벤처 (Tech VC)', symbol: 'QTG-II', holdings: '5,000', price: '₩210,000', priceChange: 5.8, totalValue: '₩784,000,000', status: 'Lock-up' }
      ]
    }
    
    return list
  }, [krwBal, hdytQty, hdytVal, gnpmQty, gnpmVal])

  // 3. Dynamic Donut Chart Allocation Calculator
  const allocation = useMemo(() => {
    const total = realTotalValue || 1
    const list = []
    
    if (krwBal > 0) {
      list.push({
        name: "원화 예치금",
        percentage: Math.round((krwBal / total) * 100),
        color: "bg-secondary"
      })
    }
    if (hdytQty > 0) {
      list.push({
        name: "홍대 청년주택 (HDYT)",
        percentage: Math.round((hdytVal / total) * 100),
        color: "bg-chart-2"
      })
    }
    if (gnpmQty > 0) {
      list.push({
        name: "강남 오피스 (GNPM)",
        percentage: Math.round((gnpmVal / total) * 100),
        color: "bg-chart-3"
      })
    }

    // Default mock allocations if empty
    if (list.length === 0) {
      return [
        { name: 'Commercial Real Estate', percentage: 65, color: 'bg-secondary' },
        { name: 'Eco Infrastructure', percentage: 25, color: 'bg-chart-2' },
        { name: 'Venture Capital', percentage: 10, color: 'bg-chart-3' },
      ]
    }
    
    return list
  }, [realTotalValue, krwBal, hdytQty, hdytVal, gnpmQty, gnpmVal])

  // Donut chart rings geometry
  const circles = useMemo(() => {
    let accumulatedCircumference = 0
    const totalCircumference = 251.33
    // Curated accent colors matching style variables
    const strokeColors = ["#0051d5", "#0b1c30", "#565e74", "#10b981", "#f59e0b"]
    
    return allocation.map((item, idx) => {
      const percentage = item.percentage
      const strokeLength = (percentage / 100) * totalCircumference
      const strokeOffset = -accumulatedCircumference
      accumulatedCircumference += strokeLength
      
      return {
        stroke: strokeColors[idx % strokeColors.length],
        strokeDasharray: `${strokeLength} ${totalCircumference}`,
        strokeDashoffset: strokeOffset
      }
    })
  }, [allocation])

  if (loading && realWallets.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center py-20 text-slate-400 gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-secondary" />
        <p className="text-sm font-semibold">포트폴리오 대시보드 로딩 중...</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Top Row: Summary & Allocation */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* Total Asset Value Card */}
        <div className="col-span-1 lg:col-span-8 bg-card border border-outline-variant rounded p-4 md:p-6 flex flex-col shadow-sm">
          <div className="flex flex-col md:flex-row md:justify-between md:items-start mb-6 gap-4">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <h2 className="text-label-caps text-muted-foreground">Total Asset Value</h2>
                <button onClick={loadRealData} className="p-1 hover:bg-surface-container rounded text-muted-foreground hover:text-foreground">
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </div>
              <div className="flex flex-col md:flex-row md:items-baseline gap-2 md:gap-3">
                <span className="text-display-lg font-bold text-foreground font-mono">
                  {formatCurrency(totalValue)}
                </span>
                <span className="text-sm text-secondary font-semibold flex items-center bg-secondary/10 px-2 py-0.5 rounded w-fit">
                  <TrendingUp className="w-3.5 h-3.5 mr-1" />
                  +{currentConfig.change}% ({currentConfig.changePeriod})
                </span>
              </div>
            </div>
            <div className="flex gap-2">
              {timeRanges.map((range) => (
                <button
                  key={range}
                  onClick={() => setSelectedRange(range)}
                  className={cn(
                    "px-3 py-1 text-xs font-semibold border rounded transition-colors",
                    selectedRange === range
                      ? "border-secondary text-secondary bg-surface-container-low"
                      : "border-outline-variant text-muted-foreground hover:border-secondary hover:text-secondary"
                  )}
                >
                  {range}
                </button>
              ))}
            </div>
          </div>

          {/* Dynamic Trend Graph */}
          <div className="flex-1 min-h-[160px] relative w-full border-b border-outline-variant mb-2">
            {/* Grid lines */}
            <div className="absolute inset-0 flex flex-col justify-between pointer-events-none opacity-20">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="w-full border-t border-outline" />
              ))}
            </div>
            {/* SVG Line Chart (dynamic path based on state) */}
            <svg className="w-full h-full" preserveAspectRatio="none" viewBox="0 0 800 160">
              <defs>
                <linearGradient id="chart-gradient" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stopColor="#0051d5" stopOpacity="0.3" />
                  <stop offset="100%" stopColor="#0051d5" stopOpacity="0" />
                </linearGradient>
              </defs>
              <path
                d={currentConfig.trendFillPath}
                fill="url(#chart-gradient)"
                className="transition-all duration-500"
              />
              <path
                d={currentConfig.trendPath}
                fill="none"
                stroke="#0051d5"
                strokeWidth="2"
                className="transition-all duration-500"
              />
            </svg>
          </div>
          <div className="flex justify-between text-xs text-outline px-1">
            {currentConfig.labels.map((lbl, idx) => (
              <span key={idx}>{lbl}</span>
            ))}
          </div>
        </div>

        {/* Allocation Card */}
        <div className="col-span-1 lg:col-span-4 bg-card border border-outline-variant rounded p-4 md:p-6 flex flex-col shadow-sm">
          <h2 className="text-headline-md font-bold text-foreground mb-6">Allocation</h2>
          
          {/* Dynamic Donut Chart */}
          <div className="flex items-center justify-center mb-6">
            <div className="w-28 h-28 md:w-32 md:h-32 rounded-full border-[12px] border-surface-container-highest relative flex items-center justify-center">
              <svg className="absolute inset-0 -rotate-90" viewBox="0 0 100 100">
                {circles.map((c, idx) => (
                  <circle
                    key={idx}
                    cx="50"
                    cy="50"
                    r="40"
                    fill="none"
                    stroke={c.stroke}
                    strokeWidth="12"
                    strokeDasharray={c.strokeDasharray}
                    strokeDashoffset={c.strokeDashoffset}
                    className="transition-all duration-500"
                  />
                ))}
              </svg>
              <div className="text-center">
                <span className="block font-mono text-lg font-bold">{allocation.length}</span>
                <span className="text-label-caps text-muted-foreground">자산 구성</span>
              </div>
            </div>
          </div>

          {/* Legend */}
          <div className="space-y-3">
            {allocation.map((item) => (
              <div key={item.name} className="flex justify-between items-center text-sm">
                <div className="flex items-center">
                  <div className={cn("w-3 h-3 rounded-sm mr-2", item.color)} />
                  <span className="text-foreground">{item.name}</span>
                </div>
                <span className="font-mono text-foreground font-semibold">{item.percentage}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Active STO Participations Table */}
      <div className="bg-card border border-outline-variant rounded shadow-sm overflow-hidden">
        <div className="p-4 md:p-6 border-b border-outline-variant flex flex-col md:flex-row md:justify-between md:items-center gap-4 bg-surface">
          <h2 className="text-headline-md font-bold text-foreground">보유 토큰 및 자산 내역 (Active Holdings)</h2>
          <Link href="/wallet" className="text-secondary text-sm font-semibold hover:underline flex items-center">
            전체 보기 <ArrowRight className="w-4 h-4 ml-1" />
          </Link>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse min-w-[640px]">
            <thead>
              <tr className="border-b border-outline-variant bg-surface-container-low text-label-caps text-muted-foreground">
                <th className="py-2.5 px-4 font-normal">자산명 (Asset Name)</th>
                <th className="py-2.5 px-4 font-normal text-right">보유 수량</th>
                <th className="py-2.5 px-4 font-normal text-right">현재 가치 (평가액)</th>
                <th className="py-2.5 px-4 font-normal text-right">총 자산 가치</th>
                <th className="py-2.5 px-4 font-normal text-center">상태</th>
              </tr>
            </thead>
            <tbody className="text-sm">
              {holdings.map((holding) => {
                const Icon = getAssetIcon(holding.type)
                return (
                  <tr 
                    key={holding.id} 
                    className="border-b border-surface-container-highest hover:bg-surface-container-low transition-colors cursor-pointer"
                  >
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded bg-surface-container flex items-center justify-center border border-surface-variant">
                          <Icon className="w-5 h-5 text-muted-foreground" />
                        </div>
                        <div>
                          <p className="font-semibold text-foreground">{holding.name}</p>
                          <p className="text-xs text-muted-foreground">{holding.type} • {holding.symbol}</p>
                        </div>
                      </div>
                    </td>
                    <td className="py-3 px-4 text-right font-mono text-foreground">{holding.holdings}</td>
                    <td className="py-3 px-4 text-right">
                      <span className="font-mono text-foreground">{holding.price}</span>
                      {holding.priceChange !== 0 && (
                        <span className={cn(
                          "ml-2 text-xs",
                          holding.priceChange >= 0 ? "text-gain" : "text-loss"
                        )}>
                          {holding.priceChange >= 0 ? '+' : ''}{holding.priceChange}%
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-right font-mono font-semibold text-foreground">{holding.totalValue}</td>
                    <td className="py-3 px-4 text-center">
                      <span className={cn(
                        "px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border",
                        holding.status === 'Active'
                          ? "border-secondary text-secondary bg-secondary/10"
                          : "border-outline text-muted-foreground bg-surface-container"
                      )}>
                        {holding.status}
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Dividend Simulator Card */}
      <div className="bg-card border border-outline-variant rounded p-6 shadow-sm flex flex-col mt-6 gap-6">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <TrendingUp className="w-5 h-5 text-secondary" />
            <h2 className="text-headline-md font-bold text-foreground">실시간 예상 배당금 시뮬레이터 (Dividend Payout Simulator)</h2>
          </div>
          <p className="text-xs text-muted-foreground">
            가상의 총 배당 재원(KRW)을 입력하면, 현재 보유 중인 STO 지분율에 비례하여 개별 수령할 예상 배당금을 실시간으로 모의 연산합니다.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 items-start">
          {/* Input Panel */}
          <div className="flex flex-col gap-2">
            <label className="text-xs font-semibold text-muted-foreground font-medium">총 배당 재원 입력 (KRW)</label>
            <input
              type="number"
              placeholder="예: 10,000,000"
              value={simulatedTotalBudget}
              onChange={(e) => setSimulatedTotalBudget(e.target.value)}
              className="w-full bg-surface border border-outline-variant rounded p-3 text-sm text-foreground outline-none focus:border-secondary transition-colors placeholder-muted-foreground font-mono"
            />
          </div>

          {/* Results Panel */}
          <div className="md:col-span-3 flex flex-col gap-3">
            <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">예상 배당 수령액 상세</h3>
            {dividendSimulations.length === 0 ? (
              <p className="text-xs text-slate-500 italic py-2">총 배당 재원을 입력하고 보유 STO를 기반으로 계산해 보세요.</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {dividendSimulations.map((sim, idx) => (
                  <div key={idx} className="p-4 bg-surface-container-low border border-outline-variant rounded-xl flex flex-col gap-1">
                    <div className="flex justify-between items-center">
                      <span className="text-xs font-bold text-secondary">{sim.symbol}</span>
                      <span className="text-[10px] text-muted-foreground font-mono">{sim.ratio}% 지분</span>
                    </div>
                    <p className="text-sm font-semibold text-foreground truncate mt-1">{sim.name}</p>
                    <div className="flex justify-between items-baseline border-t border-outline-variant/30 pt-2 mt-1">
                      <span className="text-[10px] text-slate-500">예상 수령액</span>
                      <span className="text-sm font-bold text-foreground font-mono">
                        {new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(sim.payout)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Gasless Transfer Card */}
      <div className="bg-card border border-outline-variant rounded p-6 shadow-sm flex flex-col mt-6 gap-6">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <ShieldCheck className="w-5 h-5 text-secondary" />
            <h2 className="text-headline-md font-bold text-foreground">가스비 대납 토큰증권 이체 (Gasless Relayed Transfer)</h2>
          </div>
          <p className="text-xs text-muted-foreground">
            블록체인 가스비(수수료)를 지불할 ETH 코인이 없어도 비밀키 서명만으로 토큰증권을 안전하게 무상 양도할 수 있습니다. 수수료는 TOKIT Relayer가 대납합니다.
          </p>
        </div>

        <form onSubmit={handleGaslessTransfer} className="grid grid-cols-1 md:grid-cols-4 gap-4 items-end">
          <div className="flex flex-col gap-2">
            <label className="text-xs font-semibold text-muted-foreground">전송할 자산 선택</label>
            <select
              value={transferAsset}
              onChange={(e) => setTransferAsset(e.target.value)}
              className="bg-surface border border-outline-variant rounded p-3 text-sm text-foreground outline-none focus:border-secondary transition-colors"
            >
              <option value="">-- 자산 선택 --</option>
              {realWallets.filter(w => w.assetSymbol && w.assetSymbol !== "KRW" && w.balance > 0).map((wallet) => (
                <option key={wallet.id} value={wallet.assetSymbol}>
                  {wallet.assetSymbol} (잔고: {wallet.balance})
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-2 md:col-span-2">
            <label className="text-xs font-semibold text-muted-foreground">수신자 지갑 주소 (0x...)</label>
            <input
              type="text"
              placeholder="0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
              value={transferTo}
              onChange={(e) => setTransferTo(e.target.value)}
              className="bg-surface border border-outline-variant rounded p-3 text-sm text-foreground outline-none focus:border-secondary transition-colors placeholder-muted-foreground"
            />
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-xs font-semibold text-muted-foreground">전송 수량</label>
            <div className="flex gap-2">
              <input
                type="number"
                step="any"
                placeholder="0.0"
                value={transferAmount}
                onChange={(e) => setTransferAmount(e.target.value)}
                className="flex-1 bg-surface border border-outline-variant rounded p-3 text-sm text-foreground outline-none focus:border-secondary transition-colors placeholder-muted-foreground"
              />
              <button
                type="submit"
                disabled={transferLoading}
                className="bg-secondary text-primary font-bold px-5 py-3 rounded hover:bg-secondary/90 transition-colors flex items-center justify-center gap-1.5 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {transferLoading ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    이체
                  </>
                )}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  )
}
