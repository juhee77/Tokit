'use client'

import React, { useState, useEffect, useCallback } from 'react'
import { useRouter, useParams } from 'next/navigation'
import Link from 'next/link'
import { fetchApi } from '@/lib/api'
import { toast } from 'sonner'
import { ArrowLeft, MessageSquare, Calendar, User, Trash2, Send, Loader2 } from 'lucide-react'

interface PostResponse {
  id: number
  title: string
  content: string
  userId: number
  userName: string
  assetId: number | null
  assetName: string | null
  commentsCount: number
  createdAt: string
}

interface CommentResponse {
  id: number
  content: string
  userId: number
  userName: string
  createdAt: string
}

export default function PostDetailPage() {
  const router = useRouter()
  const params = useParams()
  const postId = params.id as string

  const [post, setPost] = useState<PostResponse | null>(null)
  const [comments, setComments] = useState<CommentResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [commentLoading, setCommentLoading] = useState(false)
  const [currentUserId, setCurrentUserId] = useState<number>(1)

  // Form states
  const [newComment, setNewComment] = useState('')

  const loadPostDetails = useCallback(async (userIdVal: number) => {
    if (!postId) return
    setLoading(true)
    try {
      // Load post
      const postData = await fetchApi<PostResponse>(`/api/posts/${postId}`)
      setPost(postData)

      // Load comments
      const commentList = await fetchApi<CommentResponse[]>(`/api/posts/${postId}/comments`)
      setComments(commentList)
    } catch (err: any) {
      console.error('Failed to load post details:', err)
      if (err.message && (
        err.message.includes("not found") || 
        err.message.includes("NOT_FOUND") || 
        err.message.includes("User not found")
      )) {
        try {
          const signupRes = await fetchApi<any>("/api/users/signup", {
            method: "POST",
            body: JSON.stringify({
              email: "test-investor@tokit.com",
              name: "김토킷",
              walletAddress: "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
            })
          })
          const newId = signupRes.id
          setCurrentUserId(newId)
          if (typeof window !== "undefined") {
            localStorage.setItem("tokit_userId", newId.toString())
          }
          // Retry
          const retryPost = await fetchApi<PostResponse>(`/api/posts/${postId}`)
          setPost(retryPost)
          const retryComments = await fetchApi<CommentResponse[]>(`/api/posts/${postId}/comments`)
          setComments(retryComments)
        } catch (signupErr: any) {
          console.error("Auto signup inside read page failed", signupErr)
          toast.error("세션 동기화 실패: " + signupErr.message)
        }
      } else {
        toast.error("게시글 로드 실패: " + err.message)
      }
    } finally {
      setLoading(false)
    }
  }, [postId])

  useEffect(() => {
    let savedId = 1
    if (typeof window !== "undefined") {
      const raw = localStorage.getItem("tokit_userId")
      if (raw) savedId = parseInt(raw, 10)
    }
    setCurrentUserId(savedId)
    loadPostDetails(savedId)
  }, [loadPostDetails])

  const handleDeletePost = async () => {
    if (!post || !confirm('정말로 이 게시글을 삭제하시겠습니까?')) return

    try {
      await fetchApi<void>(`/api/posts/${post.id}?userId=${currentUserId}`, {
        method: 'DELETE',
      })
      toast.success("게시글이 삭제되었습니다.")
      router.push('/community')
    } catch (err: any) {
      toast.error("게시글 삭제 실패: " + err.message)
    }
  }

  const handleCreateComment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!post || !newComment.trim()) return

    const idempotencyKey = crypto.randomUUID()
    setCommentLoading(true)
    try {
      const addedComment = await fetchApi<CommentResponse>(`/api/posts/${post.id}/comments`, {
        method: 'POST',
        headers: {
          'X-Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          content: newComment.trim(),
          userId: currentUserId,
        }),
      })

      setComments(prev => [...prev, addedComment])
      setNewComment('')
      toast.success("댓글이 등록되었습니다.")
    } catch (err: any) {
      toast.error("댓글 등록 실패: " + err.message)
    } finally {
      setCommentLoading(false)
    }
  }

  const handleDeleteComment = async (commentId: number) => {
    if (!confirm('정말로 이 댓글을 삭제하시겠습니까?')) return

    try {
      await fetchApi<void>(`/api/posts/comments/${commentId}?userId=${currentUserId}`, {
        method: 'DELETE',
      })
      setComments(prev => prev.filter(c => c.id !== commentId))
      toast.success("댓글이 삭제되었습니다.")
    } catch (err: any) {
      toast.error("댓글 삭제 실패: " + err.message)
    }
  }

  if (loading) {
    return (
      <div className="min-h-[50vh] flex flex-col items-center justify-center gap-3">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
        <p className="text-sm text-muted-foreground">게시글 상세 데이터를 불러오고 있습니다...</p>
      </div>
    )
  }

  if (!post) {
    return (
      <div className="max-w-3xl mx-auto py-12 px-4 text-center space-y-4">
        <p className="text-muted-foreground">게시글을 찾을 수 없거나 삭제되었습니다.</p>
        <Link href="/community" className="text-sm text-primary hover:underline">
          커뮤니티 목록으로 가기
        </Link>
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto py-8 px-4 sm:px-6 lg:px-8 space-y-6">
      {/* Back button */}
      <div>
        <Link
          href="/community"
          className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft size={16} />
          목록으로 돌아가기
        </Link>
      </div>

      {/* Post content body */}
      <div className="bg-surface-container/30 border border-outline-variant/60 rounded-2xl p-6 sm:p-8 space-y-6 shadow-sm">
        <div className="flex flex-wrap justify-between items-center gap-2 pb-4 border-b border-outline-variant">
          <div className="flex items-center gap-2">
            <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold ${
              post.assetId ? 'bg-indigo-500/10 text-indigo-400' : 'bg-emerald-500/10 text-emerald-400'
            }`}>
              {post.assetId ? 'STO 토론' : '자유주제'}
            </span>
            {post.assetName && (
              <span className="text-xs text-muted-foreground font-semibold">
                &gt; {post.assetName}
              </span>
            )}
          </div>
          
          {post.userId === currentUserId && (
            <button
              onClick={handleDeletePost}
              className="inline-flex items-center gap-1 text-xs text-red-400 hover:text-red-300 transition-colors font-semibold cursor-pointer"
            >
              <Trash2 size={12} />
              글 삭제
            </button>
          )}
        </div>

        <div className="space-y-3">
          <h1 className="text-2xl font-bold tracking-tight text-foreground">{post.title}</h1>
          <div className="flex items-center gap-4 text-xs text-muted-foreground">
            <span className="flex items-center gap-1">
              <User size={12} />
              {post.userName}
            </span>
            <span className="flex items-center gap-1">
              <Calendar size={12} />
              {new Date(post.createdAt).toLocaleString()}
            </span>
          </div>
        </div>

        <p className="text-sm text-foreground/90 whitespace-pre-wrap leading-relaxed">
          {post.content}
        </p>
      </div>

      {/* Comments section */}
      <div className="space-y-4">
        <h3 className="text-lg font-bold text-foreground flex items-center gap-2">
          <MessageSquare className="w-5 h-5 text-primary" />
          댓글 ({comments.length})
        </h3>

        {/* Comment list */}
        <div className="space-y-3">
          {comments.length === 0 ? (
            <p className="text-xs text-muted-foreground py-4 text-center">
              아직 작성된 댓글이 없습니다. 첫 댓글을 남겨보세요!
            </p>
          ) : (
            comments.map((comment) => (
              <div
                key={comment.id}
                className="bg-surface-container/20 border border-outline-variant/40 rounded-xl p-4 flex gap-4 justify-between items-start transition-all"
              >
                <div className="space-y-1.5 flex-1">
                  <div className="flex items-center gap-2.5 text-xs text-muted-foreground">
                    <span className="font-semibold text-foreground/80">{comment.userName}</span>
                    <span>•</span>
                    <span>{new Date(comment.createdAt).toLocaleString()}</span>
                  </div>
                  <p className="text-xs text-foreground/95 leading-relaxed">{comment.content}</p>
                </div>

                {comment.userId === currentUserId && (
                  <button
                    onClick={() => handleDeleteComment(comment.id)}
                    className="p-1 rounded text-muted-foreground hover:text-red-400 transition-colors cursor-pointer"
                    title="댓글 삭제"
                  >
                    <Trash2 size={14} />
                  </button>
                )}
              </div>
            ))
          )}
        </div>

        {/* Write comment form */}
        <form onSubmit={handleCreateComment} className="flex gap-2 items-center p-2 bg-background border border-outline-variant rounded-xl focus-within:ring-2 focus-within:ring-primary/30 focus-within:border-primary transition-all">
          <input
            type="text"
            required
            disabled={commentLoading}
            placeholder="댓글을 자유롭게 남겨보세요..."
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            className="flex-1 px-3 py-2 bg-transparent border-none outline-none text-xs text-foreground placeholder:text-muted-foreground focus:ring-0 focus:outline-none disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={commentLoading}
            className="p-2.5 bg-primary text-primary-foreground rounded-lg hover:bg-primary/95 transition-all flex items-center justify-center shrink-0 cursor-pointer shadow-sm disabled:opacity-50"
            title="댓글 등록"
          >
            {commentLoading ? (
              <Loader2 size={12} className="animate-spin" />
            ) : (
              <Send size={12} className="fill-current" />
            )}
          </button>
        </form>
      </div>
    </div>
  )
}
