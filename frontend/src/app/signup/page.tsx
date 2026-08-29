'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { fetchApi } from '@/lib/api'
import { useAuthStore } from '@/stores/useAuthStore'

interface SignUpResponse {
  id: number
  email: string
  name: string
  walletAddress: string
  kycStatus: boolean
}

interface LoginResponse {
  accessToken: string
  expiresInMs: number
  userId: number
  email: string
  name: string
}

export default function SignUpPage() {
  const router = useRouter()
  const signIn = useAuthStore((s) => s.signIn)

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [walletAddress, setWalletAddress] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)

    try {
      await fetchApi<SignUpResponse>('/api/users/signup', {
        method: 'POST',
        body: JSON.stringify({ email, name, password, walletAddress }),
      })

      // 가입 직후 바로 로그인시켜 거래 화면으로 진입시킵니다.
      const result = await fetchApi<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })

      signIn(result.accessToken, {
        userId: result.userId,
        email: result.email,
        name: result.name,
      })
      router.replace('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : '회원가입에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">회원가입</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            TOKIT 계정을 만들고 토큰증권에 투자해 보세요.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="name">이름</Label>
            <Input
              id="name"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="김토킷"
            />
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="email">이메일</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="investor@tokit.com"
            />
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="password">비밀번호</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <p className="text-xs text-muted-foreground">8자 이상 입력하세요.</p>
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="walletAddress">지갑 주소</Label>
            <Input
              id="walletAddress"
              required
              value={walletAddress}
              onChange={(e) => setWalletAddress(e.target.value)}
              placeholder="0x..."
              className="font-mono text-xs"
            />
          </div>

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}

          <Button type="submit" disabled={submitting} className="mt-2 w-full">
            {submitting ? '가입 중…' : '회원가입'}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          이미 계정이 있으신가요?{' '}
          <Link href="/login" className="font-medium text-foreground underline underline-offset-4">
            로그인
          </Link>
        </p>
      </div>
    </div>
  )
}
