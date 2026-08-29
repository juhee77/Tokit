'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { fetchApi } from '@/lib/api'
import { useAuthStore } from '@/stores/useAuthStore'

interface LoginResponse {
  accessToken: string
  expiresInMs: number
  userId: number
  email: string
  name: string
}

export default function LoginPage() {
  const router = useRouter()
  const signIn = useAuthStore((s) => s.signIn)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)

    try {
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
      setError(err instanceof Error ? err.message : '로그인에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">TOKIT</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            토큰증권 거래를 시작하려면 로그인하세요.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
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
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}

          <Button type="submit" disabled={submitting} className="mt-2 w-full">
            {submitting ? '로그인 중…' : '로그인'}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          계정이 없으신가요?{' '}
          <Link href="/signup" className="font-medium text-foreground underline underline-offset-4">
            회원가입
          </Link>
        </p>
      </div>
    </div>
  )
}
