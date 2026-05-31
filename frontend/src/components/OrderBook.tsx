'use client';

import React from 'react';
import { useTradeStore } from '../stores/useTradeStore';

export default function OrderBook() {
  const orderBook = useTradeStore((state) => state.orderBook);
  const selectedSymbol = useTradeStore((state) => state.selectedSymbol);

  const { bids, asks } = orderBook;

  // 최대 물량 계산 (가로 바 100% 기준점을 잡기 위해)
  const maxQty = Math.max(
    ...bids.map((b) => b.quantity),
    ...asks.map((a) => a.quantity),
    1 // fallback to avoid division by zero
  );

  // Asks는 내림차순 정렬해서 호가창 위쪽에 배치 (비싼 매도 가격이 위쪽, 싼 매도 가격이 아래쪽)
  const sortedAsks = [...asks].sort((a, b) => b.price - a.price).slice(-10); // 가장 싼 10개
  const sortedBids = [...bids].sort((a, b) => b.price - a.price).slice(0, 10); // 가장 비싼 10개

  return (
    <div className="bg-[#1e293b]/70 backdrop-blur-md border border-[#334155] rounded-2xl p-6 shadow-2xl flex flex-col h-full text-slate-100">
      <div className="flex items-center justify-between border-b border-[#334155] pb-4 mb-4">
        <h3 className="font-semibold text-lg tracking-wide text-indigo-400">Order Book</h3>
        <span className="text-xs bg-indigo-500/20 text-indigo-300 font-mono px-2 py-1 rounded">
          {selectedSymbol}
        </span>
      </div>

      {/* 헤더 */}
      <div className="grid grid-cols-2 text-xs font-semibold text-slate-400 pb-2 border-b border-[#334155]/50">
        <div>Price (KRW)</div>
        <div className="text-right">Size (Qty)</div>
      </div>

      {/* 호가창 메인 영역 */}
      <div className="flex-1 flex flex-col justify-between overflow-y-auto space-y-3 font-mono py-2">
        
        {/* 매도 호가 (Asks) - 상단 */}
        <div className="flex flex-col space-y-[2px]">
          {sortedAsks.length === 0 ? (
            <div className="text-center text-slate-500 text-xs py-4">No Asks</div>
          ) : (
            sortedAsks.map((ask, idx) => {
              const percentage = (ask.quantity / maxQty) * 100;
              return (
                <div key={`ask-${idx}`} className="relative grid grid-cols-2 text-xs py-[3px] items-center cursor-pointer hover:bg-red-500/10 rounded px-1 transition-colors">
                  {/* 뒷배경 수량 막대 */}
                  <div
                    className="absolute right-0 top-0 bottom-0 bg-red-500/10 transition-all duration-300"
                    style={{ width: `${percentage}%` }}
                  />
                  <span className="text-red-400 font-medium z-10">{ask.price.toLocaleString()}</span>
                  <span className="text-right text-slate-300 z-10">{ask.quantity.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}</span>
                </div>
              );
            })
          )}
        </div>

        {/* 현재가 구분선 */}
        <div className="border-y border-indigo-500/30 bg-indigo-500/5 py-2 text-center my-1 rounded">
          <span className="text-xs text-indigo-300 font-semibold tracking-wider">STO MATCHING ENGINE ACTIVE</span>
        </div>

        {/* 매수 호가 (Bids) - 하단 */}
        <div className="flex flex-col space-y-[2px]">
          {sortedBids.length === 0 ? (
            <div className="text-center text-slate-500 text-xs py-4">No Bids</div>
          ) : (
            sortedBids.map((bid, idx) => {
              const percentage = (bid.quantity / maxQty) * 100;
              return (
                <div key={`bid-${idx}`} className="relative grid grid-cols-2 text-xs py-[3px] items-center cursor-pointer hover:bg-emerald-500/10 rounded px-1 transition-colors">
                  {/* 뒷배경 수량 막대 */}
                  <div
                    className="absolute right-0 top-0 bottom-0 bg-emerald-500/10 transition-all duration-300"
                    style={{ width: `${percentage}%` }}
                  />
                  <span className="text-emerald-400 font-medium z-10">{bid.price.toLocaleString()}</span>
                  <span className="text-right text-slate-300 z-10">{bid.quantity.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}</span>
                </div>
              );
            })
          )}
        </div>

      </div>
    </div>
  );
}
