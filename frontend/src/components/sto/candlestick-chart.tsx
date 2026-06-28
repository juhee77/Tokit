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
import { Loader2, BarChart2 } from "lucide-react"
import { cn } from "@/lib/utils"

interface CandleData {
  time: string      // Display label (e.g. "HH:mm", "MM-DD", "YYYY-MM")
  fullTime: string  // Detailed time label
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

const intervals = [
  { label: "1분", value: "1m" },
  { label: "10분", value: "10m" },
  { label: "1시간", value: "1h" },
  { label: "1일", value: "1d" },
  { label: "1달", value: "30d" },
]

export function CandlestickChart({ symbol, currentPrice }: CandlestickChartProps) {
  const [dbCandles, setDbCandles] = useState<ApiCandle[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedInterval, setSelectedInterval] = useState<string>("1m")
  const recentTrades = useTradeStore((state) => state.recentTrades)

  // 1. Fetch DB historical 1m candles on symbol change
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

  // Get active interval label
  const selectedIntervalLabel = useMemo(() => {
    return intervals.find(i => i.value === selectedInterval)?.label || "1분"
  }, [selectedInterval])

  // 2. Aggregate 1m DB candles into selected interval
  const aggregatedDbCandles = useMemo(() => {
    if (selectedInterval === "1m" || dbCandles.length === 0) {
      return dbCandles
    }

    const groups: Record<string, ApiCandle[]> = {}
    dbCandles.forEach(c => {
      let key = c.time.slice(0, 16) // "YYYY-MM-DDTHH:mm"
      if (selectedInterval === "10m") {
        key = key.slice(0, 15) + "0"
      } else if (selectedInterval === "1h") {
        key = key.slice(0, 13) + ":00"
      } else if (selectedInterval === "1d") {
        key = key.slice(0, 10)
      } else if (selectedInterval === "30d") {
        key = key.slice(0, 7)
      }

      if (!groups[key]) groups[key] = []
      groups[key].push(c)
    })

    return Object.keys(groups).sort().map(key => {
      const group = groups[key]
      const open = group[0].open
      const close = group[group.length - 1].close
      const high = Math.max(...group.map(c => c.high))
      const low = Math.min(...group.map(c => c.low))
      const volume = group.reduce((sum, c) => sum + c.volume, 0)
      
      const isoTime = selectedInterval === "30d" 
        ? key + "-01T00:00:00" 
        : selectedInterval === "1d" 
        ? key + "T00:00:00" 
        : key + ":00"

      return {
        time: isoTime,
        open,
        high,
        low,
        close,
        volume
      }
    })
  }, [dbCandles, selectedInterval])

  // 3. Generate fallback rich mock history based on selected interval
  const mockHistory = useMemo(() => {
    const history: ApiCandle[] = []
    let price = currentPrice || 12500
    const now = new Date()
    
    let stepMs = 60 * 1000
    let count = 30
    
    if (selectedInterval === "10m") stepMs = 10 * 60 * 1000
    else if (selectedInterval === "1h") stepMs = 60 * 60 * 1000
    else if (selectedInterval === "1d") stepMs = 24 * 60 * 60 * 1000
    else if (selectedInterval === "30d") {
      stepMs = 30 * 24 * 60 * 60 * 1000
      count = 12
    }

    for (let i = count - 1; i >= 0; i--) {
      const time = new Date(now.getTime() - i * stepMs)
      const dateStr = time.toISOString().split(".")[0] // YYYY-MM-DDTHH:mm:ss

      const change = (Math.random() - 0.48) * (price * 0.012)
      const open = price
      const close = price + change
      const high = Math.max(open, close) + Math.random() * (price * 0.005)
      const low = Math.min(open, close) - Math.random() * (price * 0.005)
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
  }, [currentPrice, selectedInterval])

  // 4. Aggregate real-time trades from store by interval
  const realtimeCandles = useMemo(() => {
    if (recentTrades.length === 0) return {}

    const buckets: Record<string, any[]> = {}

    recentTrades.forEach(trade => {
      try {
        const date = new Date(trade.createdAt)
        let bucketKey = ""
        
        if (selectedInterval === "1m") {
          bucketKey = new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes())
            .toISOString().split(".")[0].slice(0, 16)
        } else if (selectedInterval === "10m") {
          const min = Math.floor(date.getMinutes() / 10) * 10
          bucketKey = new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), min)
            .toISOString().split(".")[0].slice(0, 16)
        } else if (selectedInterval === "1h") {
          bucketKey = new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), 0)
            .toISOString().split(".")[0].slice(0, 16)
        } else if (selectedInterval === "1d") {
          bucketKey = new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0)
            .toISOString().split(".")[0].slice(0, 10)
        } else if (selectedInterval === "30d") {
          bucketKey = new Date(date.getFullYear(), date.getMonth(), 1, 0, 0)
            .toISOString().split(".")[0].slice(0, 7)
        }
        
        if (!buckets[bucketKey]) {
          buckets[bucketKey] = []
        }
        buckets[bucketKey].push(trade)
      } catch (e) {
        console.error(e)
      }
    })

    const result: Record<string, ApiCandle> = {}

    Object.keys(buckets).forEach(bucketTime => {
      const trades = buckets[bucketTime]
      const sorted = [...trades].sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
      
      const prices = sorted.map(t => t.price)
      const open = prices[0]
      const close = prices[prices.length - 1]
      const high = Math.max(...prices)
      const low = Math.min(...prices)
      const volume = sorted.reduce((sum, t) => sum + t.quantity, 0)

      const isoTime = selectedInterval === "30d" 
        ? bucketTime + "-01T00:00:00" 
        : selectedInterval === "1d" 
        ? bucketTime + "T00:00:00" 
        : bucketTime + ":00"

      result[bucketTime] = {
        time: isoTime,
        open,
        high,
        low,
        close,
        volume
      }
    })

    return result
  }, [recentTrades, selectedInterval])

  // 5. Merge all sources: DB aggregated (or fallback mock) + real-time updates
  const chartData = useMemo<CandleData[]>(() => {
    const baseCandles = aggregatedDbCandles.length >= 5 ? aggregatedDbCandles : mockHistory
    const mergedMap: Record<string, ApiCandle> = {}

    baseCandles.forEach(c => {
      let key = c.time.slice(0, 16)
      if (selectedInterval === "1d") key = c.time.slice(0, 10)
      else if (selectedInterval === "30d") key = c.time.slice(0, 7)
      
      mergedMap[key] = c
    })

    Object.keys(realtimeCandles).forEach(key => {
      mergedMap[key] = realtimeCandles[key]
    })

    return Object.keys(mergedMap)
      .sort()
      .map(key => {
        const c = mergedMap[key]
        const open = c.open
        const close = c.close
        const high = c.high
        const low = c.low
        const isUp = close >= open

        // Format label timestamp dynamically
        let displayTime = key.split("T")[1]?.slice(0, 5) || ""
        if (selectedInterval === "1d") {
          displayTime = key.slice(5) // MM-DD
        } else if (selectedInterval === "30d") {
          displayTime = key.slice(0, 7) // YYYY-MM
        }

        const fullTimeLabel = selectedInterval === "30d"
          ? key
          : selectedInterval === "1d"
          ? key
          : key.replace("T", " ")

        return {
          time: displayTime,
          fullTime: fullTimeLabel,
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
  }, [aggregatedDbCandles, mockHistory, realtimeCandles, selectedInterval])

  // Custom tool-tip component
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
        <p className="text-sm font-semibold">실시간 시세 차트 로딩 중...</p>
      </div>
    )
  }

  return (
    <div className="bg-card border border-outline-variant rounded shadow-sm p-4 space-y-3">
      {/* Title / Chart Header */}
      <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center border-b border-outline-variant/60 pb-3 gap-3">
        <div className="flex items-center gap-2">
          <BarChart2 className="w-5 h-5 text-secondary" />
          <h3 className="font-semibold text-foreground">실시간 시세 차트 ({selectedIntervalLabel})</h3>
        </div>
        
        <div className="flex items-center gap-3">
          {/* Interval Selector */}
          <div className="flex bg-surface-container rounded border border-outline-variant p-0.5 text-xs">
            {intervals.map((item) => (
              <button
                key={item.value}
                onClick={() => setSelectedInterval(item.value)}
                className={cn(
                  "px-2.5 py-1 rounded-sm font-semibold transition-colors",
                  selectedInterval === item.value
                    ? "bg-secondary text-secondary-foreground"
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                {item.label}
              </button>
            ))}
          </div>
          
          <div className="flex items-center gap-1">
            <span className="text-[10px] uppercase font-bold tracking-wider text-muted-foreground px-2 py-0.5 border border-outline-variant bg-surface rounded">
              Live
            </span>
            <span className="flex h-2 w-2 relative">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
            </span>
          </div>
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
            <Bar dataKey="body" barSize={selectedInterval === "30d" ? 22 : selectedInterval === "1d" ? 14 : 10}>
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

