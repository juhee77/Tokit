'use client';

import React, { useState } from 'react';
import { useTradeStore } from '../stores/useTradeStore';
import { fetchApi } from '../lib/api';
import { Order } from '../types';

export default function TradePanel() {
  const selectedSymbol = useTradeStore((state) => state.selectedSymbol);
  
  const [orderType, setOrderType] = useState<'BUY' | 'SELL'>('BUY');
  const [price, setPrice] = useState<string>('');
  const [quantity, setQuantity] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);

  // 테스트 계정 ID 임의 지정 (1번 회원)
  const testMemberId = 1; 

  const total = (parseFloat(price) || 0) * (parseFloat(quantity) || 0);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!price || !quantity) return;

    setLoading(true);
    setMessage(null);

    try {
      const response = await fetchApi<Order>('/api/orders', {
        method: 'POST',
        body: JSON.stringify({
          memberId: testMemberId,
          assetSymbol: selectedSymbol,
          orderType,
          price: parseFloat(price),
          quantity: parseFloat(quantity),
        }),
      });

      setMessage({
        text: `${orderType === 'BUY' ? '매수' : '매도'} 주문이 성공적으로 접수되었습니다. (ID: ${response.id})`,
        error: false,
      });
      
      // 입력창 초기화
      setPrice('');
      setQuantity('');
    } catch (e: any) {
      console.error(e);
      setMessage({
        text: e.message || '주문 전송에 실패했습니다.',
        error: true,
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-[#1e293b]/70 backdrop-blur-md border border-[#334155] rounded-2xl p-6 shadow-2xl flex flex-col justify-between text-slate-100 h-full">
      <div>
        <div className="flex items-center justify-between border-b border-[#334155] pb-4 mb-6">
          <h3 className="font-semibold text-lg tracking-wide text-indigo-400">Order Panel</h3>
          <span className="text-xs bg-slate-700 text-slate-300 font-mono px-2 py-1 rounded">
            Member #1
          </span>
        </div>

        {/* 매수/매도 탭 */}
        <div className="grid grid-cols-2 gap-2 mb-6 p-1 bg-slate-950/40 rounded-xl border border-[#334155]/50">
          <button
            type="button"
            className={`py-2.5 rounded-lg text-sm font-semibold tracking-wider transition-all duration-300 ${
              orderType === 'BUY'
                ? 'bg-emerald-500 text-white shadow-lg shadow-emerald-500/20'
                : 'text-slate-400 hover:text-slate-200'
            }`}
            onClick={() => setOrderType('BUY')}
          >
            BUY (매수)
          </button>
          <button
            type="button"
            className={`py-2.5 rounded-lg text-sm font-semibold tracking-wider transition-all duration-300 ${
              orderType === 'SELL'
                ? 'bg-red-500 text-white shadow-lg shadow-red-500/20'
                : 'text-slate-400 hover:text-slate-200'
            }`}
            onClick={() => setOrderType('SELL')}
          >
            SELL (매도)
          </button>
        </div>

        {/* 폼 */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Price (가격)</label>
            <div className="relative">
              <input
                type="number"
                step="any"
                required
                className="w-full bg-slate-900 border border-[#334155] focus:border-indigo-500 rounded-xl px-4 py-3 text-slate-100 font-mono focus:outline-none transition-colors"
                placeholder="0"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
              />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-500 font-mono">KRW</span>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Quantity (수량)</label>
            <div className="relative">
              <input
                type="number"
                step="any"
                required
                className="w-full bg-slate-900 border border-[#334155] focus:border-indigo-500 rounded-xl px-4 py-3 text-slate-100 font-mono focus:outline-none transition-colors"
                placeholder="0"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
              />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-500 font-mono">Qty</span>
            </div>
          </div>

          {/* 총액 */}
          <div className="bg-slate-950/30 border border-[#334155]/30 rounded-xl p-4 flex items-center justify-between font-mono mt-6">
            <span className="text-xs font-semibold text-slate-400">Total</span>
            <span className="text-base font-bold text-indigo-300">
              {total.toLocaleString()} <span className="text-xs font-normal text-slate-400">KRW</span>
            </span>
          </div>

          {/* 알림 메시지 */}
          {message && (
            <div className={`p-3 rounded-lg text-xs leading-relaxed border ${
              message.error 
                ? 'bg-red-500/10 border-red-500/30 text-red-300' 
                : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
            }`}>
              {message.text}
            </div>
          )}

          {/* 전송 버튼 */}
          <button
            type="submit"
            disabled={loading}
            className={`w-full py-4 rounded-xl text-sm font-bold uppercase tracking-wider transition-all duration-300 text-white ${
              loading 
                ? 'bg-slate-700 cursor-not-allowed'
                : orderType === 'BUY'
                  ? 'bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 shadow-lg shadow-emerald-500/20 active:scale-[0.98]'
                  : 'bg-gradient-to-r from-red-600 to-rose-500 hover:from-red-500 hover:to-rose-400 shadow-lg shadow-red-500/20 active:scale-[0.98]'
            }`}
          >
            {loading ? 'Processing...' : `Submit ${orderType} Order`}
          </button>
        </form>
      </div>

      <div className="text-[10px] text-slate-500 text-center mt-6">
        *Orders are processed instantly by the Spring Boot Price-Time priority matching engine and broadcasted via WebSockets and SSE.
      </div>
    </div>
  );
}
