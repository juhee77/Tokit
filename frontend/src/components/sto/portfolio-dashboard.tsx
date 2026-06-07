'use client'

import { useState } from 'react'
import { TrendingUp, ArrowRight, Building2, Factory, Leaf, Briefcase } from 'lucide-react'
import { cn } from '@/lib/utils'

// Mock data
const portfolioData = {
  totalValue: 12450890.00,
  change: 4.2,
  changePeriod: '30d',
  allocation: [
    { name: 'Real Estate (REIT)', percentage: 65, color: 'bg-secondary' },
    { name: 'Private Equity', percentage: 25, color: 'bg-chart-2' },
    { name: 'Venture Debt', percentage: 10, color: 'bg-chart-3' },
  ],
  holdings: [
    { 
      id: 1,
      name: 'Hudson Yards Tower C',
      type: 'Commercial Real Estate',
      symbol: 'HYT-C',
      holdings: '12,500',
      price: '$102.45',
      priceChange: 2.3,
      totalValue: '$1,280,625.00',
      status: 'Active'
    },
    { 
      id: 2,
      name: 'Green Energy Fund I',
      type: 'Infrastructure',
      symbol: 'GEF-I',
      holdings: '8,200',
      price: '$87.20',
      priceChange: -1.2,
      totalValue: '$715,040.00',
      status: 'Active'
    },
    { 
      id: 3,
      name: 'Quantum Tech Growth II',
      type: 'Venture Capital',
      symbol: 'QTG-II',
      holdings: '5,000',
      price: '$156.80',
      priceChange: 5.8,
      totalValue: '$784,000.00',
      status: 'Lock-up'
    },
    { 
      id: 4,
      name: 'Seoul Office REIT',
      type: 'Real Estate',
      symbol: 'SOR-1',
      holdings: '25,000',
      price: '$45.60',
      priceChange: 0.5,
      totalValue: '$1,140,000.00',
      status: 'Active'
    },
  ],
}

const timeRanges = ['1D', '30D', 'YTD', 'ALL']

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
  }).format(value)
}

function getAssetIcon(type: string) {
  switch (type) {
    case 'Commercial Real Estate':
    case 'Real Estate':
      return Building2
    case 'Infrastructure':
      return Leaf
    case 'Venture Capital':
      return Factory
    default:
      return Briefcase
  }
}

export function PortfolioDashboard() {
  const [selectedRange, setSelectedRange] = useState('30D')

  return (
    <div className="space-y-6">
      {/* Top Row: Summary & Allocation */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* Total Asset Value Card */}
        <div className="col-span-1 lg:col-span-8 bg-card border border-outline-variant rounded p-4 md:p-6 flex flex-col shadow-sm">
          <div className="flex flex-col md:flex-row md:justify-between md:items-start mb-6 gap-4">
            <div>
              <h2 className="text-label-caps text-muted-foreground mb-1">Total Asset Value</h2>
              <div className="flex flex-col md:flex-row md:items-baseline gap-2 md:gap-3">
                <span className="text-display-lg font-bold text-foreground font-mono">
                  {formatCurrency(portfolioData.totalValue)}
                </span>
                <span className="text-sm text-secondary font-semibold flex items-center bg-secondary/10 px-2 py-0.5 rounded w-fit">
                  <TrendingUp className="w-3.5 h-3.5 mr-1" />
                  +{portfolioData.change}% ({portfolioData.changePeriod})
                </span>
              </div>
            </div>
            <div className="flex gap-2">
              {timeRanges.map((range) => (
                <button
                  key={range}
                  onClick={() => setSelectedRange(range)}
                  className={cn(
                    "px-3 py-1 text-xs font-semibold border rounded transition-colors",
                    selectedRange === range
                      ? "border-secondary text-secondary bg-surface-container-low"
                      : "border-outline-variant text-muted-foreground hover:border-secondary hover:text-secondary"
                  )}
                >
                  {range}
                </button>
              ))}
            </div>
          </div>

          {/* Minimalist Trend Graph */}
          <div className="flex-1 min-h-[160px] relative w-full border-b border-outline-variant mb-2">
            {/* Grid lines */}
            <div className="absolute inset-0 flex flex-col justify-between pointer-events-none opacity-20">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="w-full border-t border-outline" />
              ))}
            </div>
            {/* SVG Line Chart */}
            <svg className="w-full h-full" preserveAspectRatio="none" viewBox="0 0 800 160">
              <defs>
                <linearGradient id="chart-gradient" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stopColor="#0051d5" stopOpacity="0.3" />
                  <stop offset="100%" stopColor="#0051d5" stopOpacity="0" />
                </linearGradient>
              </defs>
              <path
                d="M0,140 Q100,130 200,90 T400,100 T600,40 T800,20 L800,160 L0,160 Z"
                fill="url(#chart-gradient)"
              />
              <path
                d="M0,140 Q100,130 200,90 T400,100 T600,40 T800,20"
                fill="none"
                stroke="#0051d5"
                strokeWidth="2"
              />
            </svg>
          </div>
          <div className="flex justify-between text-xs text-outline px-1">
            <span>Mar 1</span>
            <span>Mar 8</span>
            <span>Mar 15</span>
            <span>Mar 22</span>
            <span>Mar 29</span>
          </div>
        </div>

        {/* Allocation Card */}
        <div className="col-span-1 lg:col-span-4 bg-card border border-outline-variant rounded p-4 md:p-6 flex flex-col shadow-sm">
          <h2 className="text-headline-md font-bold text-foreground mb-6">Allocation</h2>
          
          {/* Simple Donut Chart */}
          <div className="flex items-center justify-center mb-6">
            <div className="w-28 h-28 md:w-32 md:h-32 rounded-full border-[12px] border-surface-container-highest relative flex items-center justify-center">
              <svg className="absolute inset-0 -rotate-90" viewBox="0 0 100 100">
                <circle
                  cx="50"
                  cy="50"
                  r="40"
                  fill="none"
                  stroke="#0051d5"
                  strokeWidth="12"
                  strokeDasharray="163.36 251.33"
                />
                <circle
                  cx="50"
                  cy="50"
                  r="40"
                  fill="none"
                  stroke="#0b1c30"
                  strokeWidth="12"
                  strokeDasharray="62.83 251.33"
                  strokeDashoffset="-163.36"
                />
                <circle
                  cx="50"
                  cy="50"
                  r="40"
                  fill="none"
                  stroke="#565e74"
                  strokeWidth="12"
                  strokeDasharray="25.13 251.33"
                  strokeDashoffset="-226.19"
                />
              </svg>
              <div className="text-center">
                <span className="block font-mono text-lg font-bold">{portfolioData.allocation.length + 1}</span>
                <span className="text-label-caps text-muted-foreground">Assets</span>
              </div>
            </div>
          </div>

          {/* Legend */}
          <div className="space-y-3">
            {portfolioData.allocation.map((item) => (
              <div key={item.name} className="flex justify-between items-center text-sm">
                <div className="flex items-center">
                  <div className={cn("w-3 h-3 rounded-sm mr-2", item.color)} />
                  <span className="text-foreground">{item.name}</span>
                </div>
                <span className="font-mono text-foreground font-semibold">{item.percentage}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Active STO Participations Table */}
      <div className="bg-card border border-outline-variant rounded shadow-sm overflow-hidden">
        <div className="p-4 md:p-6 border-b border-outline-variant flex flex-col md:flex-row md:justify-between md:items-center gap-4 bg-surface">
          <h2 className="text-headline-md font-bold text-foreground">Active STO Participations</h2>
          <button className="text-secondary text-sm font-semibold hover:underline flex items-center">
            View All <ArrowRight className="w-4 h-4 ml-1" />
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse min-w-[640px]">
            <thead>
              <tr className="border-b border-outline-variant bg-surface-container-low text-label-caps text-muted-foreground">
                <th className="py-2.5 px-4 font-normal">Asset Name</th>
                <th className="py-2.5 px-4 font-normal text-right">Holdings</th>
                <th className="py-2.5 px-4 font-normal text-right">Current Price</th>
                <th className="py-2.5 px-4 font-normal text-right">Total Value</th>
                <th className="py-2.5 px-4 font-normal text-center">Status</th>
              </tr>
            </thead>
            <tbody className="text-sm">
              {portfolioData.holdings.map((holding) => {
                const Icon = getAssetIcon(holding.type)
                return (
                  <tr 
                    key={holding.id} 
                    className="border-b border-surface-container-highest hover:bg-surface-container-low transition-colors cursor-pointer"
                  >
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded bg-surface-container flex items-center justify-center border border-surface-variant">
                          <Icon className="w-5 h-5 text-muted-foreground" />
                        </div>
                        <div>
                          <p className="font-semibold text-foreground">{holding.name}</p>
                          <p className="text-xs text-muted-foreground">{holding.type} • {holding.symbol}</p>
                        </div>
                      </div>
                    </td>
                    <td className="py-3 px-4 text-right font-mono text-foreground">{holding.holdings}</td>
                    <td className="py-3 px-4 text-right">
                      <span className="font-mono text-foreground">{holding.price}</span>
                      <span className={cn(
                        "ml-2 text-xs",
                        holding.priceChange >= 0 ? "text-gain" : "text-loss"
                      )}>
                        {holding.priceChange >= 0 ? '+' : ''}{holding.priceChange}%
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right font-mono font-semibold text-foreground">{holding.totalValue}</td>
                    <td className="py-3 px-4 text-center">
                      <span className={cn(
                        "px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border",
                        holding.status === 'Active'
                          ? "border-secondary text-secondary bg-secondary/10"
                          : "border-outline text-muted-foreground bg-surface-container"
                      )}>
                        {holding.status}
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
