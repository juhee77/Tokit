export interface Asset {
  id: number;
  symbol: string;
  name: string;
  contractAddress: string;
  totalSupply: number;
}

export interface OrderBookEntry {
  price: number;
  quantity: number;
}

export interface OrderBook {
  symbol: string;
  bids: OrderBookEntry[];
  asks: OrderBookEntry[];
}

export interface Trade {
  id: number;
  buyOrderId: number;
  sellOrderId: number;
  assetSymbol: string;
  price: number;
  quantity: number;
  createdAt: string;
}

export type OrderType = 'BUY' | 'SELL';
export type OrderStatus = 'PENDING' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELED';

export interface Order {
  id: number;
  memberId: number;
  assetSymbol: string;
  orderType: OrderType;
  price: number;
  quantity: number;
  remainingQuantity: number;
  status: OrderStatus;
  createdAt: string;
}
