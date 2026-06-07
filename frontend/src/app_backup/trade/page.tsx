'use client';

import React, { useEffect, useState } from 'react';
import { useTradeStore } from '../../stores/useTradeStore';
import { useOrderBookStream } from '../../hooks/useOrderBookStream';
import { useTradeStream } from '../../hooks/useTradeStream';
import { fetchApi } from '../../lib/api';
import { Asset, Trade } from '../../types';
import OrderBook from '../../components/OrderBook';
import TradePanel from '../../components/TradePanel';
import Link from 'next/link';

export default function TradePage() {
  const selectedSymbol = useTradeStore((state) => state.selectedSymbol);
  const assets = useTradeStore((state) => state.assets);
  const recentTrades = useTradeStore((state) => state.recentTrades);
  const setSelectedSymbol = useTradeStore((state) => state.setSelectedSymbol);
  const setAssets = useTradeStore((state) => state.setAssets);
  const setRecentTrades = useTradeStore((state) => state.setRecentTrades);

  // 실시간 스트리밍 훅 가동 (Zustand 데이터 자동 업데이트)
  useOrderBookStream(selectedSymbol);
  useTradeStream(selectedSymbol);

  const [initializing, setInitializing] = useState(true);

  // 초기 자산 목록 및 체결 내역 로드
  useEffect(() => {
    async function initData() {
      try {
        // 1. 자산 목록 로드
        const loadedAssets = await fetchApi<Asset[]>('/api/assets');
        setAssets(loadedAssets);
        
        if (loadedAssets.length > 0) {
          // 첫 번째 자산이 있으면 기본 선택
          const hasSelected = loadedAssets.some(a => a.symbol === selectedSymbol);
          if (!hasSelected) {
            setSelectedSymbol(loadedAssets[0].symbol);
          }
        }
      } catch (e) {
        console.error('Failed to load initial assets list:', e);
        // Fallback dummy asset for styling test in front-only mode
        setAssets([
          { id: 1, symbol: 'APPL-STO', name: 'Apple Security Token', contractAddress: '0x123...', totalSupply: 1000000 },
          { id: 2, symbol: 'TSLA-STO', name: 'Tesla Security Token', contractAddress: '0x456...', totalSupply: 500000 }
        ]);
      }

      try {
        // 2. 현재 선택된 자산의 과거 체결 내역 로드
        const loadedTrades = await fetchApi<Trade[]>(`/api/trades/asset/${selectedSymbol}`);
        setRecentTrades(loadedTrades);
      } catch (e) {
        console.error(`Failed to load trades for ${selectedSymbol}:`, e);
      } finally {
        setInitializing(false);
      }
    }

    initData();
  }, [selectedSymbol, setAssets, setRecentTrades, setSelectedSymbol]);

  const currentAsset = assets.find((a) => a.symbol === selectedSymbol);

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950 text-slate-100 flex flex-col">
      {/* GNB */}
      <header className="border-b border-[#334155]/60 bg-[#1e293b]/30 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center space-x-8">
            <Link href="/" className="text-xl font-black bg-gradient-to-r from-indigo-400 to-cyan-400 bg-clip-text text-transparent tracking-wider">
              TOKIT STO
            </Link>
            <nav className="flex space-x-6 text-sm font-semibold">
              <Link href="/trade" className="text-indigo-400 hover:text-indigo-300">Trading Console</Link>
              <span className="text-slate-600">|</span>
              <span className="text-slate-400 cursor-not-allowed">Portfolios</span>
              <span className="text-slate-400 cursor-not-allowed">Compliance</span>
            </nav>
          </div>
          <div className="flex items-center space-x-4">
            <span className="text-xs bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-3 py-1.5 rounded-full font-mono font-semibold flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              Spring Boot + Hardhat Node Connected
            </span>
          </div>
        </div>
      </header>

      {initializing ? (
        <div className="flex-1 flex items-center justify-center">
          <div className="flex flex-col items-center space-y-4">
            <div className="w-12 h-12 border-4 border-indigo-500/30 border-t-indigo-500 rounded-full animate-spin"></div>
            <p className="text-sm font-semibold text-slate-400">Initializing STO Terminal...</p>
          </div>
        </div>
      ) : (
        <main className="flex-1 max-w-7xl w-full mx-auto px-6 py-8 flex flex-col space-y-6">
          {/* 자산 세부 정보 탑바 */}
          <div className="bg-[#1e293b]/40 border border-[#334155]/50 backdrop-blur-md rounded-2xl p-6 flex flex-wrap items-center justify-between gap-6 shadow-xl">
            <div className="flex items-center space-x-4">
              <div className="w-12 h-12 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center font-black text-lg tracking-wider shadow-md shadow-indigo-500/20">
                {selectedSymbol.substring(0, 2)}
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <h1 className="text-2xl font-bold tracking-tight">{currentAsset?.symbol || selectedSymbol}</h1>
                  <span className="text-xs bg-indigo-500/10 text-indigo-300 font-mono px-2 py-0.5 rounded border border-indigo-500/20">ERC-1400</span>
                </div>
                <p className="text-xs text-slate-400 font-medium mt-0.5">{currentAsset?.name || 'Security Token Asset'}</p>
              </div>
            </div>

            <div className="flex items-center space-x-8 font-mono text-sm">
              <div className="border-l border-[#334155] pl-6">
                <span className="block text-[10px] uppercase font-semibold text-slate-500 tracking-wider mb-0.5">Contract Address</span>
                <span className="text-slate-300 text-xs font-semibold select-all block max-w-[120px] truncate" title={currentAsset?.contractAddress}>
                  {currentAsset?.contractAddress || '0x0000...0000'}
                </span>
              </div>
              <div className="border-l border-[#334155] pl-6">
                <span className="block text-[10px] uppercase font-semibold text-slate-500 tracking-wider mb-0.5">Total Supply</span>
                <span className="text-slate-200 font-bold">
                  {(currentAsset?.totalSupply || 1000000).toLocaleString()} <span className="text-xs font-normal text-slate-400">Tokens</span>
                </span>
              </div>
            </div>
          </div>

          {/* 메인 3단 레이아웃 */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch flex-1">
            
            {/* 단 1: 토큰 목록 (선택기) */}
            <div className="lg:col-span-3 bg-[#1e293b]/70 backdrop-blur-md border border-[#334155] rounded-2xl p-6 shadow-2xl flex flex-col h-full">
              <h3 className="font-semibold text-base text-slate-300 mb-4 border-b border-[#334155]/50 pb-2 tracking-wide">Security Tokens</h3>
              <div className="space-y-2 flex-1 overflow-y-auto pr-1">
                {assets.length === 0 ? (
                  <div className="text-slate-500 text-xs text-center py-8">No registered tokens</div>
                ) : (
                  assets.map((asset) => (
                    <button
                      key={asset.id}
                      onClick={() => setSelectedSymbol(asset.symbol)}
                      className={`w-full text-left p-4 rounded-xl border flex items-center justify-between transition-all duration-300 ${
                        selectedSymbol === asset.symbol
                          ? 'bg-indigo-600/20 border-indigo-500 shadow-lg shadow-indigo-500/5'
                          : 'bg-slate-900/40 border-[#334155]/60 hover:bg-slate-900/80 hover:border-slate-600'
                      }`}
                    >
                      <div>
                        <span className={`block font-bold text-sm ${selectedSymbol === asset.symbol ? 'text-indigo-300' : 'text-slate-200'}`}>
                          {asset.symbol}
                        </span>
                        <span className="text-[10px] text-slate-400 mt-0.5 block truncate max-w-[150px]">
                          {asset.name}
                        </span>
                      </div>
                      <span className="text-xs font-bold text-slate-300 bg-slate-950/40 px-2 py-1 rounded border border-[#334155]/50">
                        Active
                      </span>
                    </button>
                  ))
                )}
              </div>
            </div>

            {/* 단 2: 호가창 */}
            <div className="lg:col-span-4 h-full">
              <OrderBook />
            </div>

            {/* 단 3: 매매 패널 & 최근 체결 내역 */}
            <div className="lg:col-span-5 flex flex-col space-y-6 h-full">
              {/* 매매 패널 */}
              <div className="flex-1">
                <TradePanel />
              </div>

              {/* 최근 체결 내역 */}
              <div className="bg-[#1e293b]/70 backdrop-blur-md border border-[#334155] rounded-2xl p-6 shadow-2xl flex flex-col h-[280px]">
                <h3 className="font-semibold text-sm text-indigo-400 border-b border-[#334155]/50 pb-2 mb-3 tracking-wide">
                  Recent Trades (실시간 체결)
                </h3>
                <div className="flex-grow overflow-y-auto pr-1">
                  <div className="grid grid-cols-3 text-[10px] font-semibold text-slate-400 pb-1.5 border-b border-[#334155]/30">
                    <div>Time</div>
                    <div className="text-center">Price (KRW)</div>
                    <div className="text-right">Quantity</div>
                  </div>
                  <div className="space-y-1.5 mt-2 font-mono text-xs">
                    {recentTrades.length === 0 ? (
                      <div className="text-slate-500 text-center py-8">Waiting for matches...</div>
                    ) : (
                      recentTrades.map((trade) => {
                        const date = new Date(trade.createdAt);
                        const timeStr = date.toLocaleTimeString(undefined, { hour12: false });
                        return (
                          <div key={trade.id} className="grid grid-cols-3 py-1 border-b border-[#334155]/10 animate-fade-in hover:bg-slate-800/20 px-0.5 rounded">
                            <span className="text-slate-500">{timeStr}</span>
                            <span className="text-center font-bold text-indigo-300">
                              {trade.price.toLocaleString()}
                            </span>
                            <span className="text-right text-slate-300">
                              {trade.quantity.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}
                            </span>
                          </div>
                        );
                      })
                    )}
                  </div>
                </div>
              </div>

            </div>

          </div>
        </main>
      )}
    </div>
  );
}
