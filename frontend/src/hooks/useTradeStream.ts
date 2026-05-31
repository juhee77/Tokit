import { useEffect } from 'react';
import { useTradeStore } from '../stores/useTradeStore';
import { Trade } from '../types';

export function useTradeStream(symbol: string) {
  const addRecentTrade = useTradeStore((state) => state.addRecentTrade);

  useEffect(() => {
    if (!symbol || typeof window === 'undefined') return;

    const apiBaseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
    const sseUrl = `${apiBaseUrl}/api/trades/subscribe/${symbol}`;
    
    console.log(`Connecting to SSE Trade Stream: ${sseUrl}`);
    const eventSource = new EventSource(sseUrl);

    eventSource.addEventListener('INIT', (event) => {
      console.log('SSE Trade Stream connection initialized:', event.data);
    });

    eventSource.addEventListener('TRADE', (event) => {
      try {
        const tradeData: Trade = JSON.parse(event.data);
        console.log('Realtime trade event received via SSE:', tradeData);
        addRecentTrade(tradeData);
      } catch (e) {
        console.error('Failed to parse SSE trade event data', e);
      }
    });

    eventSource.onerror = (error) => {
      console.error('SSE connection error:', error);
      // 브라우저가 자동으로 재연결을 진행함
    };

    return () => {
      console.log(`Closing SSE connection for symbol: ${symbol}`);
      eventSource.close();
    };
  }, [symbol, addRecentTrade]);
}
