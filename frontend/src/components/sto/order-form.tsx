"use client"

import { useState, useCallback, useEffect } from "react"
import { Loader2 } from "lucide-react"
import { cn } from "@/lib/utils"
import { fetchApi } from "@/lib/api"
import { toast } from "sonner"

interface OrderFormProps {
  symbol?: string
  currentPrice?: number
  selectedPrice?: number
  availableBalance?: number
  availableTokens?: number
  onOrderSubmit?: () => void
}

export function OrderForm({
  symbol = "GNPM",
  currentPrice = 12500,
  selectedPrice,
  availableBalance = 5000000,
  availableTokens = 450,
  onOrderSubmit,
}: OrderFormProps) {
  const [orderType, setOrderType] = useState<"limit" | "market">("limit")
  const [side, setSide] = useState<"buy" | "sell">("buy")
  const [price, setPrice] = useState(selectedPrice?.toString() || currentPrice.toString())
  const [quantity, setQuantity] = useState("")
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [userId, setUserId] = useState<number>(1)

  useEffect(() => {
    if (typeof window !== "undefined") {
      const raw = localStorage.getItem("tokit_userId")
      if (raw) setUserId(parseInt(raw, 10))
    }
  }, [])

  useEffect(() => {
    if (selectedPrice) {
      setPrice(selectedPrice.toString())
    }
  }, [selectedPrice])

  const priceNum = parseInt(price.replace(/,/g, "")) || 0
  const quantityNum = parseInt(quantity.replace(/,/g, "")) || 0
  const totalAmount = orderType === "market" ? quantityNum * currentPrice : priceNum * quantityNum

  const maxBuyQuantity = priceNum > 0 ? Math.floor(availableBalance / priceNum) : 0
  const maxSellQuantity = availableTokens

  const formatNumber = (value: string) => {
    const num = value.replace(/[^\d]/g, "")
    return num ? parseInt(num).toLocaleString() : ""
  }

  const handleQuantityPercent = useCallback((percent: number) => {
    if (side === "buy") {
      const qty = Math.floor(maxBuyQuantity * (percent / 100))
      setQuantity(qty.toLocaleString())
    } else {
      const qty = Math.floor(maxSellQuantity * (percent / 100))
      setQuantity(qty.toLocaleString())
    }
  }, [side, maxBuyQuantity, maxSellQuantity])

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

  const handleSubmit = useCallback(async () => {
    if (quantityNum <= 0) return
    if (orderType === "limit" && priceNum <= 0) return

    setIsSubmitting(true)
    try {
      const key = generateUUID()
      await fetchApi("/api/orders", {
        method: "POST",
        headers: {
          "X-Idempotency-Key": key
        },
        body: JSON.stringify({
          userId: userId,
          assetSymbol: symbol,
          orderType: side === "buy" ? "BUY" : "SELL",
          price: orderType === "market" ? currentPrice : priceNum,
          quantity: quantityNum
        })
      })
      toast.success(`${side === "buy" ? "매수" : "매도"} 주문이 성공적으로 접수되었습니다.`)
      setQuantity("")
      onOrderSubmit?.()
    } catch (err: any) {
      console.error(err)
      toast.error("주문 실패: " + err.message)
    } finally {
      setIsSubmitting(false)
    }
  }, [quantityNum, priceNum, orderType, side, symbol, currentPrice, userId, onOrderSubmit])

  const isValidOrder = quantityNum > 0 && (orderType === "market" || priceNum > 0)
  const hasEnoughBalance = side === "buy" ? totalAmount <= availableBalance : quantityNum <= availableTokens

  return (
    <div className="bg-card border border-outline-variant rounded shadow-sm">
      <div className="p-4 border-b border-outline-variant">
        <h3 className="font-semibold text-foreground">주문</h3>
      </div>

      <div className="p-4 space-y-4">
        {/* Buy/Sell Tabs */}
        <div className="grid grid-cols-2 gap-1 p-1 bg-surface-container rounded">
          <button
            onClick={() => setSide("buy")}
            className={cn(
              "py-2 text-sm font-semibold rounded transition-colors",
              side === "buy"
                ? "bg-gain text-white"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            매수
          </button>
          <button
            onClick={() => setSide("sell")}
            className={cn(
              "py-2 text-sm font-semibold rounded transition-colors",
              side === "sell"
                ? "bg-loss text-white"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            매도
          </button>
        </div>

        {/* Order Type */}
        <div className="flex gap-2">
          <button
            onClick={() => setOrderType("limit")}
            className={cn(
              "flex-1 py-2 text-sm font-medium rounded border transition-colors",
              orderType === "limit"
                ? "border-secondary bg-secondary/10 text-secondary"
                : "border-outline-variant text-muted-foreground hover:border-secondary hover:text-secondary"
            )}
          >
            지정가
          </button>
          <button
            onClick={() => setOrderType("market")}
            className={cn(
              "flex-1 py-2 text-sm font-medium rounded border transition-colors",
              orderType === "market"
                ? "border-secondary bg-secondary/10 text-secondary"
                : "border-outline-variant text-muted-foreground hover:border-secondary hover:text-secondary"
            )}
          >
            시장가
          </button>
        </div>

        {/* Price Input */}
        {orderType === "limit" && (
          <div>
            <label className="text-label-caps text-muted-foreground mb-2 block">
              주문 가격
            </label>
            <div className="relative">
              <input
                type="text"
                value={formatNumber(price)}
                onChange={(e) => setPrice(e.target.value.replace(/,/g, ""))}
                className="w-full px-3 py-2.5 pr-12 bg-surface-container border border-outline-variant rounded text-foreground font-mono focus:border-secondary focus:ring-1 focus:ring-secondary/20 focus:outline-none transition-colors"
                disabled={isSubmitting}
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
                원
              </span>
            </div>
          </div>
        )}

        {/* Quantity Input */}
        <div>
          <div className="flex justify-between mb-2">
            <span className="text-label-caps text-muted-foreground">수량</span>
            <span className="text-xs text-muted-foreground">
              {side === "buy" ? (
                <>주문 가능: <span className="text-foreground font-medium">{maxBuyQuantity.toLocaleString()}</span></>
              ) : (
                <>보유: <span className="text-foreground font-medium">{availableTokens.toLocaleString()} {symbol}</span></>
              )}
            </span>
          </div>
          <div className="relative">
            <input
              type="text"
              placeholder="수량 입력"
              value={formatNumber(quantity)}
              onChange={(e) => setQuantity(e.target.value.replace(/,/g, ""))}
              className="w-full px-3 py-2.5 pr-16 bg-surface-container border border-outline-variant rounded text-foreground font-mono focus:border-secondary focus:ring-1 focus:ring-secondary/20 focus:outline-none transition-colors placeholder-outline"
              disabled={isSubmitting}
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
              {symbol}
            </span>
          </div>
        </div>

        {/* Quick Quantity Buttons */}
        <div className="grid grid-cols-4 gap-2">
          {[25, 50, 75, 100].map((percent) => (
            <button
              key={percent}
              onClick={() => handleQuantityPercent(percent)}
              className="py-1.5 text-xs font-medium border border-outline-variant rounded text-muted-foreground hover:border-secondary hover:text-secondary transition-colors"
              disabled={isSubmitting}
            >
              {percent}%
            </button>
          ))}
        </div>

        {/* Total Amount */}
        <div className="bg-surface-container-low rounded p-3">
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">총 주문 금액</span>
            <span className={cn(
              "font-bold font-mono",
              side === "buy" ? "text-gain" : "text-loss"
            )}>
              {totalAmount.toLocaleString()}원
            </span>
          </div>
          {!hasEnoughBalance && quantityNum > 0 && (
            <p className="text-xs text-destructive mt-2">
              {side === "buy" ? "잔고가 부족합니다" : "보유 수량이 부족합니다"}
            </p>
          )}
        </div>

        {/* Submit Button */}
        <button
          className={cn(
            "w-full py-3 rounded font-semibold text-white transition-colors disabled:opacity-50 disabled:cursor-not-allowed",
            side === "buy" 
              ? "bg-gain hover:bg-gain/90" 
              : "bg-loss hover:bg-loss/90"
          )}
          disabled={isSubmitting || !isValidOrder || !hasEnoughBalance}
          onClick={handleSubmit}
        >
          {isSubmitting ? (
            <span className="flex items-center justify-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin" />
              주문 전송 중...
            </span>
          ) : (
            `${side === "buy" ? "매수" : "매도"} 주문`
          )}
        </button>
      </div>
    </div>
  )
}
