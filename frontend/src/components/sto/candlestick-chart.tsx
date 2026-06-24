"use client"

import { useState, useEffect, useMemo } from "react"
import { 
  ResponsiveContainer, 
  ComposedChart, 
  XAxis, 
  YAxis, 
  Tooltip, 
  Bar, 
  Cell, 
  CartesianGrid 
} from "recharts"
import { useTradeStore } from "@/stores/useTradeStore"
import { fetchApi } from "@/lib/api"
import { Loader2, TrendingUp, BarChart2 } from "lucide-react"

interface CandleData {
  time: string      // Format: "HH:mm"
  fullTime: string  // Format: "YYYY-MM-DD HH:mm"
  open: number
  high: number
  low: number
  close: number
  volume: number
  wick: [number, number]
  body: [number, number]
  isUp: boolean
}

interface ApiCandle {
  time: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

interface CandlestickChartProps {
  symbol: string
  currentPrice: number
}

export function CandlestickChart({ symbol, currentPrice }: CandlestickChartProps) {
  const [dbCandles, setDbCandles] = useState<ApiCandle[]>([])
  const [loading, setLoading] = useState(true)
  const recentTrades = useTradeStore((state) => state.recentTrades)

  // 1. Fetch DB historical candles on symbol change
  useEffect(() => {
    async function loadCandles() {
      setLoading(true)
      try {
        const data = await fetchApi<ApiCandle[]>(`/api/trades/asset/${symbol}/candles`)
        setDbCandles(data || [])
      } catch (e) {
        console.error("Failed to load historical candles:", e)
        setDbCandles([])
      } finally {
        setLoading(false)
      }
    }
    loadCandles()
  }, [symbol])

  // 2. Generate fallback rich mock history if DB has no historical trade candles
  const mockHistory = useMemo(() => {
    if (dbCandles.length >= 5) return [] // Use DB candles if available

    const history: ApiCandle[] = []
    let price = currentPrice || 12500
    const now = new Date()

    // Generate 30 minutes of historical candles
    for (let i = 29; i >= 0; i--) {
      const time = new Date(now.getTime() - i * 60 * 1000)
      const dateStr = time.toISOString().split(".")[0] // YYYY-MM-DDTHH:mm:ss

      // Apply a realistic random walk
      const change = (Math.random() - 0.48) * (price * 0.008) // Slight upward bias
      const open = price
      const close = price + change
      const high = Math.max(open, close) + Math.random() * (price * 0.004)
      const low = Math.min(open, close) - Math.random() * (price * 0.004)
      const volume = Math.floor(Math.random() * 800) + 100

      history.push({
        time: dateStr,
        open: Math.round(open),
        high: Math.round(high),
        low: Math.round(low),
        close: Math.round(close),
        volume: Math.round(volume)
      })
      price = close
    }
    return history
  }, [dbCandles, currentPrice, symbol])

  // 3. Aggregate real-time trades from store by 1-minute intervals
  const realtimeCandles = useMemo(() => {
    if (recentTrades.length === 0) return {}

    const buckets: Record<string, any[]> = {}

    // Group trades by their YYYY-MM-DDTHH:mm bucket
    recentTrades.forEach(trade => {
      try {
        const date = new Date(trade.createdAt)
        // Truncate to the minute: YYYY-MM-DDTHH:mm:00
        const bucketKey = new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes())
          .toISOString().split(".")[0].slice(0, 16) // "YYYY-MM-DDTHH:mm"
        
        if (!buckets[bucketKey]) {
          buckets[bucketKey] = []
        }
        buckets[bucketKey].push(trade)
      } catch (e) {
        console.error(e)
      }
    })

    const result: Record<string, ApiCandle> = {}

    // Calculate OHLCV for each minute bucket
    Object.keys(buckets).forEach(bucketTime => {
      const trades = buckets[bucketTime]
      // Sort trades chronological ascending (oldest first)
      const sorted = [...trades].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
      
      const prices = sorted.map(t => t.price)
      const open = prices[0]
      const close = prices[prices.length - 1]
      const high = Math.max(...prices)
      const low = Math.min(...prices)
      const volume = sorted.reduce((sum, t) => sum + t.quantity, 0)

      result[bucketTime] = {
        time: bucketTime + ":00",
        open,
        high,
        low,
        close,
        volume
      }
    })

    return result
  }, [recentTrades])

  // 4. Merge all sources: DB candles (or fallback mock) + real-time updates
  const chartData = useMemo<CandleData[]>(() => {
    const baseCandles = dbCandles.length >= 5 ? dbCandles : mockHistory
    const mergedMap: Record<string, ApiCandle> = {}

    // Map base candles by YYYY-MM-DDTHH:mm
    baseCandles.forEach(c => {
      const key = c.time.slice(0, 16) // Extract YYYY-MM-DDTHH:mm
      mergedMap[key] = c
    })

    // Merge in real-time aggregated candles (will overwrite base candles for matching minutes)
    Object.keys(realtimeCandles).forEach(key => {
      mergedMap[key] = realtimeCandles[key]
    })

    // Convert merged map back to sorted CandleData list
    return Object.keys(mergedMap)
      .sort()
      .map(key => {
        const c = mergedMap[key]
        const open = c.open
        const close = c.close
        const high = c.high
        const low = c.low
        const isUp = close >= open

        // Format label timestamp
        const timePart = key.split("T")[1] || "" // "HH:mm"

        return {
          time: timePart,
          fullTime: key.replace("T", " "),
          open,
          high,
          low,
          close,
          volume: c.volume,
          wick: [low, high],
          body: [open, close],
          isUp
        }
      })
  }, [dbCandles, mockHistory, realtimeCandles])

  // Custom tool-tip component for professional look
  const CustomTooltip = ({ active, payload }: any) => {
    if (active && payload && payload.length) {
      const data: CandleData = payload[0].payload
      return (
        <div className="bg-card/95 border border-outline-variant rounded p-3 text-xs shadow-lg space-y-1 backdrop-blur font-mono text-foreground">
          <p className="text-muted-foreground font-semibold border-b border-outline-variant/60 pb-1 mb-1">{data.fullTime}</p>
          <div className="grid grid-cols-2 gap-x-3 text-left">
            <span>시가 (Open):</span>
            <span className="text-right font-bold">{data.open.toLocaleString()}원</span>
            <span>고가 (High):</span>
            <span className="text-right font-bold text-gain">{data.high.toLocaleString()}원</span>
            <span>저가 (Low):</span>
            <span className="text-right font-bold text-loss">{data.low.toLocaleString()}원</span>
            <span>종가 (Close):</span>
            <span className="text-right font-bold">{data.close.toLocaleString()}원</span>
            <span>거래량 (Vol):</span>
            <span className="text-right font-bold text-secondary">{data.volume.toLocaleString()}</span>
          </div>
        </div>
      )
    }
    return null
  }

  // Calculate scaling domain for Y Axis
  const yDomain = useMemo(() => {
    if (chartData.length === 0) return ["auto", "auto"]
    const lows = chartData.map(d => d.low)
    const highs = chartData.map(d => d.high)
    const min = Math.min(...lows)
    const max = Math.max(...highs)
    const padding = (max - min) * 0.1 || 100
    return [Math.floor(min - padding), Math.ceil(max + padding)]
  }, [chartData])

  if (loading && dbCandles.length === 0) {
    return (
      <div className="h-[400px] w-full bg-card border border-outline-variant rounded flex flex-col items-center justify-center text-slate-400 gap-3">
        <Loader2 className="h-6 w-6 animate-spin text-secondary" />
        <p className="text-sm font-semibold">실시간 분봉 시세 로딩 중...</p>
      </div>
    )
  }

  return (
    <div className="bg-card border border-outline-variant rounded shadow-sm p-4 space-y-3">
      {/* Title / Chart Header */}
      <div className="flex justify-between items-center border-b border-outline-variant/60 pb-3">
        <div className="flex items-center gap-2">
          <BarChart2 className="w-5 h-5 text-secondary" />
          <h3 className="font-semibold text-foreground">실시간 분봉 차트 (1m Interval)</h3>
        </div>
        <div className="flex items-center gap-1">
          <span className="text-[10px] uppercase font-bold tracking-wider text-muted-foreground px-2 py-0.5 border border-outline-variant bg-surface rounded">
            Live Streaming
          </span>
          <span className="flex h-2 w-2 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
          </span>
        </div>
      </div>

      {/* Chart Body */}
      <div className="h-[340px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={chartData} margin={{ top: 10, right: 5, left: -10, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(var(--color-outline-variant-rgb, 100, 100, 100), 0.15)" vertical={false} />
            <XAxis 
              dataKey="time" 
              stroke="var(--color-muted-foreground, #6b7280)" 
              fontSize={10} 
              tickLine={false} 
              axisLine={false}
              dy={5}
            />
            <YAxis 
              domain={yDomain} 
              stroke="var(--color-muted-foreground, #6b7280)" 
              fontSize={10} 
              tickLine={false} 
              axisLine={false}
              tickFormatter={(val) => val.toLocaleString()}
              orientation="right"
            />
            <Tooltip content={<CustomTooltip />} cursor={{ fill: "rgba(100, 100, 100, 0.05)" }} />
            
            {/* 1. Wick Bar (thin line from low to high) */}
            <Bar dataKey="wick" barSize={1.5} tooltipType="none">
              {chartData.map((entry, index) => (
                <Cell key={`wick-cell-${index}`} fill={entry.isUp ? "var(--color-gain, #22c55e)" : "var(--color-loss, #ef4444)"} />
              ))}
            </Bar>
            
            {/* 2. Body Bar (thick block from open to close) */}
            <Bar dataKey="body" barSize={10}>
              {chartData.map((entry, index) => (
                <Cell key={`body-cell-${index}`} fill={entry.isUp ? "var(--color-gain, #22c55e)" : "var(--color-loss, #ef4444)"} />
              ))}
            </Bar>
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
