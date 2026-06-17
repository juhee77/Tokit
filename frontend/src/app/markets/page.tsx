'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Filter, ArrowUpDown, Building2, Factory, Leaf, Briefcase, ChevronLeft } from 'lucide-react'
import { cn } from '@/lib/utils'
import { fetchApi } from '@/lib/api'
import { OfferingDashboard } from '@/components/sto/offering-dashboard'

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

const categories = ['All Assets', 'Real Estate', 'Private Equity', 'Infrastructure', 'Venture']

function getAssetMetadata(symbol: string) {
  if (symbol === 'GNPM' || symbol.includes('GNPM')) {
    return {
      type: 'Commercial Real Estate',
      category: 'Real Estate',
      targetIrr: '12.4%',
      minInvestment: '100,000원',
      term: '5 Yrs',
      icon: Building2,
      target: 50000000000,
    }
  } else if (symbol === 'HDYT' || symbol.includes('HDYT')) {
    return {
      type: 'Residential Real Estate',
      category: 'Real Estate',
      targetIrr: '8.5%',
      minInvestment: '50,000원',
      term: '3 Yrs',
      icon: Building2,
      target: 5000000000,
    }
  } else {
    return {
      type: 'Alternative Asset',
      category: 'Venture',
      targetIrr: '15.0%',
      minInvestment: '10,000원',
      term: '4 Yrs',
      icon: Briefcase,
      target: 1000000000,
    }
  }
}

function formatKRW(value: number) {
  if (value >= 100000000) {
    return `${(value / 100000000).toFixed(1)}억원`
  }
  if (value >= 10000) {
    return `${(value / 10000).toFixed(0)}만원`
  }
  return `${value.toLocaleString()}원`
}

export default function MarketsPage() {
  const router = useRouter()
  const [assets, setAssets] = useState<AssetResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedCategory, setSelectedCategory] = useState('All Assets')
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null)

  useEffect(() => {
    async function loadAssets() {
      try {
        const data = await fetchApi<AssetResponse[]>('/api/assets')
        setAssets(data)
      } catch (err) {
        console.error('Failed to load assets', err)
      } finally {
        setLoading(false)
      }
    }
    loadAssets()
  }, [])

  if (selectedSymbol) {
    return (
      <div className="max-w-[1440px] mx-auto space-y-6">
        <button
          onClick={() => setSelectedSymbol(null)}
          className="flex items-center gap-2 px-4 py-2 border border-outline-variant rounded bg-card text-foreground text-sm hover:bg-surface-container-low transition-colors cursor-pointer"
        >
          <ChevronLeft className="w-4 h-4" />
          마켓 목록으로 돌아가기
        </button>
        <OfferingDashboard symbol={selectedSymbol} onBack={() => setSelectedSymbol(null)} />
      </div>
    )
  }

  const filteredAssets = selectedCategory === 'All Assets'
    ? assets
    : assets.filter((asset) => {
        const meta = getAssetMetadata(asset.symbol)
        return meta.category === selectedCategory
      })

  const primaryMarkets = filteredAssets.filter((a) => a.status === '청약중')
  const secondaryMarkets = filteredAssets.filter((a) => a.status === '거래중')

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center p-20 text-muted-foreground">
        Loading Assets...
      </div>
    )
  }

  return (
    <div className="max-w-[1440px] mx-auto space-y-8">
      {/* Filter Chips */}
      <div className="flex gap-2 overflow-x-auto pb-2">
        {categories.map((category) => (
          <button
            key={category}
            onClick={() => setSelectedCategory(category)}
            className={cn(
              "px-4 py-1.5 rounded-full border text-sm cursor-pointer whitespace-nowrap transition-colors",
              selectedCategory === category
                ? "border-secondary bg-secondary/10 text-secondary font-medium"
                : "border-outline-variant bg-card text-muted-foreground hover:bg-surface-container-low"
            )}
          >
            {category}
          </button>
        ))}
      </div>

      {/* Primary Markets (청약 시장) */}
      <div className="space-y-4">
        <div>
          <h2 className="text-headline-md font-bold text-foreground mb-1">Primary Markets (공모 청약)</h2>
          <p className="text-sm text-muted-foreground">현재 청약이 진행 중인 신규 토큰증권 공모 상품입니다.</p>
        </div>

        {primaryMarkets.length === 0 ? (
          <div className="p-8 border border-dashed border-outline-variant rounded bg-card text-center text-muted-foreground text-sm">
            해당 카테고리의 청약 진행 중인 자산이 없습니다.
          </div>
        ) : (
          <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
            {primaryMarkets.map((asset) => {
              const meta = getAssetMetadata(asset.symbol)
              const Icon = meta.icon
              const targetAmount = asset.totalSupply * asset.issuePrice
              const currentAmount = asset.currentAmount || 0
              const progress = (currentAmount / targetAmount) * 100

              return (
                <div
                  key={asset.id}
                  onClick={() => setSelectedSymbol(asset.symbol)}
                  className="bg-card border border-outline-variant rounded p-4 flex flex-col shadow-sm hover:border-secondary transition-colors cursor-pointer group"
                >
                  <div className="flex justify-between items-start mb-4">
                    <div className="flex gap-4 items-center">
                      <div className="w-12 h-12 rounded bg-surface-container flex items-center justify-center border border-surface-variant">
                        <Icon className="w-6 h-6 text-muted-foreground" />
                      </div>
                      <div>
                        <h3 className="text-title-sm font-semibold text-foreground group-hover:text-secondary transition-colors">
                          {asset.name}
                        </h3>
                        <p className="text-sm text-muted-foreground">{meta.type} • {asset.symbol}</p>
                      </div>
                    </div>
                    <span className="px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border border-secondary text-secondary bg-secondary/10">
                      청약중
                    </span>
                  </div>

                  <div className="grid grid-cols-3 gap-4 mb-4 border-y border-surface-container-highest py-3">
                    <div>
                      <p className="text-label-caps text-muted-foreground mb-1">목표 수익률(IRR)</p>
                      <p className="font-mono text-foreground text-base font-semibold">{meta.targetIrr}</p>
                    </div>
                    <div>
                      <p className="text-label-caps text-muted-foreground mb-1">최소 투자금</p>
                      <p className="font-mono text-foreground text-base font-semibold">{meta.minInvestment}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-label-caps text-muted-foreground mb-1">투자 기간</p>
                      <p className="font-mono text-foreground text-base font-semibold">{meta.term}</p>
                    </div>
                  </div>

                  <div className="mt-auto">
                    <div className="flex justify-between text-sm mb-1">
                      <span className="text-foreground font-medium">{formatKRW(currentAmount)} 청약 완료</span>
                      <span className="text-muted-foreground">목표 {formatKRW(targetAmount)}</span>
                    </div>
                    {/* Progress Bar */}
                    <div className="w-full h-2 bg-surface-container-highest rounded overflow-hidden">
                      <div
                        className="h-full bg-secondary transition-all"
                        style={{ width: `${Math.min(progress, 100)}%` }}
                      />
                    </div>
                    <p className="text-xs text-muted-foreground mt-2 text-right">
                      달성률 {progress.toFixed(1)}% ({asset.totalInvestors || 0}명 참여)
                    </p>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Secondary Markets (유통 거래 시장) */}
      <div className="space-y-4">
        <div>
          <h2 className="text-headline-md font-bold text-foreground mb-1">Secondary Markets (유통 거래)</h2>
          <p className="text-sm text-muted-foreground">발행이 완료되어 실시간 거래소에서 거래 중인 토큰증권입니다.</p>
        </div>

        {secondaryMarkets.length === 0 ? (
          <div className="p-8 border border-dashed border-outline-variant rounded bg-card text-center text-muted-foreground text-sm">
            해당 카테고리의 거래 중인 자산이 없습니다.
          </div>
        ) : (
          <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
            {secondaryMarkets.map((asset) => {
              const meta = getAssetMetadata(asset.symbol)
              const Icon = meta.icon
              const totalVolume = asset.totalSupply * asset.issuePrice

              return (
                <div
                  key={asset.id}
                  onClick={() => router.push(`/trading?symbol=${asset.symbol}`)}
                  className="bg-card border border-outline-variant rounded p-4 flex flex-col shadow-sm hover:border-secondary transition-colors cursor-pointer group"
                >
                  <div className="flex justify-between items-start mb-4">
                    <div className="flex gap-4 items-center">
                      <div className="w-12 h-12 rounded bg-surface-container flex items-center justify-center border border-surface-variant">
                        <Icon className="w-6 h-6 text-muted-foreground" />
                      </div>
                      <div>
                        <h3 className="text-title-sm font-semibold text-foreground group-hover:text-secondary transition-colors">
                          {asset.name}
                        </h3>
                        <p className="text-sm text-muted-foreground">{meta.type} • {asset.symbol}</p>
                      </div>
                    </div>
                    <span className="px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border border-green-600 text-green-600 bg-green-50">
                      거래중
                    </span>
                  </div>

                  <div className="grid grid-cols-3 gap-4 mb-4 border-y border-surface-container-highest py-3">
                    <div>
                      <p className="text-label-caps text-muted-foreground mb-1">기준가</p>
                      <p className="font-mono text-foreground text-base font-semibold">{asset.issuePrice.toLocaleString()}원</p>
                    </div>
                    <div>
                      <p className="text-label-caps text-muted-foreground mb-1">총 발행량</p>
                      <p className="font-mono text-foreground text-base font-semibold">{asset.totalSupply.toLocaleString()} STO</p>
                    </div>
                    <div className="text-right">
                      <p className="text-label-caps text-muted-foreground mb-1">시가총액</p>
                      <p className="font-mono text-foreground text-base font-semibold">{formatKRW(totalVolume)}</p>
                    </div>
                  </div>

                  <div className="flex justify-between items-center text-sm mt-auto text-secondary font-semibold group-hover:underline">
                    <span>실시간 거래소 바로가기 →</span>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
