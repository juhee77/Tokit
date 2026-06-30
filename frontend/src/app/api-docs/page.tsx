"use client"

import { useState } from "react"
import { 
  Code, 
  Terminal, 
  Settings, 
  Layers, 
  Key, 
  Check, 
  Copy, 
  Radio, 
  ArrowRight,
  Database
} from "lucide-react"

interface ApiEndpoint {
  id: string
  method: "GET" | "POST" | "PUT" | "DELETE"
  path: string
  summary: string
  description: string
  headers?: { name: string; type: string; required: boolean; description: string }[]
  params?: { name: string; type: string; required: boolean; description: string }[]
  requestBody?: string
  responseBody: string
  curlExample: string
  jsExample: string
}

const REST_ENDPOINTS: ApiEndpoint[] = [
  {
    id: "register-asset",
    method: "POST",
    path: "/api/assets",
    summary: "신규 STO 상품 자산 등록",
    description: "새로운 조각투자 자산을 데이터베이스에 등록하고 거래 가능 상태를 지정합니다.",
    headers: [
      { name: "Content-Type", type: "string", required: true, description: "application/json" }
    ],
    requestBody: `{
  "symbol": "TEST-STO",
  "name": "테스트 오피스 빌딩",
  "contractAddress": "0x2222222222222222222222222222222222222222",
  "totalSupply": 1000000,
  "issuePrice": 10000,
  "status": "청약중"
}`,
    responseBody: `{
  "status": 200,
  "message": "SUCCESS",
  "data": {
    "id": 35,
    "symbol": "TEST-STO",
    "name": "테스트 오피스 빌딩",
    "contractAddress": "0x2222222222222222222222222222222222222222",
    "totalSupply": 1000000,
    "status": "청약중",
    "issuePrice": 10000,
    "currentAmount": 0,
    "totalInvestors": 0
  }
}`,
    curlExample: `curl -X POST -H "Content-Type: application/json" \\
  -d '{"symbol":"TEST-STO","name":"테스트 오피스 빌딩","contractAddress":"0x2222222222222222222222222222222222222222","totalSupply":1000000,"issuePrice":10000,"status":"청약중"}' \\
  http://localhost:8080/api/assets`,
    jsExample: `fetch('http://localhost:8080/api/assets', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    symbol: 'TEST-STO',
    name: '테스트 오피스 빌딩',
    contractAddress: '0x2222222222222222222222222222222222222222',
    totalSupply: 1000000,
    issuePrice: 10000,
    status: '청약중'
  })
})
.then(res => res.json())
.then(data => console.log(data));`
  },
  {
    id: "submit-order",
    method: "POST",
    path: "/api/orders",
    summary: "신규 거래소 매도/매수 주문 등록",
    description: "거래소 매칭 엔진에 지정가 또는 시장가 주문을 실시간 제출합니다.",
    headers: [
      { name: "Content-Type", type: "string", required: true, description: "application/json" },
      { name: "X-Idempotency-Key", type: "string(UUID)", required: true, description: "이중 주문 및 충전 방지를 위한 멱등키" }
    ],
    requestBody: `{
  "userId": 1,
  "assetSymbol": "GNPM",
  "price": 10000,
  "quantity": 50,
  "orderType": "BUY",
  "priceType": "LIMIT"
}`,
    responseBody: `{
  "status": 200,
  "message": "SUCCESS",
  "data": {
    "orderId": 142,
    "userId": 1,
    "assetSymbol": "GNPM",
    "price": 10000,
    "quantity": 50,
    "orderType": "BUY",
    "priceType": "LIMIT",
    "status": "PENDING"
  }
}`,
    curlExample: `curl -X POST -H "Content-Type: application/json" \\
  -H "X-Idempotency-Key: e4b78db1-b219-482a-bc9f-141a54fbac79" \\
  -d '{"userId":1,"assetSymbol":"GNPM","price":10000,"quantity":50,"orderType":"BUY","priceType":"LIMIT"}' \\
  http://localhost:8080/api/orders`,
    jsExample: `fetch('http://localhost:8080/api/orders', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Idempotency-Key': 'e4b78db1-b219-482a-bc9f-141a54fbac79'
  },
  body: JSON.stringify({
    userId: 1,
    assetSymbol: 'GNPM',
    price: 10000,
    quantity: 50,
    orderType: 'BUY',
    priceType: 'LIMIT'
  })
})
.then(res => res.json())
.then(data => console.log(data));`
  },
  {
    id: "update-kyc",
    method: "PUT",
    path: "/api/users/{id}/kyc",
    summary: "투자 회원 KYC 승인 및 온체인 동기화",
    description: "사용자의 오프체인 신원 인증 상태를 변경하고 Solidity Smart Contract whitelist에 동기화합니다.",
    params: [
      { name: "id", type: "number", required: true, description: "사용자 고유 일련번호(ID)" },
      { name: "kycStatus", type: "boolean", required: true, description: "kyc 여부 (true / false)" }
    ],
    responseBody: `{
  "status": 200,
  "message": "SUCCESS",
  "data": {
    "id": 1,
    "email": "test-investor@tokit.com",
    "name": "김토킷",
    "walletAddress": "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
    "kycStatus": true
  }
}`,
    curlExample: `curl -X PUT -s "http://localhost:8080/api/users/1/kyc?kycStatus=true"`,
    jsExample: `fetch('http://localhost:8080/api/users/1/kyc?kycStatus=true', {
  method: 'PUT'
})
.then(res => res.json())
.then(data => console.log(data));`
  },
  {
    id: "run-reconciliation",
    method: "POST",
    path: "/api/reconciliation/run",
    summary: "온-오프체인 데이터 정합성 대사 수동 실행",
    description: "PostgreSQL의 DB 원장과 Hardhat 블록체인 노드의 ERC-1400 토큰 잔액을 대조하는 배치를 즉시 실행합니다.",
    responseBody: `{
  "status": 200,
  "message": "SUCCESS",
  "data": {
    "status": "COMPLETED"
  }
}`,
    curlExample: `curl -X POST -s "http://localhost:8080/api/reconciliation/run"`,
    jsExample: `fetch('http://localhost:8080/api/reconciliation/run', {
  method: 'POST'
})
.then(res => res.json())
.then(data => console.log(data));`
  }
]

export default function ApiDocsPage() {
  const [activeSection, setActiveSection] = useState<string>("overview")
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const [codeType, setCodeType] = useState<"curl" | "javascript">("curl")

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text)
    setCopiedId(id)
    setTimeout(() => setCopiedId(null), 1500)
  }

  return (
    <div className="flex flex-col lg:flex-row gap-6 max-w-7xl mx-auto pb-12">
      {/* Left Sidebar Menu */}
      <div className="w-full lg:w-64 shrink-0 space-y-2">
        <div className="border border-outline-variant/75 rounded-xl bg-card/65 backdrop-blur-sm p-4 sticky top-6 space-y-4 shadow-sm">
          <div className="flex items-center gap-2 border-b border-outline-variant/40 pb-2">
            <Code className="w-5 h-5 text-secondary animate-pulse" />
            <h4 className="font-bold text-sm text-foreground">API Reference</h4>
          </div>

          <div className="space-y-1 text-xs">
            <button
              onClick={() => setActiveSection("overview")}
              className={`w-full text-left px-3 py-2 rounded-lg font-semibold transition-all ${
                activeSection === "overview"
                  ? "bg-secondary text-secondary-foreground"
                  : "text-muted-foreground hover:text-foreground hover:bg-surface-container/40"
              }`}
            >
              Overview & Base URL
            </button>
            <button
              onClick={() => setActiveSection("idempotency")}
              className={`w-full text-left px-3 py-2 rounded-lg font-semibold transition-all ${
                activeSection === "idempotency"
                  ? "bg-secondary text-secondary-foreground"
                  : "text-muted-foreground hover:text-foreground hover:bg-surface-container/40"
              }`}
            >
              멱등키 (Idempotency) 검증
            </button>
            <div className="pt-2 pb-1 px-3 text-[10px] uppercase font-bold text-muted-foreground tracking-wider">
              REST APIs
            </div>
            {REST_ENDPOINTS.map((endpoint) => (
              <button
                key={endpoint.id}
                onClick={() => setActiveSection(endpoint.id)}
                className={`w-full text-left px-3 py-2 rounded-lg font-semibold transition-all flex items-center justify-between ${
                  activeSection === endpoint.id
                    ? "bg-secondary text-secondary-foreground"
                    : "text-muted-foreground hover:text-foreground hover:bg-surface-container/40"
                }`}
              >
                <span className="truncate">{endpoint.summary}</span>
                <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded ml-2 ${
                  endpoint.method === "POST" ? "bg-green-500/10 text-green-400" :
                  endpoint.method === "PUT" ? "bg-orange-500/10 text-orange-400" :
                  "bg-blue-500/10 text-blue-400"
                }`}>
                  {endpoint.method}
                </span>
              </button>
            ))}
            <div className="pt-2 pb-1 px-3 text-[10px] uppercase font-bold text-muted-foreground tracking-wider">
              Streaming Protocols
            </div>
            <button
              onClick={() => setActiveSection("stomp")}
              className={`w-full text-left px-3 py-2 rounded-lg font-semibold transition-all ${
                activeSection === "stomp"
                  ? "bg-secondary text-secondary-foreground"
                  : "text-muted-foreground hover:text-foreground hover:bg-surface-container/40"
              }`}
            >
              STOMP WebSocket (호가창)
            </button>
            <button
              onClick={() => setActiveSection("sse")}
              className={`w-full text-left px-3 py-2 rounded-lg font-semibold transition-all ${
                activeSection === "sse"
                  ? "bg-secondary text-secondary-foreground"
                  : "text-muted-foreground hover:text-foreground hover:bg-surface-container/40"
              }`}
            >
              SSE (체결 내역 스트림)
            </button>
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 space-y-6">
        <div className="border border-outline-variant/75 rounded-xl bg-card/65 backdrop-blur-sm p-6 shadow-sm min-h-[500px]">
          
          {/* Overview Section */}
          {activeSection === "overview" && (
            <div className="space-y-4">
              <h2 className="text-xl font-bold text-foreground">Overview & Base URL</h2>
              <p className="text-xs text-muted-foreground leading-relaxed">
                TOKIT 토큰증권 플랫폼은 금융 시장 안정성과 초고속 트레이딩 매칭 처리를 위한 고성능 REST 및 실시간 양방향 채널을 제공합니다.
              </p>
              
              <div className="bg-surface-container border border-outline-variant rounded p-4 space-y-2">
                <p className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider flex items-center gap-1">
                  <Database className="w-3.5 h-3.5" /> API Base URL
                </p>
                <div className="flex items-center justify-between bg-surface border border-outline-variant/80 rounded px-3 py-2 font-mono text-xs">
                  <span className="text-secondary select-all">http://localhost:8080</span>
                  <button 
                    onClick={() => copyToClipboard("http://localhost:8080", "baseurl")}
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    {copiedId === "baseurl" ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div className="space-y-2">
                <h3 className="text-sm font-bold text-foreground">공통 응답 포맷 (ApiResponse{"<T>"})</h3>
                <p className="text-xs text-muted-foreground leading-relaxed">
                  모든 REST API 응답은 아래와 같은 표준 상태 코드 구조로 패킹되어 리턴됩니다.
                </p>
                <pre className="bg-surface border border-outline-variant rounded p-4 font-mono text-xs text-secondary overflow-x-auto">
{`{
  "status": 200,      // HTTP 상태 코드와 대칭
  "message": "SUCCESS",
  "data": { ... }     // 리턴되는 엔티티 또는 DTO 본문
}`}
                </pre>
              </div>
            </div>
          )}

          {/* Idempotency Section */}
          {activeSection === "idempotency" && (
            <div className="space-y-4">
              <h2 className="text-xl font-bold text-foreground">멱등성(Idempotency) 검증 및 중복 제어</h2>
              <p className="text-xs text-muted-foreground leading-relaxed">
                이중 클릭, 네트워크 순서 꼬임으로 인한 중복 체결 및 다중 입금 충전을 방지하기 위해 쓰기(POST/PUT) 연산 시 멱등키 전송을 필수로 요구합니다.
              </p>

              <div className="space-y-2">
                <h3 className="text-sm font-bold text-foreground">인증 요구 헤더</h3>
                <div className="border border-outline-variant rounded overflow-hidden text-xs">
                  <div className="grid grid-cols-4 bg-surface-container font-semibold p-2 border-b border-outline-variant">
                    <span>헤더명</span>
                    <span>타입</span>
                    <span>필수여부</span>
                    <span className="col-span-2">설명</span>
                  </div>
                  <div className="grid grid-cols-4 p-2.5 bg-surface/50 border-b border-outline-variant/60 font-mono">
                    <span className="font-bold text-secondary">X-Idempotency-Key</span>
                    <span>String</span>
                    <span className="text-red-400">Required</span>
                    <span className="col-span-2 font-sans text-muted-foreground">최초 생성한 고유한 UUIDv4 키값</span>
                  </div>
                </div>
              </div>

              <div className="space-y-2 text-xs text-muted-foreground leading-relaxed bg-surface-container/60 p-4 border border-outline-variant rounded">
                <p className="font-bold text-foreground mb-1">💡 백엔드 분산락 동작 원리</p>
                1. 클라이언트가 고유 UUID 키를 담아 요청을 전송합니다.<br />
                2. Redis의 `SETNX {"{key}"} "PROCESSING" EX 120`을 실행하여 2분간 락을 선점합니다.<br />
                3. 처리 완료 후 결과가 성공적이면 원장 캐시에 결과를 업데이트하며, 기존에 존재하던 키로 중복 재전송할 경우 저장해 두었던 동일 성공 응답을 즉시 복사 리턴(Idempotent return)합니다.
              </div>
            </div>
          )}

          {/* REST API Sections */}
          {REST_ENDPOINTS.map((endpoint) => {
            if (activeSection !== endpoint.id) return null
            return (
              <div key={endpoint.id} className="space-y-5 animate-fade-in">
                <div className="space-y-2">
                  <div className="flex items-center gap-3">
                    <span className={`text-xs font-bold px-2 py-1 rounded ${
                      endpoint.method === "POST" ? "bg-green-500/10 text-green-400" :
                      endpoint.method === "PUT" ? "bg-orange-500/10 text-orange-400" :
                      "bg-blue-500/10 text-blue-400"
                    }`}>
                      {endpoint.method}
                    </span>
                    <h2 className="text-xl font-bold text-foreground">{endpoint.summary}</h2>
                  </div>
                  <p className="text-xs text-muted-foreground">{endpoint.description}</p>
                </div>

                {/* HTTP Endpoint Bar */}
                <div className="flex items-center justify-between bg-surface border border-outline-variant/80 rounded px-3 py-2 font-mono text-xs">
                  <div className="flex gap-2 items-center">
                    <span className="text-muted-foreground font-semibold">{endpoint.method}</span>
                    <span className="text-secondary select-all">{endpoint.path}</span>
                  </div>
                  <button 
                    onClick={() => copyToClipboard(endpoint.path, endpoint.id + "-path")}
                    className="text-muted-foreground hover:text-foreground transition-colors"
                  >
                    {copiedId === endpoint.id + "-path" ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>

                {/* Headers / Params Tables */}
                {(endpoint.headers && endpoint.headers.length > 0) && (
                  <div className="space-y-2">
                    <h3 className="text-xs font-bold text-foreground">Headers</h3>
                    <div className="border border-outline-variant rounded overflow-hidden text-xs">
                      <div className="grid grid-cols-4 bg-surface-container font-semibold p-2 border-b border-outline-variant">
                        <span>이름</span>
                        <span>타입</span>
                        <span>필수여부</span>
                        <span>설명</span>
                      </div>
                      {endpoint.headers.map((h, i) => (
                        <div key={i} className="grid grid-cols-4 p-2 bg-surface/50 border-b border-outline-variant/40 font-mono">
                          <span className="font-bold text-secondary">{h.name}</span>
                          <span>{h.type}</span>
                          <span className={h.required ? "text-red-400" : "text-muted-foreground"}>{h.required ? "Required" : "Optional"}</span>
                          <span className="font-sans text-muted-foreground">{h.description}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {(endpoint.params && endpoint.params.length > 0) && (
                  <div className="space-y-2">
                    <h3 className="text-xs font-bold text-foreground">Query Parameters</h3>
                    <div className="border border-outline-variant rounded overflow-hidden text-xs">
                      <div className="grid grid-cols-4 bg-surface-container font-semibold p-2 border-b border-outline-variant">
                        <span>이름</span>
                        <span>타입</span>
                        <span>필수여부</span>
                        <span>설명</span>
                      </div>
                      {endpoint.params.map((p, i) => (
                        <div key={i} className="grid grid-cols-4 p-2 bg-surface/50 border-b border-outline-variant/40 font-mono">
                          <span className="font-bold text-secondary">{p.name}</span>
                          <span>{p.type}</span>
                          <span className={p.required ? "text-red-400" : "text-muted-foreground"}>{p.required ? "Required" : "Optional"}</span>
                          <span className="font-sans text-muted-foreground">{p.description}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Left/Right Split Code block view */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Left: JSON Spec */}
                  <div className="space-y-2">
                    {endpoint.requestBody && (
                      <div className="space-y-1">
                        <p className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider">Request Body (JSON)</p>
                        <pre className="bg-surface border border-outline-variant rounded p-3 font-mono text-[10px] text-secondary overflow-x-auto max-h-56">
                          {endpoint.requestBody}
                        </pre>
                      </div>
                    )}
                    <div className="space-y-1">
                      <p className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider">Response Body (JSON)</p>
                      <pre className="bg-surface border border-outline-variant rounded p-3 font-mono text-[10px] text-secondary overflow-x-auto max-h-56">
                        {endpoint.responseBody}
                      </pre>
                    </div>
                  </div>

                  {/* Right: Code Playground */}
                  <div className="space-y-2">
                    <div className="flex justify-between items-center border-b border-outline-variant/40 pb-2">
                      <p className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider flex items-center gap-1">
                        <Terminal className="w-3.5 h-3.5" /> Sample Request
                      </p>
                      <div className="flex gap-2 text-[10px] font-semibold">
                        <button 
                          onClick={() => setCodeType("curl")}
                          className={`px-2 py-0.5 rounded ${codeType === "curl" ? "bg-secondary text-secondary-foreground" : "text-muted-foreground"}`}
                        >
                          cURL
                        </button>
                        <button 
                          onClick={() => setCodeType("javascript")}
                          className={`px-2 py-0.5 rounded ${codeType === "javascript" ? "bg-secondary text-secondary-foreground" : "text-muted-foreground"}`}
                        >
                          Javascript
                        </button>
                      </div>
                    </div>

                    <div className="relative">
                      <pre className="bg-surface-container border border-outline-variant rounded p-3 font-mono text-[10px] text-foreground overflow-x-auto min-h-36 max-h-96">
                        {codeType === "curl" ? endpoint.curlExample : endpoint.jsExample}
                      </pre>
                      <button
                        onClick={() => copyToClipboard(
                          codeType === "curl" ? endpoint.curlExample : endpoint.jsExample, 
                          endpoint.id + "-code"
                        )}
                        className="absolute right-2 top-2 bg-surface hover:bg-surface/80 border border-outline-variant text-muted-foreground hover:text-foreground p-1.5 rounded transition-all"
                      >
                        {copiedId === endpoint.id + "-code" ? <Check className="w-3 h-3 text-green-400" /> : <Copy className="w-3 h-3" />}
                      </button>
                    </div>
                  </div>
                </div>

              </div>
            )
          })}

          {/* STOMP WebSocket */}
          {activeSection === "stomp" && (
            <div className="space-y-4">
              <div className="flex items-center gap-2">
                <Radio className="w-5 h-5 text-secondary animate-pulse" />
                <h2 className="text-xl font-bold text-foreground">STOMP WebSocket (실시간 호가창 스트리밍)</h2>
              </div>
              <p className="text-xs text-muted-foreground leading-relaxed">
                매칭 엔진 내부에서 접수되는 지정가 매수/매도 주문 대기량(Orderbook)을 실시간 스트리밍하기 위해 STOMP 호스팅 채널을 구독합니다.
              </p>

              <div className="bg-surface-container border border-outline-variant rounded p-4 space-y-2">
                <p className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider">WebSocket Endpoint URL</p>
                <div className="bg-surface border border-outline-variant/80 rounded px-3 py-2 font-mono text-xs text-secondary select-all">
                  ws://localhost:8080/ws-tokit
                </div>
              </div>

              <div className="space-y-2 text-xs">
                <h3 className="font-bold text-foreground">호가창 구독 Destination</h3>
                <pre className="bg-surface border border-outline-variant rounded p-3 font-mono text-secondary">
                  {"/topic/orderbook/{symbol}"}
                </pre>
              </div>

              <div className="space-y-2 text-xs">
                <h3 className="font-bold text-foreground">수신 메시지 JSON 포맷</h3>
                <pre className="bg-surface border border-outline-variant rounded p-3 font-mono text-secondary overflow-x-auto">
{`{
  "symbol": "GNPM",
  "bids": [
    { "price": 10000.0, "quantity": 120.0 }
  ],
  "asks": [
    { "price": 10100.0, "quantity": 80.0 }
  ]
}`}
                </pre>
              </div>
            </div>
          )}

          {/* SSE Streams */}
          {activeSection === "sse" && (
            <div className="space-y-4">
              <div className="flex items-center gap-2">
                <Radio className="w-5 h-5 text-secondary animate-pulse" />
                <h2 className="text-xl font-bold text-foreground">SSE (실시간 매칭 체결 스트림)</h2>
              </div>
              <p className="text-xs text-muted-foreground leading-relaxed">
                특정 자산 종목에 대해 매칭 엔진에서 주문 체결(TRADE)이 발생할 때마다 Server-Sent Events(SSE) 채널을 통해 이벤트를 전송받습니다.
              </p>

              <div className="bg-surface-container border border-outline-variant rounded p-4 space-y-2">
                <p className="text-[10px] uppercase font-bold text-muted-foreground tracking-wider">HTTP Event Stream URL</p>
                <div className="bg-surface border border-outline-variant/80 rounded px-3 py-2 font-mono text-xs text-secondary select-all">
                  {"GET /api/trades/subscribe/{symbol}"}
                </div>
              </div>

              <div className="space-y-2 text-xs">
                <h3 className="font-bold text-foreground">수신 이벤트 명세</h3>
                <div className="border border-outline-variant rounded overflow-hidden">
                  <div className="grid grid-cols-3 bg-surface-container font-semibold p-2 border-b border-outline-variant">
                    <span>이벤트명</span>
                    <span>MIME Type</span>
                    <span>설명</span>
                  </div>
                  <div className="grid grid-cols-3 p-2 bg-surface/50 font-mono">
                    <span className="font-bold text-secondary">TRADE</span>
                    <span>text/event-stream</span>
                    <span className="font-sans text-muted-foreground">신규 주문 체결 완료 정보</span>
                  </div>
                </div>
              </div>

              <div className="space-y-2 text-xs">
                <h3 className="font-bold text-foreground">이벤트 메시지 JSON 데이터</h3>
                <pre className="bg-surface border border-outline-variant rounded p-3 font-mono text-secondary overflow-x-auto">
{`{
  "id": 1,
  "buyOrderId": 23,
  "sellOrderId": 24,
  "assetSymbol": "GNPM",
  "price": 10000.0,
  "quantity": 10.0,
  "createdAt": "2026-06-28T22:51:15"
}`}
                </pre>
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  )
}
