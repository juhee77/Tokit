'use client'

import { useState, useEffect } from 'react'
import { usePathname } from 'next/navigation'
import Link from 'next/link'
import { Search, Bell, Settings, ChevronRight, ChevronDown } from 'lucide-react'
import { fetchApi } from '@/lib/api'

interface MyPageUserResponse {
  user: {
    name: string;
    kycStatus: boolean;
  };
}

const breadcrumbMap: Record<string, string[]> = {
  '/': ['Portfolio', 'Dashboard'],
  '/wallet': ['Portfolio', 'My Assets'],
  '/history': ['Portfolio', 'History'],
  '/mypage': ['Portfolio', 'My Page'],
  '/settings': ['Portfolio', 'Settings'],
  '/notifications': ['Portfolio', 'Notifications'],
  '/markets': ['Markets', 'All Assets'],
  '/trading': ['Trading', 'Exchange'],
  '/community': ['Community'],
}

export function TopNav() {
  const pathname = usePathname()
  const [searchQuery, setSearchQuery] = useState('')
  const [userProfile, setUserProfile] = useState({ name: 'J. Smith', initials: 'JS', role: 'Institutional' })
  
  useEffect(() => {
    const loadUserProfile = async () => {
      const savedId = localStorage.getItem("tokit_userId") || "1"
      try {
        const data = await fetchApi<MyPageUserResponse>(`/api/users/${savedId}/mypage`)
        if (data && data.user) {
          const name = data.user.name
          let initials = 'US'
          if (name && name.length > 0) {
            // 한글 이름의 경우 첫 두글자 또는 성과 이름 이니셜
            initials = name.substring(0, Math.min(name.length, 2))
          }
          setUserProfile({
            name: name,
            initials: initials,
            role: data.user.kycStatus ? 'Certified Investor' : 'General Investor'
          })
        }
      } catch (error) {
        console.error("Failed to load user profile in TopNav:", error)
      }
    }
    loadUserProfile()
  }, [])

  const breadcrumbs = breadcrumbMap[pathname] || ['Dashboard']

  return (
    <header className="bg-surface border-b border-outline-variant flex justify-between items-center px-4 md:px-6 h-16 fixed top-0 right-0 w-full md:w-[calc(100%-16rem)] z-40">
      {/* Breadcrumbs */}
      <div className="flex items-center ml-12 md:ml-0">
        {breadcrumbs.map((crumb, index) => (
          <div key={crumb} className="flex items-center">
            {index > 0 && (
              <ChevronRight className="w-4 h-4 mx-1 md:mx-2 text-outline-variant" />
            )}
            <span className={
              index === breadcrumbs.length - 1
                ? "text-xs md:text-sm font-semibold text-foreground"
                : "text-xs md:text-sm text-muted-foreground"
            }>
              {crumb}
            </span>
          </div>
        ))}
      </div>

      {/* Right Actions */}
      <div className="flex items-center gap-2 md:gap-4">
        {/* Search - Hidden on mobile */}
        <div className="relative hidden lg:block w-64">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-outline" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search assets, markets..."
            className="w-full bg-card border border-outline-variant rounded pl-9 pr-3 py-1.5 text-sm focus:border-secondary focus:ring-1 focus:ring-secondary/20 focus:outline-none placeholder-outline text-foreground transition-colors"
          />
        </div>

        {/* Icons */}
        <Link href="/notifications" className="text-muted-foreground hover:text-secondary transition-colors relative p-2">
          <Bell className="w-5 h-5" />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-destructive rounded-full border border-surface" />
        </Link>
        <Link href="/settings" className="text-muted-foreground hover:text-secondary transition-colors p-2 hidden md:block">
          <Settings className="w-5 h-5" />
        </Link>

        {/* User Profile - Wrapped with Link for My Page routing */}
        <Link href="/mypage" className="flex items-center ml-2 pl-2 md:pl-4 border-l border-outline-variant cursor-pointer hover:bg-surface-container-low transition-colors rounded text-left no-underline">
          <div className="w-8 h-8 rounded-full bg-secondary flex items-center justify-center text-secondary-foreground font-bold text-sm">
            {userProfile.initials}
          </div>
          <div className="hidden xl:block text-left ml-2">
            <p className="text-sm font-semibold text-foreground leading-none">{userProfile.name}</p>
            <p className="text-[10px] text-muted-foreground leading-none mt-1">{userProfile.role}</p>
          </div>
          <ChevronDown className="w-4 h-4 text-outline ml-2 hidden md:block" />
        </Link>
      </div>
    </header>
  )
}
