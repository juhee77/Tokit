'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import { fetchApi } from '@/lib/api'
import { MessageSquare, Calendar, User, Search, Plus, Trash2, X, ChevronRight } from 'lucide-react'

// --- Interfaces matching backend records ---
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

interface Asset {
  id: number
  name: string
  symbol: string
  status: string
}

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export default function CommunityPage() {
  const [posts, setPosts] = useState<PostResponse[]>([])
  const [assets, setAssets] = useState<Asset[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filters
  const [selectedAssetId, setSelectedAssetId] = useState<string>('all')
  const [searchTerm, setSearchTerm] = useState('')
  const [activeTab, setActiveTab] = useState<'all' | 'free' | 'sto'>('all')

  // Modals
  const [selectedPost, setSelectedPost] = useState<PostResponse | null>(null)
  const [comments, setComments] = useState<CommentResponse[]>([])
  const [commentLoading, setCommentLoading] = useState(false)

  // Form states
  const [newComment, setNewComment] = useState('')

  const [currentUserId, setCurrentUserId] = useState<number>(1)

  useEffect(() => {
    if (typeof window !== "undefined") {
      const raw = localStorage.getItem("tokit_userId")
      if (raw) setCurrentUserId(parseInt(raw, 10))
    }
    loadAssets()
    loadPosts()
  }, [])

  useEffect(() => {
    loadPosts()
  }, [selectedAssetId, activeTab])

  const loadAssets = async () => {
    try {
      const data = await fetchApi<Asset[]>('/api/assets')
      setAssets(data)
    } catch (err: any) {
      console.error('Failed to load assets:', err)
    }
  }

  const loadPosts = async () => {
    setLoading(true)
    setError(null)
    try {
      let endpoint = '/api/posts?size=100'
      if (selectedAssetId !== 'all') {
        endpoint += `&assetId=${selectedAssetId}`
      }
      const pageData = await fetchApi<PageResponse<PostResponse>>(endpoint)
      
      let filtered = pageData.content
      
      // Additional client-side tab filtering
      if (activeTab === 'free') {
        filtered = filtered.filter(p => p.assetId === null)
      } else if (activeTab === 'sto') {
        filtered = filtered.filter(p => p.assetId !== null)
      }

      setPosts(filtered)
    } catch (err: any) {
      setError(err.message || 'Failed to fetch posts.')
    } finally {
      setLoading(false)
    }
  }



  const handleDeletePost = async (postId: number) => {
    if (!confirm('Are you sure you want to delete this post?')) return

    try {
      await fetchApi<void>(`/api/posts/${postId}?userId=${currentUserId}`, {
        method: 'DELETE',
      })
      setSelectedPost(null)
      loadPosts()
    } catch (err: any) {
      alert(err.message || 'Failed to delete post.')
    }
  }

  const handleOpenPostDetails = async (post: PostResponse) => {
    setSelectedPost(post)
    setComments([])
    try {
      const commentList = await fetchApi<CommentResponse[]>(`/api/posts/${post.id}/comments`)
      setComments(commentList)
    } catch (err: any) {
      console.error('Failed to load comments:', err)
    }
  }

  const handleCreateComment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedPost || !newComment.trim()) return

    const idempotencyKey = crypto.randomUUID()
    setCommentLoading(true)
    try {
      const addedComment = await fetchApi<CommentResponse>(`/api/posts/${selectedPost.id}/comments`, {
        method: 'POST',
        headers: {
          'X-Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          content: newComment,
          userId: currentUserId,
        }),
      })

      setComments(prev => [...prev, addedComment])
      setNewComment('')
      
      // Update local posts comments count
      setPosts(prev => prev.map(p => p.id === selectedPost.id ? { ...p, commentsCount: p.commentsCount + 1 } : p))
    } catch (err: any) {
      alert(err.message || 'Failed to post comment.')
    } finally {
      setCommentLoading(false)
    }
  }

  const handleDeleteComment = async (commentId: number) => {
    if (!confirm('Are you sure you want to delete this comment?')) return

    try {
      await fetchApi<void>(`/api/posts/comments/${commentId}?userId=${currentUserId}`, {
        method: 'DELETE',
      })
      setComments(prev => prev.filter(c => c.id !== commentId))
      if (selectedPost) {
        setPosts(prev => prev.map(p => p.id === selectedPost.id ? { ...p, commentsCount: Math.max(0, p.commentsCount - 1) } : p))
      }
    } catch (err: any) {
      alert(err.message || 'Failed to delete comment.')
    }
  }

  const filteredPostsBySearch = posts.filter(post => 
    post.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
    post.content.toLowerCase().includes(searchTerm.toLowerCase()) ||
    (post.assetName && post.assetName.toLowerCase().includes(searchTerm.toLowerCase()))
  )

  return (
    <div className="min-h-screen bg-background text-foreground p-6 md:p-10">
      {/* Header Area */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight bg-gradient-to-r from-primary to-indigo-400 bg-clip-text text-transparent">
            Community Dashboard
          </h1>
          <p className="text-muted-foreground mt-1">
            토큰증권 공모/거래 토론 및 자유로운 소통 공간입니다.
          </p>
        </div>
        <Link
          href="/community/write"
          className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-primary text-primary-foreground font-semibold hover:opacity-90 transition-all shadow-lg shadow-primary/20 cursor-pointer"
        >
          <Plus size={18} />
          글쓰기
        </Link>
      </div>

      {/* Filter and Search Section */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 mb-8">
        {/* Category Tabs */}
        <div className="lg:col-span-6 flex p-1 rounded-xl bg-card border border-outline-variant w-fit">
          <button
            onClick={() => setActiveTab('all')}
            className={`px-4 py-2 rounded-lg font-medium text-sm transition-all cursor-pointer ${
              activeTab === 'all' ? 'bg-primary text-primary-foreground shadow' : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            전체 게시글
          </button>
          <button
            onClick={() => setActiveTab('free')}
            className={`px-4 py-2 rounded-lg font-medium text-sm transition-all cursor-pointer ${
              activeTab === 'free' ? 'bg-primary text-primary-foreground shadow' : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            자유게시판
          </button>
          <button
            onClick={() => setActiveTab('sto')}
            className={`px-4 py-2 rounded-lg font-medium text-sm transition-all cursor-pointer ${
              activeTab === 'sto' ? 'bg-primary text-primary-foreground shadow' : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            STO 토론방
          </button>
        </div>

        {/* Search & Asset Filter */}
        <div className="lg:col-span-6 flex flex-col sm:flex-row gap-4">
          <div className="relative flex-1">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted-foreground" size={18} />
            <input
              type="text"
              placeholder="게시글 내용, 제목, 자산 검색..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-card border border-outline-variant focus:outline-none focus:border-primary text-sm transition-all"
            />
          </div>
          <select
            value={selectedAssetId}
            onChange={e => setSelectedAssetId(e.target.value)}
            className="px-4 py-2.5 rounded-xl bg-card border border-outline-variant focus:outline-none focus:border-primary text-sm cursor-pointer"
          >
            <option value="all">모든 STO 토큰</option>
            {assets.map(asset => (
              <option key={asset.id} value={asset.id}>
                {asset.name} ({asset.symbol})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Main Board Grid */}
      {loading ? (
        <div className="flex flex-col items-center justify-center py-20 gap-3">
          <div className="w-10 h-10 border-4 border-primary border-t-transparent rounded-full animate-spin"></div>
          <span className="text-muted-foreground text-sm font-medium">게시글을 불러오는 중입니다...</span>
        </div>
      ) : error ? (
        <div className="text-center py-20 border border-red-500/20 rounded-2xl bg-red-500/5">
          <p className="text-red-400 font-semibold">{error}</p>
          <button onClick={loadPosts} className="mt-4 text-sm text-primary underline">다시 시도</button>
        </div>
      ) : filteredPostsBySearch.length === 0 ? (
        <div className="text-center py-20 border border-outline-variant rounded-2xl bg-card">
          <MessageSquare className="mx-auto text-muted-foreground mb-4" size={40} />
          <p className="text-muted-foreground">작성된 게시글이 없습니다. 첫 게시글을 작성해 보세요!</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {filteredPostsBySearch.map(post => (
            <div
              key={post.id}
              onClick={() => handleOpenPostDetails(post)}
              className="group flex flex-col justify-between p-6 rounded-2xl bg-card border border-outline-variant hover:border-primary/50 hover:shadow-xl hover:shadow-primary/5 transition-all duration-300 cursor-pointer"
            >
              <div>
                <div className="flex justify-between items-start gap-4 mb-3">
                  <span className={`px-2.5 py-1 rounded-md text-xs font-semibold ${
                    post.assetId 
                      ? 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20' 
                      : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                  }`}>
                    {post.assetId ? `${post.assetName} 토론` : '자유주제'}
                  </span>
                  <span className="text-xs text-muted-foreground flex items-center gap-1">
                    <Calendar size={12} />
                    {new Date(post.createdAt).toLocaleDateString()}
                  </span>
                </div>
                <h3 className="text-lg font-bold group-hover:text-primary transition-colors line-clamp-1 mb-2">
                  {post.title}
                </h3>
                <p className="text-sm text-muted-foreground line-clamp-3 mb-4 leading-relaxed">
                  {post.content}
                </p>
              </div>

              <div className="flex justify-between items-center pt-4 border-t border-outline-variant text-xs">
                <div className="flex items-center gap-1.5 text-muted-foreground">
                  <div className="w-5 h-5 rounded-full bg-primary/10 flex items-center justify-center text-[10px] text-primary font-bold">
                    {post.userName.substring(0, 1)}
                  </div>
                  <span>{post.userName}</span>
                </div>
                <span className="flex items-center gap-1 text-primary font-semibold">
                  <MessageSquare size={14} />
                  댓글 {post.commentsCount}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* --- Detail Modal --- */}
      {selectedPost && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="bg-card border border-outline-variant rounded-2xl w-full max-w-2xl max-h-[85vh] flex flex-col shadow-2xl animate-in fade-in zoom-in-95 duration-200">
            {/* Modal Header */}
            <div className="flex justify-between items-center p-6 border-b border-outline-variant">
              <div className="flex items-center gap-2">
                <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                  selectedPost.assetId ? 'bg-indigo-500/10 text-indigo-400' : 'bg-emerald-500/10 text-emerald-400'
                }`}>
                  {selectedPost.assetId ? 'STO 토론' : '자유주제'}
                </span>
                {selectedPost.assetName && (
                  <span className="text-xs text-muted-foreground font-medium">
                    &gt; {selectedPost.assetName}
                  </span>
                )}
              </div>
              <button
                onClick={() => setSelectedPost(null)}
                className="p-1.5 rounded-lg hover:bg-outline-variant text-muted-foreground hover:text-foreground transition-all cursor-pointer"
              >
                <X size={20} />
              </button>
            </div>

            {/* Modal Content */}
            <div className="p-6 overflow-y-auto flex-1 flex flex-col gap-6">
              {/* Title & Author info */}
              <div>
                <h2 className="text-2xl font-bold mb-3">{selectedPost.title}</h2>
                <div className="flex justify-between items-center text-xs text-muted-foreground pb-4 border-b border-outline-variant">
                  <span className="flex items-center gap-1.5">
                    <User size={14} />
                    {selectedPost.userName}
                  </span>
                  <span>{new Date(selectedPost.createdAt).toLocaleString()}</span>
                </div>
              </div>

              {/* Main Body */}
              <p className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">
                {selectedPost.content}
              </p>

              {/* Author Action Panel */}
              {selectedPost.userId === currentUserId && (
                <div className="flex justify-end border-b border-outline-variant pb-4">
                  <button
                    onClick={() => handleDeletePost(selectedPost.id)}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-red-500/10 text-red-400 border border-red-500/20 hover:bg-red-500/20 transition-all cursor-pointer"
                  >
                    <Trash2 size={12} />
                    게시글 삭제
                  </button>
                </div>
              )}

              {/* Comment Section Header */}
              <div>
                <h3 className="text-base font-bold flex items-center gap-1.5 mb-4">
                  <MessageSquare size={16} className="text-primary" />
                  댓글 스레드 ({comments.length})
                </h3>

                {/* Comment Input Widget */}
                <form onSubmit={handleCreateComment} className="flex gap-2 mb-6">
                  <input
                    type="text"
                    placeholder="따뜻한 댓글을 남겨보세요..."
                    value={newComment}
                    onChange={e => setNewComment(e.target.value)}
                    className="flex-1 px-4 py-2.5 rounded-xl bg-background border border-outline-variant focus:outline-none focus:border-primary text-sm"
                  />
                  <button
                    type="submit"
                    disabled={commentLoading || !newComment.trim()}
                    className="px-4 py-2 rounded-xl bg-primary text-primary-foreground text-sm font-semibold hover:opacity-90 transition-all disabled:opacity-50 cursor-pointer"
                  >
                    등록
                  </button>
                </form>

                {/* Comment List */}
                <div className="flex flex-col gap-4">
                  {comments.length === 0 ? (
                    <p className="text-center py-6 text-xs text-muted-foreground">아직 작성된 댓글이 없습니다.</p>
                  ) : (
                    comments.map(c => (
                      <div key={c.id} className="p-4 rounded-xl bg-background border border-outline-variant flex justify-between items-start gap-4">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1.5">
                            <span className="text-xs font-bold">{c.userName}</span>
                            <span className="text-[10px] text-muted-foreground">{new Date(c.createdAt).toLocaleString()}</span>
                          </div>
                          <p className="text-sm text-foreground leading-relaxed">{c.content}</p>
                        </div>
                        {c.userId === currentUserId && (
                          <button
                            onClick={() => handleDeleteComment(c.id)}
                            className="p-1 rounded text-muted-foreground hover:text-red-400 transition-colors cursor-pointer"
                          >
                            <Trash2 size={14} />
                          </button>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}


    </div>
  )
}
