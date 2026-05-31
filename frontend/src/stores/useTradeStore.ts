import { create } from 'zustand';
import { Asset, OrderBook, Trade } from '../types';

interface TradeState {
  selectedSymbol: string;
  assets: Asset[];
  orderBook: OrderBook;
  recentTrades: Trade[];
  loading: boolean;
  
  setSelectedSymbol: (symbol: string) => void;
  setAssets: (assets: Asset[]) => void;
  setOrderBook: (orderBook: OrderBook) => void;
  updateOrderBook: (updater: (prev: OrderBook) => OrderBook) => void;
  setRecentTrades: (trades: Trade[]) => void;
  addRecentTrade: (trade: Trade) => void;
  setLoading: (loading: boolean) => void;
}

const initialOrderBook: OrderBook = {
  symbol: '',
  bids: [],
  asks: [],
};

export const useTradeStore = create<TradeState>((set) => ({
  selectedSymbol: 'APPL-STO', // 기본 선택 자산
  assets: [],
  orderBook: initialOrderBook,
  recentTrades: [],
  loading: false,

  setSelectedSymbol: (symbol) => set({ selectedSymbol: symbol, orderBook: { ...initialOrderBook, symbol } }),
  setAssets: (assets) => set({ assets }),
  setOrderBook: (orderBook) => set({ orderBook }),
  updateOrderBook: (updater) => set((state) => ({ orderBook: updater(state.orderBook) })),
  setRecentTrades: (trades) => set({ recentTrades: trades }),
  addRecentTrade: (trade) => set((state) => ({
    recentTrades: [trade, ...state.recentTrades.slice(0, 49)], // 최신 50개 유지
  })),
  setLoading: (loading) => set({ loading }),
}));
