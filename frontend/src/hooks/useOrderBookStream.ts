import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useTradeStore } from '../stores/useTradeStore';
import { OrderBook } from '../types';

export function useOrderBookStream(symbol: string) {
  const setOrderBook = useTradeStore((state) => state.setOrderBook);
  const stompClientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!symbol || typeof window === 'undefined') return;

    const socketUrl = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080/ws-tokit';
    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      reconnectDelay: 5000,
      debug: (str) => {
        console.log('[STOMP]', str);
      },
    });

    client.onConnect = () => {
      console.log(`Connected to STOMP WebSocket for order book symbol: ${symbol}`);
      
      // 호가창 토픽 구독
      client.subscribe(`/topic/orderbook/${symbol}`, (message) => {
        if (message.body) {
          try {
            const data: OrderBook = JSON.parse(message.body);
            setOrderBook(data);
          } catch (e) {
            console.error('Failed to parse order book websocket message', e);
          }
        }
      });
    };

    client.onStompError = (frame) => {
      console.error('STOMP broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    client.activate();
    stompClientRef.current = client;

    // Cleanup
    return () => {
      if (stompClientRef.current) {
        console.log(`Disconnecting STOMP client for symbol: ${symbol}`);
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
      }
    };
  }, [symbol, setOrderBook]);
}
