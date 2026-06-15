import type { Metadata } from 'next'
import { Inter, JetBrains_Mono } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import { Sidebar } from '@/components/sto/sidebar'
import { TopNav } from '@/components/sto/top-nav'
import { ConnectionBanner } from '@/components/sto/connection-banner'
import './globals.css'

const inter = Inter({ subsets: ["latin"], variable: '--font-inter' });
const jetbrainsMono = JetBrains_Mono({ subsets: ["latin"], variable: '--font-jetbrains' });

export const metadata: Metadata = {
  title: 'TOKIT - Institutional STO Platform',
  description: '기관투자자를 위한 토큰 증권 투자 플랫폼',
  generator: 'v0.app',
  icons: {
    icon: [
      {
        url: '/icon-light-32x32.png',
        media: '(prefers-color-scheme: light)',
      },
      {
        url: '/icon-dark-32x32.png',
        media: '(prefers-color-scheme: dark)',
      },
      {
        url: '/icon.svg',
        type: 'image/svg+xml',
      },
    ],
    apple: '/apple-icon.png',
  },
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="ko" className={`${inter.variable} ${jetbrainsMono.variable} bg-background light`} style={{ colorScheme: 'light' }}>
      <body className="font-sans antialiased min-h-screen bg-background text-foreground overflow-hidden">
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
        {process.env.NODE_ENV === 'production' && <Analytics />}
      </body>
    </html>
  )
}
