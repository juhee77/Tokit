"use client"

import React, { useState, useEffect, useCallback } from "react"
import { 
  Building2, 
  Users, 
  FileText, 
  UploadCloud, 
  TrendingUp, 
  PieChart as PieIcon, 
  Download, 
  Loader2, 
  CheckCircle2
} from "lucide-react"
import { toast } from "sonner"
import { fetchApi } from "@/lib/api"
import { cn } from "@/lib/utils"
import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer
} from "recharts"

interface IssuerAsset {
  id: number
  symbol: string
  name: string
  contractAddress: string
  totalSupply: number
  issuePrice: number
  status: string
  subscriptionProgress: number
  totalInvestors: number
}

interface Shareholder {
  name: string
  walletAddress: string
  balance: number
  shareRatio: number
}

interface AssetReport {
  id: number
  title: string
  filePath: string
  createdAt: string
}

const COLORS = ["#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#6366F1", "#14B8A6"]

export default function IssuerPage() {
  const [loading, setLoading] = useState(true)
  const [mounted, setMounted] = useState(false)
  const [activeTab, setActiveTab] = useState<"assets" | "reports">("assets")
  
  const [assets, setAssets] = useState<IssuerAsset[]>([])
  const [selectedAsset, setSelectedAsset] = useState<IssuerAsset | null>(null)
  const [shareholders, setShareholders] = useState<Shareholder[]>([])
  const [reports, setReports] = useState<AssetReport[]>([])
  
  const [uploadAssetId, setUploadAssetId] = useState("")
  const [reportTitle, setReportTitle] = useState("")
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [actionLoading, setActionLoading] = useState(false)

  const issuerId = 1 

  useEffect(() => {
    setMounted(true)
  }, [])

  const loadAssets = useCallback(async () => {
    try {
      const res = await fetchApi<IssuerAsset[]>(`/api/issuer/assets?issuerId=${issuerId}`)
      setAssets(res || [])
      if (res && res.length > 0 && !selectedAsset) {
        setSelectedAsset(res[0])
      }
    } catch (e: any) {
      console.error("Failed to load issuer assets:", e)
      toast.error("자산 목록 로드 실패: " + e.message)
    }
  }, [selectedAsset])

  const loadShareholders = useCallback(async (symbol: string) => {
    try {
      const res = await fetchApi<Shareholder[]>(`/api/issuer/assets/${symbol}/investors?issuerId=${issuerId}`)
      setShareholders(res || [])
    } catch (e: any) {
      console.error("Failed to load shareholders:", e)
    }
  }, [])

  const loadReports = useCallback(async (symbol: string) => {
    try {
      const res = await fetchApi<AssetReport[]>(`/api/issuer/assets/${symbol}/reports?issuerId=${issuerId}`)
      setReports(res || [])
    } catch (e: any) {
      console.error("Failed to load reports:", e)
    }
  }, [])

  const loadAll = useCallback(async () => {
    setLoading(true)
    await loadAssets()
    setLoading(false)
  }, [loadAssets])

  useEffect(() => {
    loadAll()
  }, [])

  useEffect(() => {
    if (selectedAsset) {
      loadShareholders(selectedAsset.symbol)
      loadReports(selectedAsset.symbol)
    }
  }, [selectedAsset, loadShareholders, loadReports])

  const handleUploadReport = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!uploadAssetId || !reportTitle || !uploadFile) {
      toast.error("모든 필드를 입력하고 파일을 선택해 주세요.")
      return
    }

    setActionLoading(true)
    const formData = new FormData()
    formData.append("assetId", uploadAssetId)
    formData.append("title", reportTitle)
    formData.append("file", uploadFile)
    formData.append("issuerId", issuerId.toString())

    try {
      const response = await fetch(`http://localhost:8080/api/issuer/reports`, {
        method: "POST",
        body: formData
      })
      const json = await response.json()
      if (!response.ok) throw new Error(json.message || "Failed to upload")

      toast.success("분기 보고서 공시가 성공적으로 등록되었습니다.")
      setReportTitle("")
      setUploadFile(null)
      
      const fileInput = document.getElementById("file-upload") as HTMLInputElement
      if (fileInput) fileInput.value = ""

      if (selectedAsset && selectedAsset.id === parseInt(uploadAssetId)) {
        loadReports(selectedAsset.symbol)
      }
    } catch (e: any) {
      console.error("Failed to upload report:", e)
      toast.error("보고서 업로드 실패: " + e.message)
    } finally {
      setActionLoading(false)
    }
  }

  const chartData = shareholders.map(s => ({
    name: s.name,
    value: s.shareRatio
  }))

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-slate-950 text-slate-400 gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-500" />
        <p className="text-sm font-semibold">발행사 포털 페이지 로딩 중...</p>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <header className="border-b border-slate-800 bg-slate-900/50 backdrop-blur px-8 py-5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Building2 className="h-7 w-7 text-emerald-500" />
          <div>
            <h1 className="text-xl font-bold tracking-tight">TOKIT Issuer Portal</h1>
            <p className="text-xs text-slate-400">발행사 자산 및 투자자 공시 관리 시스템</p>
          </div>
        </div>
        <div className="flex items-center gap-2 bg-slate-800/80 px-3 py-1.5 rounded-lg border border-slate-700">
          <div className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
          <span className="text-xs font-semibold text-slate-300">발행사 권한 (ID: 1)</span>
        </div>
      </header>

      <div className="flex-1 flex">
        <aside className="w-64 border-r border-slate-800 bg-slate-900/20 p-6 flex flex-col gap-2">
          <button
            onClick={() => setActiveTab("assets")}
            className={cn(
              "w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition text-left",
              activeTab === "assets"
                ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                : "text-slate-400 hover:bg-slate-800/40 hover:text-slate-200"
            )}
          >
            <TrendingUp className="h-4 w-4" />
            자사 발행 자산
          </button>
          <button
            onClick={() => setActiveTab("reports")}
            className={cn(
              "w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition text-left",
              activeTab === "reports"
                ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20"
                : "text-slate-400 hover:bg-slate-800/40 hover:text-slate-200"
            )}
          >
            <FileText className="h-4 w-4" />
            공시 보고서 업로드
          </button>
        </aside>

        <main className="flex-1 p-8">
          {activeTab === "assets" && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
              <div className="lg:col-span-1 flex flex-col gap-4">
                <h2 className="text-lg font-bold text-slate-300">발행 자산 목록</h2>
                <div className="flex flex-col gap-3">
                  {assets.map((asset) => (
                    <div
                      key={asset.id}
                      onClick={() => setSelectedAsset(asset)}
                      className={cn(
                        "p-5 rounded-2xl border transition cursor-pointer flex flex-col gap-3",
                        selectedAsset?.id === asset.id
                          ? "bg-slate-900 border-emerald-500/50 shadow-lg shadow-emerald-500/5"
                          : "bg-slate-900/40 border-slate-800 hover:border-slate-700"
                      )}
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-800 text-slate-300">
                          {asset.symbol}
                        </span>
                        <span className={cn(
                          "text-xs font-semibold px-2 py-0.5 rounded",
                          asset.status === "청약중" ? "bg-amber-500/10 text-amber-400" : "bg-emerald-500/10 text-emerald-400"
                        )}>
                          {asset.status}
                        </span>
                      </div>
                      <div>
                        <h3 className="font-bold text-slate-100 text-sm leading-tight">{asset.name}</h3>
                        <p className="text-xs text-slate-400 mt-1">발행 수량: {asset.totalSupply.toLocaleString()} STO</p>
                      </div>

                      <div className="flex flex-col gap-1 mt-2">
                        <div className="flex justify-between text-[11px] font-medium text-slate-400">
                          <span>청약 달성률</span>
                          <span>{asset.subscriptionProgress}%</span>
                        </div>
                        <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
                          <div 
                            className="h-full bg-emerald-500 rounded-full transition-all duration-500"
                            style={{ width: `${Math.min(asset.subscriptionProgress, 100)}%` }}
                          />
                        </div>
                      </div>

                      <div className="flex items-center justify-between text-xs text-slate-400 border-t border-slate-800 pt-3 mt-1">
                        <div className="flex items-center gap-1.5">
                          <Users className="h-3.5 w-3.5 text-slate-500" />
                          <span>주주: {asset.totalInvestors}명</span>
                        </div>
                        <span className="font-semibold text-slate-200">
                          {(asset.issuePrice).toLocaleString()} 원
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="lg:col-span-2 flex flex-col gap-6">
                {selectedAsset ? (
                  <>
                    <div className="bg-slate-900/60 border border-slate-800 p-6 rounded-3xl flex flex-col gap-6">
                      <div>
                        <h2 className="text-lg font-bold text-slate-200">{selectedAsset.name} 주주 지분 분포</h2>
                        <p className="text-xs text-slate-400 mt-1">자사 토큰증권을 보유 중인 주주 지분 분포 차트 및 순위</p>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center">
                        <div className="h-64 flex justify-center items-center">
                          {mounted && chartData.length > 0 ? (
                            <ResponsiveContainer width="100%" height="100%">
                              <PieChart>
                                <Pie
                                  data={chartData}
                                  cx="50%"
                                  cy="50%"
                                  innerRadius={60}
                                  outerRadius={80}
                                  paddingAngle={4}
                                  dataKey="value"
                                >
                                  {chartData.map((entry, index) => (
                                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                  ))}
                                </Pie>
                                <Tooltip 
                                  contentStyle={{ backgroundColor: "#1e293b", borderColor: "#334155", borderRadius: "12px", color: "#f8fafc" }} 
                                />
                              </PieChart>
                            </ResponsiveContainer>
                          ) : (
                            <div className="text-sm text-slate-500 flex flex-col items-center gap-2">
                              <PieIcon className="h-10 w-10 text-slate-700" />
                              <span>보유 주주 정보가 존재하지 않습니다.</span>
                            </div>
                          )}
                        </div>

                        <div className="flex flex-col gap-3">
                          <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">주주 지분 순위</h3>
                          <div className="flex flex-col gap-2 max-h-56 overflow-y-auto pr-1">
                            {shareholders.map((s, idx) => (
                              <div key={idx} className="flex items-center justify-between p-3 bg-slate-800/30 border border-slate-800/80 rounded-xl">
                                <div className="flex items-center gap-2.5">
                                  <div className="h-2 w-2 rounded-full" style={{ backgroundColor: COLORS[idx % COLORS.length] }} />
                                  <div>
                                    <p className="text-sm font-bold text-slate-200">{s.name}</p>
                                    <p className="text-[10px] text-slate-500 font-mono leading-none mt-0.5">{s.walletAddress}</p>
                                  </div>
                                </div>
                                <div className="text-right">
                                  <p className="text-sm font-bold text-slate-200">{s.balance.toLocaleString()} STO</p>
                                  <p className="text-xs font-semibold text-emerald-500">{s.shareRatio}%</p>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="bg-slate-900/30 border border-slate-800 p-6 rounded-3xl flex flex-col gap-4">
                      <h3 className="font-bold text-slate-300">공시 보고서 이력</h3>
                      {reports.length === 0 ? (
                        <p className="text-xs text-slate-500">등록된 공시 보고서가 없습니다.</p>
                      ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                          {reports.map((r) => (
                            <div key={r.id} className="p-4 bg-slate-900 border border-slate-800 rounded-2xl flex items-center justify-between">
                              <div className="flex items-center gap-3">
                                <FileText className="h-5 w-5 text-emerald-500" />
                                <div>
                                  <p className="text-sm font-semibold text-slate-200">{r.title}</p>
                                  <p className="text-[10px] text-slate-500 mt-0.5">등록일: {new Date(r.createdAt).toLocaleDateString()}</p>
                                </div>
                              </div>
                              <a
                                href={`http://localhost:8080${r.filePath}`}
                                download
                                className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 transition text-slate-300"
                              >
                                <Download className="h-4 w-4" />
                              </a>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </>
                ) : (
                  <div className="flex-1 flex flex-col items-center justify-center py-20 text-slate-500 border border-dashed border-slate-800 rounded-3xl">
                    <Building2 className="h-12 w-12 text-slate-700 mb-2" />
                    <span>선택된 자산이 없습니다.</span>
                  </div>
                )}
              </div>
            </div>
          )}

          {activeTab === "reports" && (
            <div className="max-w-2xl mx-auto flex flex-col gap-8">
              <div className="bg-slate-900 border border-slate-800 p-8 rounded-3xl flex flex-col gap-6">
                <div>
                  <h2 className="text-lg font-bold text-slate-200">분기 공시 보고서 업로드</h2>
                  <p className="text-xs text-slate-400 mt-1">자사 STO의 투자자 권익 보호를 위한 분기 실적 보고서 공시 등록</p>
                </div>

                <form onSubmit={handleUploadReport} className="flex flex-col gap-5">
                  <div className="flex flex-col gap-2">
                    <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">대상 자산 선택</label>
                    <select
                      value={uploadAssetId}
                      onChange={(e) => setUploadAssetId(e.target.value)}
                      className="w-full bg-slate-950 border border-slate-800 focus:border-emerald-500/50 rounded-xl px-4 py-3 text-sm text-slate-200 outline-none transition"
                    >
                      <option value="">-- 자산을 선택하세요 --</option>
                      {assets.map((asset) => (
                        <option key={asset.id} value={asset.id.toString()}>
                          {asset.name} ({asset.symbol})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="flex flex-col gap-2">
                    <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">보고서 제목</label>
                    <input
                      type="text"
                      placeholder="예: 2026년 3분기 빌딩 운용 실적 보고서"
                      value={reportTitle}
                      onChange={(e) => setReportTitle(e.target.value)}
                      className="w-full bg-slate-950 border border-slate-800 focus:border-emerald-500/50 rounded-xl px-4 py-3 text-sm text-slate-200 outline-none transition placeholder-slate-600"
                    />
                  </div>

                  <div className="flex flex-col gap-2">
                    <label className="text-xs font-bold text-slate-400 uppercase tracking-wider">PDF 파일 선택</label>
                    <div className="border-2 border-dashed border-slate-800 hover:border-slate-700 rounded-2xl p-8 flex flex-col items-center justify-center gap-3 transition bg-slate-950/30">
                      <UploadCloud className="h-10 w-10 text-slate-600" />
                      <div className="text-center">
                        <label htmlFor="file-upload" className="cursor-pointer text-sm font-semibold text-emerald-500 hover:underline">
                          파일 찾아보기
                        </label>
                        <p className="text-xs text-slate-500 mt-1">PDF 파일만 업로드 가능 (최대 10MB)</p>
                      </div>
                      <input
                        id="file-upload"
                        type="file"
                        accept=".pdf"
                        onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
                        className="hidden"
                      />
                      {uploadFile && (
                        <div className="flex items-center gap-2 mt-2 bg-slate-800/80 px-3 py-1.5 rounded-lg border border-slate-700">
                          <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                          <span className="text-xs font-medium text-slate-300">{uploadFile.name}</span>
                        </div>
                      )}
                    </div>
                  </div>

                  <button
                    type="submit"
                    disabled={actionLoading}
                    className="w-full py-4 rounded-2xl bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-bold transition flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed mt-2"
                  >
                    {actionLoading ? (
                      <Loader2 className="h-5 w-5 animate-spin" />
                    ) : (
                      <>
                        <CheckCircle2 className="h-5 w-5" />
                        공시 등록 완료
                      </>
                    )}
                  </button>
                </form>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  )
}
