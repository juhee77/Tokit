'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useState } from 'react'
import { 
  LayoutDashboard, 
  Wallet, 
  History, 
  Store, 
  Users, 
  HelpCircle, 
  Code,
  Plus,
  ChevronDown,
  Menu,
  X,
  ShieldAlert
} from 'lucide-react'
import { cn } from '@/lib/utils'

const navItems = [
  {
    label: 'Portfolio',
    icon: LayoutDashboard,
    children: [
      { label: 'Dashboard', href: '/' },
      { label: 'My Assets', href: '/wallet' },
      { label: 'History', href: '/history' },
      { label: 'My Page', href: '/mypage' },
    ]
  },
  {
    label: 'Markets',
    icon: Store,
    href: '/markets',
  },
  {
    label: 'Trading',
    icon: Store,
    href: '/trading',
  },
  {
    label: 'Community',
    icon: Users,
    href: '/community',
  },
]

const footerItems = [
  { label: 'Support', icon: HelpCircle, href: '/support' },
  { label: 'API', icon: Code, href: '/api-docs' },
  { label: 'Admin', icon: ShieldAlert, href: '/admin' },
]

export function Sidebar() {
  const pathname = usePathname()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [expandedSection, setExpandedSection] = useState<string | null>('Portfolio')

  const isActive = (href: string) => pathname === href
  const isParentActive = (children?: { href: string }[]) => 
    children?.some(child => pathname === child.href)

  return (
    <>
      {/* Mobile Menu Button */}
      <button 
        onClick={() => setMobileOpen(true)}
        className="fixed top-4 left-4 z-50 md:hidden p-2 rounded bg-card border border-outline-variant"
        aria-label="Open menu"
      >
        <Menu className="w-5 h-5" />
      </button>

      {/* Mobile Overlay */}
      {mobileOpen && (
        <div 
          className="fixed inset-0 bg-black/50 z-40 md:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}

      {/* Sidebar */}
      <nav className={cn(
        "bg-card border-r border-outline-variant flex flex-col h-screen fixed left-0 top-0 w-64 z-50 py-4 px-4 transition-transform duration-300",
        mobileOpen ? "translate-x-0" : "-translate-x-full md:translate-x-0"
      )}>
        {/* Close button for mobile */}
        <button 
          onClick={() => setMobileOpen(false)}
          className="absolute top-4 right-4 md:hidden p-1 rounded hover:bg-muted"
          aria-label="Close menu"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Brand Header */}
        <div className="flex items-center pb-4 border-b border-outline-variant">
          <div className="w-8 h-8 rounded bg-secondary flex items-center justify-center mr-3">
            <span className="text-secondary-foreground font-bold text-sm">T</span>
          </div>
          <div>
            <h1 className="text-headline-md font-bold text-foreground tracking-tight text-lg">TOKIT</h1>
            <p className="text-label-caps text-muted-foreground">Institutional STO</p>
          </div>
        </div>

        {/* CTA Button */}
        <div className="py-6">
          <button className="w-full bg-primary text-primary-foreground flex items-center justify-center py-2.5 px-4 rounded text-sm font-semibold hover:bg-primary/90 transition-colors">
            <Plus className="w-4 h-4 mr-2" />
            Issue Token
          </button>
        </div>

        {/* Navigation */}
        <div className="flex-1 overflow-y-auto space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon
            const hasChildren = 'children' in item && item.children
            const isExpanded = expandedSection === item.label
            const active = hasChildren ? isParentActive(item.children) : isActive(item.href!)

            return (
              <div key={item.label}>
                {hasChildren ? (
                  <>
                    <button
                      onClick={() => setExpandedSection(isExpanded ? null : item.label)}
                      className={cn(
                        "w-full flex items-center px-3 py-2 rounded text-sm transition-all duration-200",
                        active 
                          ? "bg-surface-container text-secondary font-semibold border-r-2 border-secondary" 
                          : "text-muted-foreground hover:bg-surface-container"
                      )}
                    >
                      <Icon className="w-5 h-5 mr-3" />
                      <span className="flex-1 text-left">{item.label}</span>
                      <ChevronDown className={cn(
                        "w-4 h-4 transition-transform",
                        isExpanded && "rotate-180"
                      )} />
                    </button>
                    {isExpanded && (
                      <div className="ml-8 mt-1 flex flex-col gap-1 border-l border-outline-variant pl-3 py-1">
                        {item.children.map((child) => (
                          <Link
                            key={child.href}
                            href={child.href}
                            onClick={() => setMobileOpen(false)}
                            className={cn(
                              "text-sm py-1.5 transition-colors",
                              isActive(child.href)
                                ? "text-secondary font-semibold"
                                : "text-muted-foreground hover:text-foreground"
                            )}
                          >
                            {child.label}
                          </Link>
                        ))}
                      </div>
                    )}
                  </>
                ) : (
                  <Link
                    href={item.href!}
                    onClick={() => setMobileOpen(false)}
                    className={cn(
                      "flex items-center px-3 py-2 rounded text-sm transition-all duration-200",
                      active 
                        ? "bg-surface-container text-secondary font-semibold border-r-2 border-secondary" 
                        : "text-muted-foreground hover:bg-surface-container"
                    )}
                  >
                    <Icon className="w-5 h-5 mr-3" />
                    <span>{item.label}</span>
                  </Link>
                )}
              </div>
            )
          })}
        </div>

        {/* Footer */}
        <div className="border-t border-outline-variant pt-4 mt-auto space-y-1">
          {footerItems.map((item) => {
            const Icon = item.icon
            return (
              <Link
                key={item.href}
                href={item.href}
                className="flex items-center px-3 py-2 rounded text-muted-foreground hover:bg-surface-container transition-colors text-sm"
              >
                <Icon className="w-4 h-4 mr-3" />
                <span className="text-label-caps">{item.label}</span>
              </Link>
            )
          })}
        </div>
      </nav>
    </>
  )
}
