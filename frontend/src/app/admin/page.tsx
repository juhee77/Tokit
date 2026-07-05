"use client"

import React, { useState, useEffect, useCallback } from "react"
import { 
  Building2, 
  ShieldAlert, 
  ShieldCheck, 
  Users, 
  Plus, 
  RefreshCw, 
  Activity, 
  TrendingUp, 
  UserPlus, 
  AlertTriangle,
  Loader2,
  CheckCircle2,
  Play
} from "lucide-react"
import { toast } from "sonner"
import { fetchApi } from "@/lib/api"
import { cn } from "@/lib/utils"

interface UserItem {
  id: number
  email: string
  name: string
  walletAddress: string
  kycStatus: boolean
}

interface ReconciliationLogItem {
  id: number
  userId: number
  userName: string
  assetId: number
  assetSymbol: string
  walletAddress: string
  offchainBalance: number
  onchainBalance: number
  difference: number
  checkedAt: string
}

interface AssetItem {
  id: number
  symbol: string
  name: string
  contractAddress: string
  totalSupply: number
  status: string
  issuePrice: number
  totalInvestors: number
}

interface DividendPayoutItem {
  id: number
  assetId: number
  assetSymbol: string
  assetName: string
  totalDividendAmount: number
  payoutDate: string
  status: string
}

interface DividendDetailItem {
  id: number
  userId: number
  userName: string
  walletAddress: string
  shareRatio: number
  payoutAmount: number
  status: string
  errorMessage: string
}

export default function AdminPage() {
  const [activeTab, setActiveTab] = useState<"assets" | "kyc" | "reconciliation" | "dividend">("assets")
  const [loading, setLoading] = useState<boolean>(true)
  
  // Data lists
  const [users, setUsers] = useState<UserItem[]>([])
  const [logs, setLogs] = useState<ReconciliationLogItem[]>([])
  const [assets, setAssets] = useState<AssetItem[]>([])
  const [dividends, setDividends] = useState<DividendPayoutItem[]>([])
  const [selectedPayoutId, setSelectedPayoutId] = useState<number | null>(null)
  const [payoutDetails, setPayoutDetails] = useState<DividendDetailItem[]>([])

  // Asset creation form state
  const [assetSymbol, setAssetSymbol] = useState("")
  const [assetName, setAssetName] = useState("")
  const [contractAddress, setContractAddress] = useState("")
  const [totalSupply, setTotalSupply] = useState("")
  const [issuePrice, setIssuePrice] = useState("")
  const [status, setStatus] = useState("청약중")
  const [actionLoading, setActionLoading] = useState(false)
  const [batchRunning, setBatchRunning] = useState(false)

  // Dividend form & detail state
  const [dividendAssetId, setDividendAssetId] = useState<string>("")
  const [dividendAmount, setDividendAmount] = useState<string>("")
  const [dividendActionLoading, setDividendActionLoading] = useState(false)
  const [detailsLoading, setDetailsLoading] = useState(false)

  // 1. Fetch Users List
  const loadUsers = useCallback(async () => {
    try {
      const res = await fetchApi<UserItem[]>("/api/users")
      setUsers(res || [])
    } catch (e: any) {
      console.error("Failed to load users:", e)
      toast.error("사용자 정보 조회 실패: " + e.message)
    }
  }, [])

  // 2. Fetch Reconciliation Logs
  const loadLogs = useCallback(async () => {
    try {
      const res = await fetchApi<ReconciliationLogItem[]>("/api/reconciliation/logs")
      setLogs(res || [])
    } catch (e: any) {
      console.error("Failed to load reconciliation logs:", e)
      toast.error("대사 로그 조회 실패: " + e.message)
    }
  }, [])

  // 3. Fetch Assets List
  const loadAssets = useCallback(async () => {
    try {
      const res = await fetchApi<AssetItem[]>("/api/assets")
      setAssets(res || [])
    } catch (e: any) {
      console.error("Failed to load assets:", e)
    }
  }, [])

  // 3.5 Fetch Dividend History List
  const loadDividends = useCallback(async () => {
    try {
      const res = await fetchApi<DividendPayoutItem[]>("/api/admin/dividends")
      setDividends(res || [])
    } catch (e: any) {
      console.error("Failed to load dividends:", e)
    }
  }, [])

  const loadPayoutDetails = async (payoutId: number) => {
    setDetailsLoading(true)
    setSelectedPayoutId(payoutId)
    try {
      const res = await fetchApi<DividendDetailItem[]>(`/api/admin/dividends/${payoutId}/details`)
      setPayoutDetails(res || [])
    } catch (e: any) {
      console.error("Failed to load payout details:", e)
      toast.error("상세 지급 로그 조회 실패: " + e.message)
    } finally {
      setDetailsLoading(false)
    }
  }

  const handleRunDividend = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!dividendAssetId || !dividendAmount) {
      toast.error("발행 자산 및 배당 재원을 올바르게 입력해 주세요.")
      return
    }

    setDividendActionLoading(true)
    try {
      const amount = parseFloat(dividendAmount.replace(/,/g, ""))
      toast.info("배당금 분배 배치를 실행합니다...")
      
      const res = await fetchApi<DividendPayoutItem>("/api/admin/dividends", {
        method: "POST",
        body: JSON.stringify({
          assetId: parseInt(dividendAssetId),
          totalDividendAmount: amount
        })
      })

      toast.success(`배당금 자동 지급 요청이 등록되었습니다. (상태: ${res.status})`)
      setDividendAmount("")
      setDividendAssetId("")
      
      // Reload dividend and user balance info
      await Promise.all([loadDividends(), loadUsers()])
    } catch (e: any) {
      console.error("Failed to run dividend distribution:", e)
      toast.error("배당금 지급 실행 실패: " + e.message)
    } finally {
      setDividendActionLoading(false)
    }
  }

  const loadAllData = useCallback(async () => {
    setLoading(true)
    await Promise.all([loadUsers(), loadLogs(), loadAssets(), loadDividends()])
    setLoading(false)
  }, [loadUsers, loadLogs, loadAssets, loadDividends])

  useEffect(() => {
    loadAllData()

    const apiBaseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'
    const eventSource = new EventSource(`${apiBaseUrl}/api/admin/alerts/subscribe`)

    eventSource.addEventListener("ALERT", (event) => {
      try {
        const data = JSON.parse(event.data)
        toast.error(
          React.createElement("div", { className: "flex flex-col gap-1 text-left" },
            React.createElement("span", { className: "font-bold text-red-600" }, data.title),
            React.createElement("span", { className: "text-xs text-slate-600 whitespace-pre-wrap" }, data.message)
          ),
          {
            duration: 10000,
          }
        )
        loadLogs()
        loadUsers()
      } catch (e) {
        console.error("Failed to parse SSE alert data:", e)
      }
    })

    eventSource.onerror = (err) => {
      console.error("SSE connection error:", err)
      eventSource.close()
    }

    return () => {
      eventSource.close()
    }
  }, [loadAllData, loadLogs, loadUsers])

  // 4. KYC Status Toggle Handler
  const handleKycToggle = async (userId: number, currentKyc: boolean) => {
    const nextKyc = !currentKyc
    try {
      toast.info(`KYC 상태를 ${nextKyc ? "인증 완료" : "미인증"} 상태로 변경 중...`)
      await fetchApi(`/api/users/${userId}/kyc?kycStatus=${nextKyc}`, {
        method: "PUT"
      })
      toast.success("KYC 및 온체인 화이트리스트 상태가 변경되었습니다.")
      loadUsers()
    } catch (e: any) {
      console.error("Failed to toggle KYC:", e)
      toast.error("KYC 변경 실패: " + e.message)
    }
  }

  // 5. STO Asset Registration Form Submit Handler
  const handleRegisterAsset = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!assetSymbol || !assetName || !contractAddress || !totalSupply || !issuePrice) {
      toast.error("모든 필드를 올바르게 입력해 주세요.")
      return
    }

    setActionLoading(true)
    try {
      await fetchApi("/api/assets", {
        method: "POST",
        body: JSON.stringify({
          symbol: assetSymbol,
          name: assetName,
          contractAddress: contractAddress,
          totalSupply: parseFloat(totalSupply.replace(/,/g, "")),
          issuePrice: parseFloat(issuePrice.replace(/,/g, "")),
          status: status
        })
      })
      
      toast.success(`신규 STO 상품 ${assetSymbol}이 성공적으로 등록되었습니다.`)
      // Clear form
      setAssetSymbol("")
      setAssetName("")
      setContractAddress("")
      setTotalSupply("")
      setIssuePrice("")
      setStatus("청약중")
      
      loadAssets()
    } catch (e: any) {
      console.error("Failed to register asset:", e)
      toast.error("STO 자산 등록 실패: " + e.message)
    } finally {
      setActionLoading(false)
    }
  }

  // 6. Manual Run Reconciliation Batch Handler
  const handleRunBatch = async () => {
    setBatchRunning(true)
    try {
      toast.info("온-오프체인 데이터 정합성 대사 배치를 실행합니다...")
      const res = await fetchApi<any>("/api/reconciliation/run", {
        method: "POST"
      })
      toast.success(`대사 배치가 성공적으로 완료되었습니다. (상태: ${res.status})`)
      await loadLogs()
    } catch (e: any) {
      console.error("Failed to trigger reconciliation batch:", e)
      toast.error("대사 배치 실행 실패: " + e.message)
    } finally {
      setBatchRunning(false)
    }
  }

  const formatKRW = (value: number) => {
    return `${Math.floor(value).toLocaleString("ko-KR")} 원`
  }

  const formatDateTime = (dateStr: string) => {
    try {
      const d = new Date(dateStr)
      return d.toLocaleString("ko-KR")
    } catch {
      return dateStr
    }
  }

  if (loading && assets.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center py-20 text-slate-400 gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-secondary" />
        <p className="text-sm font-semibold">어드민 백오피스 정보 로딩 중...</p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center bg-card border border-outline-variant p-4 rounded shadow-sm">
        <div>
          <h2 className="text-headline-md font-bold text-foreground">어드민 통제 본부 (Admin Backoffice)</h2>
          <p className="text-xs text-muted-foreground mt-1">신규 STO 발행 등록, KYC 강제 변경 및 온-오프체인 실시간 잔고 정합성을 관리합니다.</p>
        </div>
        <button 
          onClick={loadAllData}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border border-outline-variant rounded bg-surface hover:border-secondary hover:text-secondary transition-colors"
          disabled={loading}
        >
          <RefreshCw className={cn("w-3.5 h-3.5", loading && "animate-spin")} />
          새로고침
        </button>
      </div>

      {/* Tabs */}
      <div className="bg-card border border-outline-variant rounded shadow-sm overflow-hidden">
        <div className="flex border-b border-outline-variant bg-surface">
          <button 
            onClick={() => setActiveTab("assets")}
            className={cn(
              "flex-1 px-4 py-3 text-sm font-semibold border-b-2 transition-all flex items-center justify-center gap-2",
              activeTab === "assets"
                ? "border-secondary text-secondary bg-surface-container-low"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            <Building2 className="w-4 h-4" />
            STO 자산 관리 ({assets.length})
          </button>
          <button 
            onClick={() => setActiveTab("kyc")}
            className={cn(
              "flex-1 px-4 py-3 text-sm font-semibold border-b-2 transition-all flex items-center justify-center gap-2",
              activeTab === "kyc"
                ? "border-secondary text-secondary bg-surface-container-low"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            <Users className="w-4 h-4" />
            KYC 및 회원 관리 ({users.length})
          </button>
          <button 
            onClick={() => setActiveTab("reconciliation")}
            className={cn(
              "flex-1 px-4 py-3 text-sm font-semibold border-b-2 transition-all flex items-center justify-center gap-2",
              activeTab === "reconciliation"
                ? "border-secondary text-secondary bg-surface-container-low"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            <AlertTriangle className="w-4 h-4" />
            정합성 대사 감사 ({logs.length})
          </button>
          <button 
            onClick={() => {
              setActiveTab("dividend")
              setSelectedPayoutId(null)
            }}
            className={cn(
              "flex-1 px-4 py-3 text-sm font-semibold border-b-2 transition-all flex items-center justify-center gap-2",
              activeTab === "dividend"
                ? "border-secondary text-secondary bg-surface-container-low"
                : "border-transparent text-muted-foreground hover:text-foreground"
            )}
          >
            <TrendingUp className="w-4 h-4" />
            배당금 지급 관리 ({dividends.length})
          </button>
        </div>

        {/* Tab 1: Asset Management & Form */}
        {activeTab === "assets" && (
          <div className="p-4 md:p-6 grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Form */}
            <div className="lg:col-span-5 bg-surface-container-low border border-outline-variant p-5 rounded space-y-4 shadow-inner">
              <div className="flex items-center gap-1.5 border-b border-outline-variant/60 pb-2 mb-3">
                <Plus className="w-5 h-5 text-secondary" />
                <h3 className="font-semibold text-sm text-foreground">신규 STO 상품 등록</h3>
              </div>
              
              <form onSubmit={handleRegisterAsset} className="space-y-3.5">
                <div>
                  <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-1">토큰 심볼 (Symbol)</label>
                  <input 
                    type="text" 
                    placeholder="예: GNPM, HDYT"
                    value={assetSymbol}
                    onChange={(e) => setAssetSymbol(e.target.value.toUpperCase())}
                    className="w-full px-3 py-2 bg-surface border border-outline-variant rounded text-sm text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20"
                    required
                  />
                </div>
                <div>
                  <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-1">자산명 (Asset Name)</label>
                  <input 
                    type="text" 
                    placeholder="예: 서울 강남 프라임 오피스 빌딩"
                    value={assetName}
                    onChange={(e) => setAssetName(e.target.value)}
                    className="w-full px-3 py-2 bg-surface border border-outline-variant rounded text-sm text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20"
                    required
                  />
                </div>
                <div>
                  <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-1">블록체인 스마트 컨트랙트 주소</label>
                  <input 
                    type="text" 
                    placeholder="예: 0x5FbDB2315678afecb367f032d93F642f64180aa3"
                    value={contractAddress}
                    onChange={(e) => setContractAddress(e.target.value)}
                    className="w-full px-3 py-2 bg-surface border border-outline-variant rounded text-sm font-mono text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20"
                    required
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-1">총 공급량 (주)</label>
                    <input 
                      type="text" 
                      placeholder="예: 5,000,000"
                      value={totalSupply}
                      onChange={(e) => setTotalSupply(e.target.value.replace(/[^0-9]/g, "").replace(/\B(?=(\d{3})+(?!\d))/g, ","))}
                      className="w-full px-3 py-2 bg-surface border border-outline-variant rounded text-sm text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20 font-mono"
                      required
                    />
                  </div>
                  <div>
                    <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-1">액면가 / 공모가 (원)</label>
                    <input 
                      type="text" 
                      placeholder="예: 10,000"
                      value={issuePrice}
                      onChange={(e) => setIssuePrice(e.target.value.replace(/[^0-9]/g, "").replace(/\B(?=(\d{3})+(?!\d))/g, ","))}
                      className="w-full px-3 py-2 bg-surface border border-outline-variant rounded text-sm text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20 font-mono"
                      required
                    />
                  </div>
                </div>
                <div>
                  <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-1">초기 자산 상태 (Status)</label>
                  <select 
                    value={status}
                    onChange={(e) => setStatus(e.target.value)}
                    className="w-full px-3 py-2 bg-surface border border-outline-variant rounded text-sm text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20"
                  >
                    <option value="청약중">청약중 (Primary offering)</option>
                    <option value="거래중">거래중 (Secondary market)</option>
                    <option value="종료">종료 (Ended)</option>
                  </select>
                </div>
                
                <button 
                  type="submit"
                  className="w-full mt-2 bg-primary text-primary-foreground flex items-center justify-center py-2.5 px-4 rounded text-sm font-semibold hover:bg-primary/90 transition-colors disabled:opacity-50"
                  disabled={actionLoading}
                >
                  {actionLoading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Plus className="w-4 h-4 mr-2" />}
                  신규 자산 등록 승인
                </button>
              </form>
            </div>

            {/* List */}
            <div className="lg:col-span-7 border border-outline-variant rounded overflow-hidden">
              <div className="bg-surface-container-low px-4 py-3 border-b border-outline-variant flex justify-between items-center">
                <h4 className="font-semibold text-sm text-foreground">플랫폼 활성 자산 목록</h4>
                <span className="text-[10px] bg-secondary/10 text-secondary border border-secondary/20 px-2 py-0.5 rounded font-bold font-mono">
                  {assets.length} Assets
                </span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[500px]">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface text-label-caps text-muted-foreground text-xs">
                      <th className="py-3 px-4 font-semibold">자산명 / 심볼</th>
                      <th className="py-3 px-4 font-semibold text-right">총 공급량</th>
                      <th className="py-3 px-4 font-semibold text-right">공모가</th>
                      <th className="py-3 px-4 font-semibold text-center">상태</th>
                    </tr>
                  </thead>
                  <tbody className="text-sm divide-y divide-outline-variant/60">
                    {assets.map((asset) => (
                      <tr key={asset.id} className="hover:bg-surface-container-low transition-colors">
                        <td className="py-3.5 px-4">
                          <p className="font-semibold text-foreground">{asset.name}</p>
                          <code className="text-xs text-muted-foreground font-mono">{asset.symbol} • {asset.contractAddress.slice(0, 8)}...{asset.contractAddress.slice(-6)}</code>
                        </td>
                        <td className="py-3.5 px-4 text-right font-mono text-foreground">{asset.totalSupply.toLocaleString()} 주</td>
                        <td className="py-3.5 px-4 text-right font-mono text-foreground">{asset.issuePrice.toLocaleString()} 원</td>
                        <td className="py-3.5 px-4 text-center">
                          <span className={cn(
                            "px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border",
                            asset.status === "청약중"
                              ? "border-warning text-warning bg-warning/10"
                              : asset.status === "거래중"
                              ? "border-secondary text-secondary bg-secondary/10"
                              : "border-outline text-muted-foreground bg-surface-container"
                          )}>
                            {asset.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: KYC & User Control */}
        {activeTab === "kyc" && (
          <div className="p-4 md:p-6">
            <div className="border border-outline-variant rounded overflow-hidden">
              <div className="bg-surface-container-low px-4 py-3 border-b border-outline-variant flex justify-between items-center">
                <h4 className="font-semibold text-sm text-foreground">투자 회원 리스트 및 KYC 통제</h4>
                <span className="text-[10px] bg-secondary/10 text-secondary border border-secondary/20 px-2 py-0.5 rounded font-bold font-mono">
                  {users.length} Users
                </span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[700px]">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface text-label-caps text-muted-foreground text-xs">
                      <th className="py-3 px-4 font-semibold">고객 ID</th>
                      <th className="py-3 px-4 font-semibold">이름 (이메일)</th>
                      <th className="py-3 px-4 font-semibold">지갑 주소 (온체인)</th>
                      <th className="py-3 px-4 font-semibold text-center">KYC 신원인증</th>
                      <th className="py-3 px-4 font-semibold text-center">관리 조치</th>
                    </tr>
                  </thead>
                  <tbody className="text-sm divide-y divide-outline-variant/60">
                    {users.map((u) => (
                      <tr key={u.id} className="hover:bg-surface-container-low transition-colors">
                        <td className="py-3.5 px-4 font-mono text-muted-foreground">{u.id}</td>
                        <td className="py-3.5 px-4">
                          <p className="font-semibold text-foreground">{u.name}</p>
                          <p className="text-xs text-muted-foreground">{u.email}</p>
                        </td>
                        <td className="py-3.5 px-4">
                          <code className="text-xs font-mono text-foreground select-all bg-surface-container px-2 py-0.5 rounded border border-outline-variant/50">
                            {u.walletAddress}
                          </code>
                        </td>
                        <td className="py-3.5 px-4 text-center">
                          <div className="flex items-center justify-center gap-1">
                            {u.kycStatus ? (
                              <span className="text-green-500 flex items-center text-xs font-semibold bg-green-500/10 border border-green-500/20 px-2.5 py-0.5 rounded-full">
                                <ShieldCheck className="w-3.5 h-3.5 mr-1" />
                                인증 완료 (Whitelist)
                              </span>
                            ) : (
                              <span className="text-warning flex items-center text-xs font-semibold bg-warning/10 border border-warning/20 px-2.5 py-0.5 rounded-full">
                                <ShieldAlert className="w-3.5 h-3.5 mr-1" />
                                미인증 (Restricted)
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="py-3.5 px-4 text-center">
                          <button
                            onClick={() => handleKycToggle(u.id, u.kycStatus)}
                            className={cn(
                              "px-3 py-1 text-xs font-bold rounded border transition-colors inline-flex items-center justify-center",
                              u.kycStatus
                                ? "border-warning/50 text-warning hover:bg-warning/5"
                                : "border-green-600/50 text-green-600 hover:bg-green-50"
                            )}
                          >
                            {u.kycStatus ? "인증 취소" : "KYC 통과"}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Tab 3: On-Off Chain Reconciliation Audit */}
        {activeTab === "reconciliation" && (
          <div className="p-4 md:p-6 space-y-6">
            {/* Batch execution controls */}
            <div className="bg-surface-container-low border border-outline-variant p-4 rounded flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
              <div className="space-y-1">
                <h4 className="font-semibold text-foreground flex items-center gap-1.5">
                  <Play className="w-4 h-4 text-secondary" />
                  온-오프체인 데이터 정합성 대사 (Daily Reconciliation Audit Batch)
                </h4>
                <p className="text-xs text-muted-foreground">
                  PostgreSQL 오프체인 장부와 Hardhat 블록체인 온체인 지갑의 실시간 ERC-1400 토큰 잔액을 대조하고 오류가 있을 시 로그를 생성합니다.
                </p>
              </div>
              
              <button 
                onClick={handleRunBatch}
                className="flex items-center gap-2 bg-secondary text-secondary-foreground hover:bg-secondary/90 font-bold text-xs py-2.5 px-4 rounded shadow transition-all disabled:opacity-50 flex-shrink-0"
                disabled={batchRunning}
              >
                {batchRunning ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
                대사 작업 강제 실행 (Manual Audit Launch)
              </button>
            </div>

            {/* Audit Logs Table */}
            <div className="border border-outline-variant rounded overflow-hidden">
              <div className="bg-surface-container-low px-4 py-3 border-b border-outline-variant flex justify-between items-center">
                <h4 className="font-semibold text-sm text-foreground flex items-center gap-1.5">
                  <ShieldAlert className="w-4 h-4 text-warning" />
                  정합성 검증 불일치 감사 로그 이력
                </h4>
                <span className="text-[10px] bg-red-500/10 text-red-500 border border-red-500/20 px-2 py-0.5 rounded font-bold font-mono">
                  {logs.length} Errors detected
                </span>
              </div>
              
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[800px]">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface text-label-caps text-muted-foreground text-xs">
                      <th className="py-3 px-4 font-semibold">검사 일시</th>
                      <th className="py-3 px-4 font-semibold">해당 사용자</th>
                      <th className="py-3 px-4 font-semibold">자산 심볼</th>
                      <th className="py-3 px-4 font-semibold text-right">오프체인 잔고 (DB)</th>
                      <th className="py-3 px-4 font-semibold text-right">온체인 잔고 (Block)</th>
                      <th className="py-3 px-4 font-semibold text-right">오차량 (Diff)</th>
                      <th className="py-3 px-4 font-semibold text-center">조치 상태</th>
                    </tr>
                  </thead>
                  <tbody className="text-sm divide-y divide-outline-variant/60">
                    {logs.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="py-10 text-center text-muted-foreground">
                          <CheckCircle2 className="w-8 h-8 text-green-500 mx-auto mb-2 opacity-80" />
                          <p className="font-semibold text-foreground text-sm">원장 대사 정합성 100% 확보 완료</p>
                          <p className="text-xs text-muted-foreground mt-1">블록체인 지갑과 RDBMS 데이터간 불일치 사항이 없습니다.</p>
                        </td>
                      </tr>
                    ) : (
                      logs.map((logEntry) => (
                        <tr key={logEntry.id} className="hover:bg-red-500/[0.02] transition-colors">
                          <td className="py-3 px-4 text-xs text-muted-foreground font-mono">{formatDateTime(logEntry.checkedAt)}</td>
                          <td className="py-3 px-4 font-semibold text-foreground">{logEntry.userName} <span className="text-xs text-muted-foreground font-normal">(ID: {logEntry.userId})</span></td>
                          <td className="py-3 px-4 font-mono font-bold text-foreground">{logEntry.assetSymbol}</td>
                          <td className="py-3 px-4 text-right font-mono text-foreground">{logEntry.offchainBalance.toLocaleString()}</td>
                          <td className="py-3 px-4 text-right font-mono text-foreground">{logEntry.onchainBalance.toLocaleString()}</td>
                          <td className="py-3 px-4 text-right font-mono text-destructive font-bold">-{logEntry.difference.toLocaleString()}</td>
                          <td className="py-3 px-4 text-center">
                            <span className="px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border border-red-500/20 text-red-500 bg-red-500/10">
                              ALERT SENT
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Tab 4: Dividend Payout Management */}
        {activeTab === "dividend" && (
          <div className="p-4 md:p-6 grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Form */}
            <div className="lg:col-span-4 bg-surface-container-low border border-outline-variant p-5 rounded space-y-4 shadow-inner">
              <div className="flex items-center gap-1.5 border-b border-outline-variant/60 pb-2 mb-3">
                <TrendingUp className="w-5 h-5 text-secondary" />
                <h3 className="font-semibold text-sm text-foreground">배당금 분배 실행</h3>
              </div>
              
              <form onSubmit={handleRunDividend} className="space-y-4">
                <div>
                  <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-1">대상 STO 자산</label>
                  <select 
                    value={dividendAssetId}
                    onChange={(e) => setDividendAssetId(e.target.value)}
                    className="w-full px-3 py-2 bg-surface border border-outline-variant rounded text-sm text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20"
                    required
                  >
                    <option value="">선택해 주세요...</option>
                    {assets.map((asset) => (
                      <option key={asset.id} value={asset.id}>
                        {asset.name} ({asset.symbol})
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider block mb-1">총 배당 재원 (원화 KRW)</label>
                  <div className="relative">
                    <input 
                      type="text" 
                      placeholder="예: 10,000,000"
                      value={dividendAmount}
                      onChange={(e) => setDividendAmount(e.target.value.replace(/[^0-9]/g, "").replace(/\B(?=(\d{3})+(?!\d))/g, ","))}
                      className="w-full pl-3 pr-10 py-2 bg-surface border border-outline-variant rounded text-sm text-foreground focus:outline-none focus:border-secondary focus:ring-1 focus:ring-secondary/20 font-mono"
                      required
                    />
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-bold text-muted-foreground">KRW</span>
                  </div>
                </div>
                
                <button 
                  type="submit"
                  className="w-full mt-2 bg-primary text-primary-foreground flex items-center justify-center py-2.5 px-4 rounded text-sm font-semibold hover:bg-primary/90 transition-colors disabled:opacity-50"
                  disabled={dividendActionLoading}
                >
                  {dividendActionLoading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Play className="w-4 h-4 mr-2" />}
                  배당 자동 분배 시작
                </button>
              </form>
            </div>

            {/* List and Details */}
            <div className="lg:col-span-8 space-y-6">
              {/* Dividend History Table */}
              <div className="border border-outline-variant rounded overflow-hidden">
                <div className="bg-surface-container-low px-4 py-3 border-b border-outline-variant flex justify-between items-center">
                  <h4 className="font-semibold text-sm text-foreground">배당금 지급 이력</h4>
                  <span className="text-[10px] bg-secondary/10 text-secondary border border-secondary/20 px-2 py-0.5 rounded font-bold font-mono">
                    {dividends.length} Records
                  </span>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse min-w-[500px]">
                    <thead>
                      <tr className="border-b border-outline-variant bg-surface text-label-caps text-muted-foreground text-xs">
                        <th className="py-3 px-4 font-semibold">지급일</th>
                        <th className="py-3 px-4 font-semibold">자산명 / 심볼</th>
                        <th className="py-3 px-4 font-semibold text-right">총 배당액</th>
                        <th className="py-3 px-4 font-semibold text-center">상태</th>
                        <th className="py-3 px-4 font-semibold text-center">작업</th>
                      </tr>
                    </thead>
                    <tbody className="text-sm divide-y divide-outline-variant/60">
                      {dividends.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="py-10 text-center text-muted-foreground">
                            배당금 지급 이력이 존재하지 않습니다.
                          </td>
                        </tr>
                      ) : (
                        dividends.map((div) => (
                          <tr key={div.id} className={cn("hover:bg-surface-container-low transition-colors", selectedPayoutId === div.id && "bg-secondary/[0.03]")}>
                            <td className="py-3 px-4 text-xs text-muted-foreground font-mono">{formatDateTime(div.payoutDate)}</td>
                            <td className="py-3 px-4">
                              <p className="font-semibold text-foreground">{div.assetName}</p>
                              <code className="text-xs text-muted-foreground font-mono">{div.assetSymbol}</code>
                            </td>
                            <td className="py-3 px-4 text-right font-mono text-foreground font-bold">{formatKRW(div.totalDividendAmount)}</td>
                            <td className="py-3 px-4 text-center">
                              <span className={cn(
                                "px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border",
                                div.status === "COMPLETED"
                                  ? "border-green-500 text-green-500 bg-green-500/10"
                                  : div.status === "PENDING" || div.status === "PROCESSING"
                                  ? "border-warning text-warning bg-warning/10"
                                  : "border-red-500 text-red-500 bg-red-500/10"
                              )}>
                                {div.status}
                              </span>
                            </td>
                            <td className="py-3 px-4 text-center">
                              <button
                                onClick={() => loadPayoutDetails(div.id)}
                                className="px-2 py-1 text-xs border border-outline hover:border-secondary hover:text-secondary rounded transition-colors"
                              >
                                지급상세
                              </button>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Selected Payout Detail Logs */}
              {selectedPayoutId && (
                <div className="border border-outline-variant rounded overflow-hidden">
                  <div className="bg-surface-container-low px-4 py-3 border-b border-outline-variant flex justify-between items-center">
                    <h4 className="font-semibold text-sm text-foreground">
                      지급 상세 로그 (배당 ID: {selectedPayoutId})
                    </h4>
                    <button 
                      onClick={() => setSelectedPayoutId(null)}
                      className="text-xs text-muted-foreground hover:text-foreground font-semibold"
                    >
                      상세 닫기
                    </button>
                  </div>
                  {detailsLoading ? (
                    <div className="py-10 text-center text-muted-foreground flex flex-col items-center gap-2">
                      <Loader2 className="w-6 h-6 animate-spin text-secondary" />
                      <span className="text-xs">상세 지급 상태 로딩 중...</span>
                    </div>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-left border-collapse min-w-[600px]">
                        <thead>
                          <tr className="border-b border-outline-variant bg-surface text-label-caps text-muted-foreground text-xs">
                            <th className="py-2.5 px-4 font-semibold">주주명</th>
                            <th className="py-2.5 px-4 font-semibold">지갑 주소</th>
                            <th className="py-2.5 px-4 font-semibold text-right">지분 비율</th>
                            <th className="py-2.5 px-4 font-semibold text-right">지급액 (KRW)</th>
                            <th className="py-2.5 px-4 font-semibold text-center">지급 상태</th>
                          </tr>
                        </thead>
                        <tbody className="text-xs divide-y divide-outline-variant/60">
                          {payoutDetails.length === 0 ? (
                            <tr>
                              <td colSpan={5} className="py-6 text-center text-muted-foreground">
                                상세 주주 지급 내역이 존재하지 않거나 처리 전입니다.
                              </td>
                            </tr>
                          ) : (
                            payoutDetails.map((det) => (
                              <tr key={det.id} className="hover:bg-surface-container-low transition-colors">
                                <td className="py-2 px-4 font-semibold text-foreground">{det.userName}</td>
                                <td className="py-2 px-4 font-mono text-muted-foreground">{det.walletAddress.slice(0, 10)}...{det.walletAddress.slice(-8)}</td>
                                <td className="py-2 px-4 text-right font-mono text-foreground">{(det.shareRatio * 100).toFixed(4)}%</td>
                                <td className="py-2 px-4 text-right font-mono text-foreground font-semibold">{det.payoutAmount.toLocaleString()} 원</td>
                                <td className="py-2 px-4 text-center">
                                  <span className={cn(
                                    "px-1.5 py-0.5 text-[9px] font-bold rounded border",
                                    det.status === "SUCCESS"
                                      ? "border-green-500/30 text-green-500 bg-green-500/5"
                                      : det.status === "PENDING"
                                      ? "border-warning/30 text-warning bg-warning/5"
                                      : "border-red-500/30 text-red-500 bg-red-500/5"
                                  )}>
                                    {det.status}
                                  </span>
                                </td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
