'use client'

import { useState, useEffect } from 'react'
import { ShieldCheck, Eye, EyeOff, Key, Monitor, BellRing, Settings as SettingsIcon } from 'lucide-react'
import { toast } from 'sonner'

export default function SettingsPage() {
  const [showApiKey, setShowApiKey] = useState(false)
  const [currentUserId, setCurrentUserId] = useState('49')
  const [apiKey] = useState('tk_live_51Nzh1D2e82f10b7f8e8112c3...')

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const raw = localStorage.getItem('tokit_userId')
      if (raw) setCurrentUserId(raw)
    }
  }, [])

  const handleRegenKey = () => {
    toast.success("새로운 API 키가 성공적으로 발급되었습니다.")
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center gap-3 pb-4 border-b border-outline-variant">
        <SettingsIcon className="w-8 h-8 text-secondary" />
        <div>
          <h2 className="text-2xl font-bold text-foreground">시스템 설정 (Settings)</h2>
          <p className="text-sm text-muted-foreground">TOKIT STO 플랫폼의 사용자 환경설정 및 API 관리</p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Navigation/Categories */}
        <div className="space-y-2">
          <button className="w-full flex items-center gap-3 px-4 py-3 bg-surface-container text-secondary font-semibold border-r-2 border-secondary rounded text-left text-sm transition-all">
            <Monitor className="w-4 h-4" />
            일반 환경 설정
          </button>
          <button onClick={() => toast.info("준비 중인 기능입니다.")} className="w-full flex items-center gap-3 px-4 py-3 hover:bg-surface-container text-muted-foreground rounded text-left text-sm transition-all">
            <ShieldCheck className="w-4 h-4" />
            계정 및 보안
          </button>
          <button onClick={() => toast.info("준비 중인 기능입니다.")} className="w-full flex items-center gap-3 px-4 py-3 hover:bg-surface-container text-muted-foreground rounded text-left text-sm transition-all">
            <BellRing className="w-4 h-4" />
            알림 수신 설정
          </button>
        </div>

        {/* Settings Body */}
        <div className="md:col-span-2 space-y-6">
          {/* Section 1 */}
          <div className="bg-card border border-outline-variant rounded p-6 shadow-sm space-y-4">
            <h3 className="text-lg font-semibold text-foreground border-b border-outline-variant pb-2">사용자 및 세션 정보</h3>
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <p className="text-muted-foreground mb-1">테스트 유저 ID</p>
                <p className="font-semibold text-foreground font-mono bg-surface-container-low px-2 py-1.5 rounded">{currentUserId}</p>
              </div>
              <div>
                <p className="text-muted-foreground mb-1">플랫폼 역할</p>
                <p className="font-semibold text-foreground bg-surface-container-low px-2 py-1.5 rounded">전문 투자자 (Institutional)</p>
              </div>
            </div>
          </div>

          {/* Section 2 */}
          <div className="bg-card border border-outline-variant rounded p-6 shadow-sm space-y-4">
            <div className="flex items-center justify-between border-b border-outline-variant pb-2">
              <h3 className="text-lg font-semibold text-foreground">기관용 OpenAPI 연동 키</h3>
              <Key className="w-5 h-5 text-secondary" />
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              기관 투자 전용 매칭 엔진 또는 청약 배치 프로세스를 스크립트로 자동 연동할 때 사용하는 API 인증 키입니다. 외부로 유출되지 않도록 주의해 주세요.
            </p>
            
            <div className="space-y-3">
              <div className="flex items-center gap-2 bg-surface-container-low border border-outline-variant rounded px-3 py-2">
                <input
                  type={showApiKey ? 'text' : 'password'}
                  value={apiKey}
                  readOnly
                  className="bg-transparent border-none text-foreground font-mono text-sm flex-1 focus:outline-none"
                />
                <button
                  onClick={() => setShowApiKey(!showApiKey)}
                  className="text-muted-foreground hover:text-foreground transition-colors"
                >
                  {showApiKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>

              <div className="flex gap-2">
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(apiKey)
                    toast.success("API 키가 클립보드에 복사되었습니다.")
                  }}
                  className="flex-1 bg-surface-container text-foreground border border-outline-variant py-2 rounded text-sm font-medium hover:bg-surface-container-high transition-colors"
                >
                  API 키 복사
                </button>
                <button
                  onClick={handleRegenKey}
                  className="flex-1 bg-secondary text-secondary-foreground py-2 rounded text-sm font-medium hover:bg-secondary/90 transition-colors"
                >
                  재발급 받기
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
