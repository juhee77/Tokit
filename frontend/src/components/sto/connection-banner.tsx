"use client"

import { useState, useEffect, useCallback } from "react"
import { cn } from "@/lib/utils"
import { WifiOff, RefreshCw, X } from "lucide-react"
import { Button } from "@/components/ui/button"

interface ConnectionStatus {
  isConnected: boolean
  isReconnecting: boolean
  lastConnected?: Date
}

interface ConnectionBannerProps {
  onRetry?: () => void
}

export function ConnectionBanner({ onRetry }: ConnectionBannerProps) {
  const [status, setStatus] = useState<ConnectionStatus>({
    isConnected: true,
    isReconnecting: false,
  })
  const [isDismissed, setIsDismissed] = useState(false)

  // Simulate connection status changes (for demo)
  useEffect(() => {
    // Simulate random disconnections for demo
    const interval = setInterval(() => {
      if (Math.random() > 0.95 && status.isConnected) {
        setStatus({
          isConnected: false,
          isReconnecting: true,
          lastConnected: new Date(),
        })
        setIsDismissed(false)

        // Auto-reconnect after 3-5 seconds
        setTimeout(() => {
          setStatus({
            isConnected: true,
            isReconnecting: false,
          })
        }, 3000 + Math.random() * 2000)
      }
    }, 5000)

    return () => clearInterval(interval)
  }, [status.isConnected])

  const handleRetry = useCallback(() => {
    setStatus(prev => ({ ...prev, isReconnecting: true }))
    onRetry?.()
    
    // Simulate reconnection
    setTimeout(() => {
      setStatus({
        isConnected: true,
        isReconnecting: false,
      })
    }, 2000)
  }, [onRetry])

  if (status.isConnected || isDismissed) return null

  return (
    <div className={cn(
      "fixed top-0 left-0 right-0 z-50 bg-warning/90 text-warning-foreground px-4 py-2",
      "animate-in slide-in-from-top duration-300"
    )}>
      <div className="container mx-auto flex items-center justify-between">
        <div className="flex items-center gap-3">
          {status.isReconnecting ? (
            <RefreshCw className="h-4 w-4 animate-spin" />
          ) : (
            <WifiOff className="h-4 w-4" />
          )}
          <span className="text-sm font-medium">
            {status.isReconnecting 
              ? "실시간 연결이 끊어졌습니다. 재연결 중..." 
              : "네트워크 연결이 끊어졌습니다. 주문이 비활성화되었습니다."}
          </span>
        </div>
        <div className="flex items-center gap-2">
          {!status.isReconnecting && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleRetry}
              className="h-7 text-xs hover:bg-warning-foreground/20"
            >
              <RefreshCw className="h-3 w-3 mr-1" />
              재연결
            </Button>
          )}
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setIsDismissed(true)}
            className="h-7 w-7 hover:bg-warning-foreground/20"
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  )
}

// Hook to check connection status
export function useConnectionStatus() {
  const [isConnected, setIsConnected] = useState(true)

  useEffect(() => {
    const handleOnline = () => setIsConnected(true)
    const handleOffline = () => setIsConnected(false)

    window.addEventListener("online", handleOnline)
    window.addEventListener("offline", handleOffline)

    return () => {
      window.removeEventListener("online", handleOnline)
      window.removeEventListener("offline", handleOffline)
    }
  }, [])

  return isConnected
}
