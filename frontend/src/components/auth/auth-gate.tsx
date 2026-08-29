'use client'

import { useEffect } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { Sidebar } from '@/components/sto/sidebar'
import { TopNav } from '@/components/sto/top-nav'
import { ConnectionBanner } from '@/components/sto/connection-banner'
import { useAuthStore } from '@/stores/useAuthStore'

/** 로그인 없이 접근할 수 있는 경로. 그 외에는 인증된 세션이 필요합니다. */
const PUBLIC_ROUTES = ['/login', '/signup']

export function AuthGate({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const router = useRouter()
  const { token, loading, restore } = useAuthStore()

  const isPublicRoute = PUBLIC_ROUTES.some((route) => pathname.startsWith(route))

  useEffect(() => {
    restore()
  }, [restore])

  useEffect(() => {
    if (!loading && !token && !isPublicRoute) {
      router.replace('/login')
    }
  }, [loading, token, isPublicRoute, router])

  // 세션 복원 중에는 화면을 그리지 않아 로그인 화면이 깜빡이지 않도록 합니다.
  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <div className="text-sm text-muted-foreground">불러오는 중…</div>
      </div>
    )
  }

  // 로그인/회원가입 화면은 사이드바 없이 단독으로 보여줍니다.
  if (isPublicRoute) {
    return <main className="min-h-screen bg-background">{children}</main>
  }

  if (!token) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <div className="text-sm text-muted-foreground">로그인 화면으로 이동합니다…</div>
      </div>
    )
  }

  return (
    <>
      <ConnectionBanner />
      <div className="flex h-screen">
        <Sidebar />
        <div className="flex-1 flex flex-col md:ml-64 min-w-0">
          <TopNav />
          <main className="flex-1 overflow-y-auto bg-background pt-20 md:pt-24 pb-4 px-4 md:pb-6 md:px-6">
            {children}
          </main>
        </div>
      </div>
    </>
  )
}
