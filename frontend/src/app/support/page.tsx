"use client"

import { useState, useMemo } from "react"
import { 
  HelpCircle, 
  Search, 
  ChevronDown, 
  ChevronUp, 
  Building, 
  User, 
  Mail, 
  FileText, 
  MessageSquare,
  ArrowRight,
  CheckCircle2
} from "lucide-react"

interface FAQItem {
  id: string
  category: "issuance" | "account" | "trading" | "compliance"
  question: string
  answer: string
}

const FAQ_DATA: FAQItem[] = [
  {
    id: "faq-1",
    category: "issuance",
    question: "조각투자 자산의 토큰증권(STO) 발행 절차는 어떻게 되나요?",
    answer: "신규 STO 상품을 발행하려면 먼저 기초자산 감정평가 및 금융감독원 증권신고서 수리를 완료해야 합니다. 승인 후 어드민 통제 본부를 통해 자산명, 총 공급량, 공모가를 입력하여 ERC-1400 규격의 온체인 토큰 스마트 컨트랙트를 배포 및 활성화하게 됩니다."
  },
  {
    id: "faq-2",
    category: "account",
    question: "원화(KRW) 예치금 입출금 및 홀딩(Locked) 처리는 어떤 규칙을 따르나요?",
    answer: "TOKIT 플랫폼은 원화(KRW) 자산을 오프체인 RDBMS 원장과 분산락(Distributed Lock)으로 철저히 보호합니다. 지정가 매수 주문을 접수하면 해당 금액만큼 'Locked Balance'로 즉시 이체되며, 주문 체결 시 최종 차감되고 주문 취소 시 실시간으로 가용 잔고(Available)로 복원됩니다."
  },
  {
    id: "faq-3",
    category: "trading",
    question: "유통 시장(Secondary Market) 거래 시 체결 수수료가 있나요?",
    answer: "기관 투자자 파트너의 초기 활성화를 위해 현재 거래 수수료는 기본 0.05%가 적용되며, 마켓 메이커 및 대량 유동성 공급자는 별도의 협약을 통해 수수료 할인 요율을 우대 적용받으실 수 있습니다."
  },
  {
    id: "faq-4",
    category: "compliance",
    question: "KYC(신원인증) 승인을 받아야만 거래를 진행할 수 있나요?",
    answer: "네, 금융 규제 및 블록체인 거래소 컴플라이언스(Compliance) 준수 규정에 따라 모든 참여 지갑 주소는 사전에 whitelist에 등록되어야 합니다. whitelist에 등록되지 않은 주소는 스마트 컨트랙트 상의 'transferByPartition' 전송 검증 단계에서 거래가 즉각 차단됩니다."
  },
  {
    id: "faq-5",
    category: "issuance",
    question: "발행할 수 있는 기초자산의 종류에는 어떤 것들이 있나요?",
    answer: "현재 상업용 부동산(오피스 빌딩, 호텔 등), 미술품 지분권, 웹툰 및 음원 저작권 신탁 수익증권, 그리고 선박/항공기 금융 지분증권 등 다양한 실물자산(RWA) 및 무형 수익권을 토큰화하여 유통할 수 있습니다."
  },
  {
    id: "faq-6",
    category: "compliance",
    question: "온-오프체인 데이터 불일치가 발생할 경우 어떻게 조치하나요?",
    answer: "매일 새벽 3시에 'Daily Reconciliation Batch' 작업이 실행되어 오프체인 RDBMS 장부와 블록체인 온체인 잔고(ERC-1400)를 교차 검증합니다. 만약 1원이라도 불일치가 감지되면 즉시 데이터 정합성 대사 로그 테이블에 경고 로그가 생성되고 관리자 시스템으로 알림이 전송되어 수동 복구 모드가 활성화됩니다."
  }
]

const categories = [
  { id: "all", label: "전체 FAQ" },
  { id: "issuance", label: "STO 발행 및 공모" },
  { id: "account", label: "계좌 및 원화 잔고" },
  { id: "trading", label: "거래소 이용 가이드" },
  { id: "compliance", label: "법률 및 컴플라이언스" }
]

export default function SupportPage() {
  const [searchQuery, setSearchQuery] = useState("")
  const [activeCategory, setActiveCategory] = useState("all")
  const [expandedFaqId, setExpandedFaqId] = useState<string | null>(null)

  // Inquiry form states
  const [company, setCompany] = useState("")
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [inquiryType, setInquiryType] = useState("issuance_inquiry")
  const [message, setMessage] = useState("")
  const [submitted, setSubmitted] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  // Toggle FAQ accordion
  const toggleFaq = (id: string) => {
    if (expandedFaqId === id) {
      setExpandedFaqId(null)
    } else {
      setExpandedFaqId(id)
    }
  }

  // Filter FAQs
  const filteredFAQs = useMemo(() => {
    return FAQ_DATA.filter(faq => {
      const matchesCategory = activeCategory === "all" || faq.category === activeCategory
      const matchesSearch = searchQuery === "" || 
        faq.question.toLowerCase().includes(searchQuery.toLowerCase()) ||
        faq.answer.toLowerCase().includes(searchQuery.toLowerCase())
      return matchesCategory && matchesSearch
    })
  }, [searchQuery, activeCategory])

  // Handle mock submission
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!company || !name || !email || !message) return

    setSubmitting(true)
    setTimeout(() => {
      setSubmitting(false)
      setSubmitted(true)
      // reset states
      setCompany("")
      setName("")
      setEmail("")
      setMessage("")
    }, 1200)
  }

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* Search Header Banner */}
      <div className="relative rounded-2xl overflow-hidden border border-outline-variant/60 bg-gradient-to-r from-surface-container via-surface/40 to-surface-container p-6 sm:p-10 flex flex-col items-center text-center space-y-4 shadow-sm">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(63,131,248,0.06),transparent_40%)]" />
        <div className="inline-flex p-3 rounded-full bg-secondary/10 text-secondary border border-secondary/20">
          <HelpCircle className="w-6 h-6 animate-pulse" />
        </div>
        <div className="space-y-1">
          <h2 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">TOKIT 고객지원 센터</h2>
          <p className="text-sm text-muted-foreground max-w-lg mx-auto">
            토큰증권(STO) 발행, 매칭 엔진 연동, 계좌 관리 및 컴플라이언스에 관한 자주 묻는 질문을 확인하세요.
          </p>
        </div>

        {/* Search Bar */}
        <div className="relative w-full max-w-lg mt-2">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="궁금한 내용을 검색창에 입력해보세요..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 bg-surface/80 hover:bg-surface border border-outline-variant rounded-lg text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-secondary/50 focus:border-secondary transition-all shadow-inner"
          />
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left/Center Columns: FAQ Accordion */}
        <div className="lg:col-span-2 space-y-4">
          {/* Category Tabs */}
          <div className="flex flex-wrap gap-1.5 border-b border-outline-variant/40 pb-2">
            {categories.map(cat => (
              <button
                key={cat.id}
                onClick={() => {
                  setActiveCategory(cat.id)
                  setExpandedFaqId(null)
                }}
                className={cn(
                  "px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all border",
                  activeCategory === cat.id
                    ? "bg-secondary text-secondary-foreground border-secondary"
                    : "bg-surface-container/60 hover:bg-surface text-muted-foreground hover:text-foreground border-outline-variant"
                )}
              >
                {cat.label}
              </button>
            ))}
          </div>

          {/* FAQ Accordion List */}
          <div className="space-y-2">
            {filteredFAQs.length > 0 ? (
              filteredFAQs.map((faq) => {
                const isExpanded = expandedFaqId === faq.id
                return (
                  <div 
                    key={faq.id} 
                    className="border border-outline-variant/75 rounded-lg bg-card/65 backdrop-blur-sm overflow-hidden transition-all duration-200"
                  >
                    <button
                      onClick={() => toggleFaq(faq.id)}
                      className="w-full px-5 py-4 flex justify-between items-center text-left hover:bg-surface/40 transition-colors gap-4"
                    >
                      <span className="font-semibold text-sm text-foreground leading-snug">{faq.question}</span>
                      {isExpanded ? (
                        <ChevronUp className="w-4 h-4 text-secondary shrink-0" />
                      ) : (
                        <ChevronDown className="w-4 h-4 text-muted-foreground shrink-0" />
                      )}
                    </button>
                    
                    {isExpanded && (
                      <div className="px-5 pb-4 pt-1 border-t border-outline-variant/40 text-xs text-muted-foreground leading-relaxed bg-surface/20">
                        {faq.answer}
                      </div>
                    )}
                  </div>
                )
              })
            ) : (
              <div className="py-12 border border-dashed border-outline-variant/60 rounded-xl text-center space-y-2">
                <HelpCircle className="w-8 h-8 text-muted-foreground/60 mx-auto" />
                <p className="text-sm font-semibold text-muted-foreground">검색 결과와 일치하는 질문이 없습니다.</p>
                <p className="text-xs text-muted-foreground/80">다른 검색어를 입력하시거나 다른 카테고리를 선택해 보세요.</p>
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Institutional Inquiry Form */}
        <div className="space-y-4">
          <div className="border border-outline-variant/75 rounded-xl bg-card/65 backdrop-blur-sm p-5 shadow-sm space-y-4">
            <div className="border-b border-outline-variant/40 pb-3">
              <h3 className="font-bold text-sm text-foreground flex items-center gap-1.5">
                <Building className="w-4 h-4 text-secondary" />
                기관 제휴 및 1:1 상담 문의
              </h3>
              <p className="text-[11px] text-muted-foreground leading-relaxed mt-1">
                STO 발행 자문, 유동성 연동 및 시스템 제휴 상담을 남겨주시면 담당 파트너가 24시간 내에 연락드립니다.
              </p>
            </div>

            {submitted ? (
              <div className="py-8 text-center space-y-3 bg-secondary/5 rounded-lg border border-secondary/15 p-4 animate-fade-in">
                <CheckCircle2 className="w-10 h-10 text-green-400 mx-auto animate-bounce" />
                <div className="space-y-1">
                  <p className="text-xs font-bold text-foreground">상담 신청 완료!</p>
                  <p className="text-[11px] text-muted-foreground leading-relaxed">
                    작성해주신 정보가 안전하게 전달되었습니다.<br />
                    검토 후 담당 파트너가 이메일로 회신해 드리겠습니다.
                  </p>
                </div>
                <button
                  onClick={() => setSubmitted(false)}
                  className="mt-2 text-[10px] font-semibold text-secondary hover:underline"
                >
                  새로운 문의 작성하기
                </button>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-3.5">
                {/* Company Name */}
                <div className="space-y-1.5">
                  <label className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider flex items-center gap-1">
                    <Building className="w-3 h-3" /> 소속 기관명
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="예: 한국증권금융, 조각자산운용"
                    value={company}
                    onChange={(e) => setCompany(e.target.value)}
                    className="w-full bg-surface border border-outline-variant rounded px-3 py-1.5 text-xs text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:border-secondary transition-colors"
                  />
                </div>

                {/* Contact Name */}
                <div className="space-y-1.5">
                  <label className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider flex items-center gap-1">
                    <User className="w-3 h-3" /> 담당자명
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="예: 홍길동 팀장"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    className="w-full bg-surface border border-outline-variant rounded px-3 py-1.5 text-xs text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:border-secondary transition-colors"
                  />
                </div>

                {/* Contact Email */}
                <div className="space-y-1.5">
                  <label className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider flex items-center gap-1">
                    <Mail className="w-3 h-3" /> 이메일 주소
                  </label>
                  <input
                    type="email"
                    required
                    placeholder="partner@company.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="w-full bg-surface border border-outline-variant rounded px-3 py-1.5 text-xs text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:border-secondary transition-colors"
                  />
                </div>

                {/* Inquiry Classification */}
                <div className="space-y-1.5">
                  <label className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider flex items-center gap-1">
                    <FileText className="w-3 h-3" /> 문의 분류
                  </label>
                  <select
                    value={inquiryType}
                    onChange={(e) => setInquiryType(e.target.value)}
                    className="w-full bg-surface border border-outline-variant rounded px-3 py-1.5 text-xs text-foreground focus:outline-none focus:border-secondary transition-colors"
                  >
                    <option value="issuance_inquiry">신규 STO 상품 발행 상담</option>
                    <option value="api_integration">매칭 엔진 API 제휴</option>
                    <option value="node_operator">컨소시엄 블록체인 노드 참여</option>
                    <option value="others">기타 문의</option>
                  </select>
                </div>

                {/* Inquiry Message */}
                <div className="space-y-1.5">
                  <label className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider flex items-center gap-1">
                    <MessageSquare className="w-3 h-3" /> 문의 사항 상세
                  </label>
                  <textarea
                    required
                    rows={4}
                    placeholder="희망 기초 자산 정보 및 요구 사항을 상세히 기재해 주세요..."
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
                    className="w-full bg-surface border border-outline-variant rounded px-3 py-1.5 text-xs text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:border-secondary transition-colors resize-none"
                  />
                </div>

                {/* Submit button */}
                <button
                  type="submit"
                  disabled={submitting}
                  className="w-full flex items-center justify-center gap-1 bg-secondary hover:bg-secondary/90 disabled:bg-secondary/40 text-secondary-foreground text-xs font-semibold py-2 px-4 rounded transition-colors"
                >
                  {submitting ? (
                    <span>문의 접수 처리 중...</span>
                  ) : (
                    <>
                      <span>상담 신청 제출하기</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </>
                  )}
                </button>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

function cn(...classes: any[]) {
  return classes.filter(Boolean).join(" ")
}
