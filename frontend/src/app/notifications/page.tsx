'use client'

import { useState, useEffect } from 'react'
import { Bell, Check, ShieldCheck, CreditCard, ShoppingBag, X } from 'lucide-react'
import { toast } from 'sonner'

interface NotificationItem {
  id: number
  type: 'kyc' | 'deposit' | 'trade' | 'system'
  title: string
  message: string
  time: string
  isRead: boolean
}

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([
    {
      id: 1,
      type: 'trade',
      title: '주문 체결 완료',
      message: 'TEST-CONCUR 자산에 대한 매수 주문 5개가 가격 10,000원에 체결 완료되었습니다.',
      time: '방금 전',
      isRead: false,
    },
    {
      id: 2,
      type: 'kyc',
      title: 'KYC 온체인 화이트리스트 등록 성공',
      message: '스마트 컨트랙트 화이트리스트에 지갑 주소가 성공적으로 추가되었습니다. 이제 자유롭게 토큰증권 거래가 가능합니다.',
      time: '3분 전',
      isRead: false,
    },
    {
      id: 3,
      type: 'deposit',
      title: '원화(KRW) 입금 승인',
      message: '예치금 계좌로 요청하신 1,000,000 KRW 입금 승인이 완료되었습니다. (멱등성 승인완료)',
      time: '15분 전',
      isRead: true,
    },
    {
      id: 4,
      type: 'system',
      title: 'TOKIT STO 매칭엔진 분산 큐 연결됨',
      message: '로컬 RabbitMQ 브로커에 정상 연결되어 실시간 호가 전송 채널이 활성화되었습니다.',
      time: '1시간 전',
      isRead: true,
    }
  ])

  const handleMarkAllRead = () => {
    setNotifications(notifications.map(n => ({ ...n, isRead: true })))
    toast.success("모든 알림을 읽음 처리했습니다.")
  }

  const handleDeleteNotification = (id: number) => {
    setNotifications(notifications.filter(n => n.id !== id))
  }

  const getIcon = (type: string) => {
    switch (type) {
      case 'kyc': return <ShieldCheck className="w-5 h-5 text-gain" />
      case 'deposit': return <CreditCard className="w-5 h-5 text-secondary" />
      case 'trade': return <ShoppingBag className="w-5 h-5 text-warning" />
      default: return <Bell className="w-5 h-5 text-muted-foreground" />
    }
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="flex items-center justify-between pb-4 border-b border-outline-variant">
        <div className="flex items-center gap-3">
          <Bell className="w-8 h-8 text-secondary" />
          <div>
            <h2 className="text-2xl font-bold text-foreground">알림 보드 (Notifications)</h2>
            <p className="text-sm text-muted-foreground">실시간 온체인 전송 및 주문/체결 발생 알림 내역</p>
          </div>
        </div>
        
        {notifications.some(n => !n.isRead) && (
          <button
            onClick={handleMarkAllRead}
            className="text-xs bg-surface-container hover:bg-surface-container-high border border-outline-variant px-3 py-1.5 rounded transition-colors text-foreground font-medium"
          >
            모든 알림 읽음 처리
          </button>
        )}
      </div>

      <div className="space-y-3">
        {notifications.length === 0 ? (
          <div className="text-center py-20 text-muted-foreground bg-card border border-outline-variant rounded">
            수신된 알림이 없습니다.
          </div>
        ) : (
          notifications.map((n) => (
            <div
              key={n.id}
              className={`flex items-start gap-4 p-4 border rounded shadow-sm transition-all bg-card ${
                n.isRead ? 'border-outline-variant opacity-75' : 'border-secondary bg-secondary/5 font-semibold'
              }`}
            >
              <div className="p-2 rounded bg-surface-container-low mt-0.5">
                {getIcon(n.type)}
              </div>
              <div className="flex-1 text-left">
                <div className="flex items-baseline justify-between gap-2">
                  <h4 className="text-sm font-bold text-foreground">{n.title}</h4>
                  <span className="text-[10px] text-muted-foreground font-mono">{n.time}</span>
                </div>
                <p className="text-xs text-muted-foreground leading-relaxed mt-1">{n.message}</p>
              </div>
              
              <button
                onClick={() => handleDeleteNotification(n.id)}
                className="text-muted-foreground hover:text-foreground transition-colors p-1"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
