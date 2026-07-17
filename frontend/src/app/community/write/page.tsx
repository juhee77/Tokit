'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { fetchApi } from '@/lib/api'
import { toast } from 'sonner'
import { MessageSquare, ArrowLeft, Loader2, CheckCircle } from 'lucide-react'

interface Asset {
  id: number
  name: string
  symbol: string
  status: string
}

export default function WritePostPage() {
  const router = useRouter()
  const [assets, setAssets] = useState<Asset[]>([])
  const [userId, setUserId] = useState<number>(1)
  const [loading, setLoading] = useState(false)
  const [assetsLoading, setAssetsLoading] = useState(true)

  // Form states
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [assetId, setAssetId] = useState('')

  useEffect(() => {
    // Load assets
    async function loadAssets() {
      try {
        const data = await fetchApi<Asset[]>('/api/assets')
        setAssets(data)
      } catch (err: any) {
        console.error('Failed to load assets:', err)
      } finally {
        setAssetsLoading(false)
      }
    }

    // Load user session
    let savedId = 1
    if (typeof window !== "undefined") {
      const raw = localStorage.getItem("tokit_userId")
      if (raw) savedId = parseInt(raw, 10)
    }
    setUserId(savedId)

    loadAssets()
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !content.trim()) return

    setLoading(true)
    const idempotencyKey = crypto.randomUUID()
    const assetIdVal = assetId ? parseInt(assetId) : null

    const makePostRequest = async (targetUserId: number) => {
      await fetchApi<any>('/api/posts', {
        method: 'POST',
        headers: {
          'X-Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          title: title.trim(),
          content: content.trim(),
          userId: targetUserId,
          assetId: assetIdVal,
        }),
      })
    }

    try {
      await makePostRequest(userId)
      toast.success("게시글이 성공적으로 등록되었습니다.")
      router.push('/community')
    } catch (err: any) {
      if (err.message && (
        err.message.includes("not found") || 
        err.message.includes("NOT_FOUND") || 
        err.message.includes("User not found")
      )) {
        try {
          // Auto signup
          const signupRes = await fetchApi<any>("/api/users/signup", {
            method: "POST",
            body: JSON.stringify({
              email: "test-investor@tokit.com",
              name: "김토킷",
              walletAddress: "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
            })
          })
          const newId = signupRes.id
          setUserId(newId)
          if (typeof window !== "undefined") {
            localStorage.setItem("tokit_userId", newId.toString())
          }
          // Retry
          await makePostRequest(newId)
          toast.success("김토킷 계정이 생성되고 게시글이 성공적으로 등록되었습니다!")
          router.push('/community')
        } catch (signupErr: any) {
          console.error("Auto signup inside write page failed", signupErr)
          toast.error("계정 생성 실패: " + signupErr.message)
        }
      } else {
        toast.error("게시글 등록 실패: " + err.message)
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-3xl mx-auto py-8 px-4 sm:px-6 lg:px-8 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between pb-4 border-b border-outline-variant">
        <button
          onClick={() => router.push('/community')}
          className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
        >
          <ArrowLeft size={16} />
          돌아가기
        </button>
        <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
          <MessageSquare className="w-5 h-5 text-primary" />
          새 게시글 작성
        </h2>
        <div className="w-20" /> {/* Spacer */}
      </div>

      {/* Write Form */}
      <form onSubmit={handleSubmit} className="space-y-6 bg-surface-container/30 border border-outline-variant/60 rounded-2xl p-6 sm:p-8 shadow-sm">
        {/* Title input */}
        <div className="space-y-2">
          <label htmlFor="title" className="block text-xs font-semibold text-muted-foreground">
            게시글 제목
          </label>
          <input
            id="title"
            type="text"
            required
            disabled={loading}
            placeholder="제목을 입력하세요..."
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full px-4 py-2.5 bg-background border border-outline-variant rounded-xl text-sm focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary transition-all disabled:opacity-50"
          />
        </div>

        {/* Tagged Asset select */}
        <div className="space-y-2">
          <label htmlFor="asset" className="block text-xs font-semibold text-muted-foreground">
            연관 토큰증권 (선택)
          </label>
          <select
            id="asset"
            disabled={loading || assetsLoading}
            value={assetId}
            onChange={(e) => setAssetId(e.target.value)}
            className="w-full px-4 py-2.5 bg-background border border-outline-variant rounded-xl text-sm focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary transition-all disabled:opacity-50 appearance-none cursor-pointer"
          >
            <option value="">연관된 토큰증권 없음 (일반 잡담)</option>
            {assets.map((asset) => (
              <option key={asset.id} value={asset.id}>
                [{asset.symbol}] {asset.name}
              </option>
            ))}
          </select>
        </div>

        {/* Content textarea */}
        <div className="space-y-2">
          <label htmlFor="content" className="block text-xs font-semibold text-muted-foreground">
            본문 내용
          </label>
          <textarea
            id="content"
            required
            rows={10}
            disabled={loading}
            placeholder="여기에 생각이나 의견을 자유롭게 적어보세요..."
            value={content}
            onChange={(e) => setContent(e.target.value)}
            className="w-full px-4 py-3 bg-background border border-outline-variant rounded-xl text-sm focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary transition-all resize-none disabled:opacity-50"
          />
        </div>

        {/* Action buttons */}
        <div className="flex justify-end gap-3 pt-4 border-t border-outline-variant">
          <button
            type="button"
            disabled={loading}
            onClick={() => router.push('/community')}
            className="px-5 py-2.5 bg-secondary-container hover:bg-secondary-container/90 text-secondary-foreground rounded-xl text-sm font-semibold transition-all cursor-pointer disabled:opacity-50"
          >
            취소
          </button>
          <button
            type="submit"
            disabled={loading}
            className="px-5 py-2.5 bg-primary text-primary-foreground hover:bg-primary/95 rounded-xl text-sm font-semibold transition-all flex items-center gap-2 cursor-pointer shadow-sm disabled:opacity-50"
          >
            {loading ? (
              <>
                <Loader2 size={16} className="animate-spin" />
                등록 중...
              </>
            ) : (
              <>
                <CheckCircle size={16} />
                게시글 발행
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  )
}
