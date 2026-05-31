import Link from 'next/link';

export default function Home() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950 text-slate-100 flex flex-col justify-between">
      {/* GNB */}
      <header className="border-b border-[#334155]/60 bg-[#1e293b]/30 backdrop-blur-md">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-8">
            <span className="text-xl font-black bg-gradient-to-r from-indigo-400 to-cyan-400 bg-clip-text text-transparent tracking-wider">
              TOKIT STO
            </span>
          </div>
          <Link
            href="/trade"
            className="bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm px-5 py-2.5 rounded-xl shadow-lg shadow-indigo-500/20 transition-all duration-300"
          >
            Launch Terminal
          </Link>
        </div>
      </header>

      {/* Hero Section */}
      <main className="flex-1 max-w-4xl mx-auto flex flex-col items-center justify-center text-center px-6 py-20">
        <div className="inline-flex items-center gap-2 bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 px-4 py-2 rounded-full text-xs font-semibold uppercase tracking-wider mb-6 animate-pulse">
          <span className="w-1.5 h-1.5 rounded-full bg-indigo-400"></span>
          Next-Gen Monorepo Trading Infrastructure
        </div>

        <h1 className="text-5xl md:text-6xl font-extrabold tracking-tight mb-8 leading-tight">
          STO 토큰증권 <br />
          <span className="bg-gradient-to-r from-indigo-400 via-purple-400 to-cyan-400 bg-clip-text text-transparent">
            매칭 엔진 & 거래 플랫폼
          </span>
        </h1>

        <p className="text-slate-400 text-base md:text-lg leading-relaxed max-w-2xl mb-12">
          Java 25 Spring Boot 비동기 매칭 엔진과 Next.js 16 실시간 호가창(WebSockets/SSE),
          그리고 Solidity ERC-1400 토큰증권 규격을 채택한 엔터프라이즈급 STO 프로젝트의 초기 뼈대입니다.
        </p>

        <div className="flex flex-col sm:flex-row gap-4 justify-center items-center">
          <Link
            href="/trade"
            className="bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500 text-white font-bold text-base px-8 py-4 rounded-xl shadow-xl shadow-indigo-500/30 transition-all duration-300 transform active:scale-95 w-full sm:w-auto"
          >
            Enter Trading Terminal
          </Link>
          <a
            href="https://github.com/ethereum/EIPs/issues/1411"
            target="_blank"
            rel="noopener noreferrer"
            className="bg-slate-900/60 hover:bg-slate-900 border border-[#334155] text-slate-300 font-semibold text-base px-8 py-4 rounded-xl transition-all duration-300 w-full sm:w-auto hover:border-slate-500"
          >
            ERC-1400 Standards
          </a>
        </div>

        {/* Tech Stack Icons/Badges */}
        <div className="mt-24 grid grid-cols-2 sm:grid-cols-4 gap-4 w-full max-w-3xl">
          <div className="bg-[#1e293b]/40 border border-[#334155]/40 rounded-2xl p-6 backdrop-blur-md">
            <span className="block text-indigo-400 font-black text-lg mb-1">Spring 4.0</span>
            <span className="text-[11px] text-slate-400 font-medium">Java 25 DDD Backend</span>
          </div>
          <div className="bg-[#1e293b]/40 border border-[#334155]/40 rounded-2xl p-6 backdrop-blur-md">
            <span className="block text-purple-400 font-black text-lg mb-1">Next.js 16</span>
            <span className="text-[11px] text-slate-400 font-medium">React 19 App Router</span>
          </div>
          <div className="bg-[#1e293b]/40 border border-[#334155]/40 rounded-2xl p-6 backdrop-blur-md">
            <span className="block text-cyan-400 font-black text-lg mb-1">ERC-1400</span>
            <span className="text-[11px] text-slate-400 font-medium">Security Token Solidity</span>
          </div>
          <div className="bg-[#1e293b]/40 border border-[#334155]/40 rounded-2xl p-6 backdrop-blur-md">
            <span className="block text-teal-400 font-black text-lg mb-1">Docker</span>
            <span className="text-[11px] text-slate-400 font-medium">Local Multi-Infra Setup</span>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-[#334155]/50 bg-slate-950/20 py-8 text-center text-xs text-slate-500 font-mono">
        © 2026 TOKIT Inc. All rights reserved. Monorepo Architecture.
      </footer>
    </div>
  );
}
