"use client"

import { useState, useCallback } from "react"
import { cn } from "@/lib/utils"
import { useTradeStore } from "@/stores/useTradeStore"

interface OrderbookEntry {
  price: number
  quantity: number
  total: number
  isNew?: boolean
}

interface OrderbookData {
  asks: OrderbookEntry[]
  bids: OrderbookEntry[]
  lastPrice: number
  priceChange: number
  priceChangePercent: number
}

interface OrderbookProps {
  symbol?: string
  onPriceSelect?: (price: number) => void
}

function generateMockOrderbook(): OrderbookData {
  const basePrice = 12500
  const asks: OrderbookEntry[] = []
  const bids: OrderbookEntry[] = []

  for (let i = 14; i >= 0; i--) {
    const price = basePrice + (i + 1) * 10
    const quantity = Math.floor(Math.random() * 5000) + 500
    asks.push({ price, quantity, total: price * quantity })
  }

  for (let i = 0; i < 15; i++) {
    const price = basePrice - i * 10
    const quantity = Math.floor(Math.random() * 5000) + 500
    bids.push({ price, quantity, total: price * quantity })
  }

  return {
    asks,
    bids,
    lastPrice: basePrice,
    priceChange: 150,
    priceChangePercent: 1.21,
  }
}

export function Orderbook({ symbol = "GNPM", onPriceSelect }: OrderbookProps) {
  const [flashCells, setFlashCells] = useState<Set<string>>(new Set());
  
  // Zustand 스토어 연동 (실시간 WebSocket 데이터)
  const orderBook = useTradeStore((state) => state.orderBook);

  // MVP UI 구조에 맞춰 데이터 포맷팅
  const data: OrderbookData = {
    asks: [...(orderBook?.asks || [])].reverse().map(a => ({
      price: a.price,
      quantity: a.quantity,
      total: a.price * a.quantity
    })),
    bids: (orderBook?.bids || []).map(b => ({
      price: b.price,
      quantity: b.quantity,
      total: b.price * b.quantity
    })),
    lastPrice: 12500, // 임시
    priceChange: 150,
    priceChangePercent: 1.21,
  };

  const maxAskQuantity = data.asks.length > 0 ? Math.max(...data.asks.map(a => a.quantity)) : 1;
  const maxBidQuantity = data.bids.length > 0 ? Math.max(...data.bids.map(b => b.quantity)) : 1;

  const handlePriceClick = useCallback((price: number) => {
    onPriceSelect?.(price)
  }, [onPriceSelect])

  return (
    <div className="bg-card border border-outline-variant rounded shadow-sm h-full">
      <div className="p-4 border-b border-outline-variant flex items-center justify-between">
        <h3 className="font-semibold text-foreground">호가창</h3>
        <span className="text-xs text-muted-foreground">{symbol}</span>
      </div>
      
      {/* Header */}
      <div className="grid grid-cols-3 text-label-caps text-muted-foreground px-4 py-2 border-b border-surface-container-highest">
        <span>가격</span>
        <span className="text-right">수량</span>
        <span className="text-right">누적</span>
      </div>

      {/* Ask orders (매도) */}
      <div className="max-h-[200px] md:max-h-[300px] overflow-auto flex flex-col justify-end">
        {data.asks.length === 0 ? (
          <div className="text-center py-8 text-xs text-slate-500">매도 호가 없음</div>
        ) : (
          data.asks.map((ask, i) => (
            <div
              key={`ask-${i}`}
              className={cn(
                "grid grid-cols-3 text-sm px-4 py-1.5 cursor-pointer hover:bg-surface-container-low relative transition-colors",
                flashCells.has(`ask-${i}`) && "bg-loss/20"
              )}
              onClick={() => handlePriceClick(ask.price)}
            >
              <div 
                className="absolute right-0 top-0 h-full bg-loss/10"
                style={{ width: `${(ask.quantity / maxAskQuantity) * 100}%` }}
              />
              <span className="text-loss relative z-10 font-mono">{ask.price.toLocaleString()}</span>
              <span className="text-right relative z-10 font-mono text-foreground">{ask.quantity.toLocaleString(undefined, { maximumFractionDigits: 2 })}</span>
              <span className="text-right text-muted-foreground relative z-10 font-mono text-xs">
                {(ask.total / 10000).toFixed(0)}만
              </span>
            </div>
          ))
        )}
      </div>

      {/* Current Price */}
      <div className="px-4 py-3 border-y border-outline-variant bg-surface-container-low">
        <div className="flex items-center justify-between">
          <span className={cn(
            "text-lg font-bold font-mono",
            data.priceChange >= 0 ? "text-gain" : "text-loss"
          )}>
            {data.lastPrice.toLocaleString()}원
          </span>
          <span className={cn(
            "text-sm font-mono",
            data.priceChange >= 0 ? "text-gain" : "text-loss"
          )}>
            {data.priceChange >= 0 ? "+" : ""}{data.priceChange.toLocaleString()} ({data.priceChangePercent.toFixed(2)}%)
          </span>
        </div>
      </div>

      {/* Bid orders (매수) */}
      <div className="max-h-[200px] md:max-h-[300px] overflow-auto">
        {data.bids.length === 0 ? (
          <div className="text-center py-8 text-xs text-slate-500">매수 호가 없음</div>
        ) : (
          data.bids.map((bid, i) => (
            <div
              key={`bid-${i}`}
              className={cn(
                "grid grid-cols-3 text-sm px-4 py-1.5 cursor-pointer hover:bg-surface-container-low relative transition-colors",
                flashCells.has(`bid-${i}`) && "bg-gain/20"
              )}
              onClick={() => handlePriceClick(bid.price)}
            >
              <div 
                className="absolute right-0 top-0 h-full bg-gain/10"
                style={{ width: `${(bid.quantity / maxBidQuantity) * 100}%` }}
              />
              <span className="text-gain relative z-10 font-mono">{bid.price.toLocaleString()}</span>
              <span className="text-right relative z-10 font-mono text-foreground">{bid.quantity.toLocaleString(undefined, { maximumFractionDigits: 2 })}</span>
              <span className="text-right text-muted-foreground relative z-10 font-mono text-xs">
                {(bid.total / 10000).toFixed(0)}만
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
