'use client'

import { useState } from 'react'
import { Filter, ArrowUpDown, Building2, Factory, Leaf, Briefcase } from 'lucide-react'
import { cn } from '@/lib/utils'

const categories = ['All Assets', 'Real Estate', 'Private Equity', 'Infrastructure', 'Venture']

const offerings = [
  {
    id: 1,
    name: 'Hudson Yards Tower C',
    type: 'Commercial Real Estate',
    category: 'Core+',
    targetIrr: '12.4%',
    minInvestment: '$50,000',
    term: '5 Yrs',
    raised: 42500000,
    target: 50000000,
    status: 'Funding',
    icon: Building2,
  },
  {
    id: 2,
    name: 'Quantum Tech Growth Fund II',
    type: 'Private Equity',
    category: 'Venture',
    targetIrr: '24.0%',
    minInvestment: '$100,000',
    term: '7 Yrs',
    raised: 0,
    target: 150000000,
    status: 'Upcoming',
    daysUntil: 14,
    icon: Factory,
  },
  {
    id: 3,
    name: 'Green Energy Infrastructure',
    type: 'Infrastructure',
    category: 'Sustainable',
    targetIrr: '9.5%',
    minInvestment: '$25,000',
    term: '10 Yrs',
    raised: 180000000,
    target: 200000000,
    status: 'Funding',
    icon: Leaf,
  },
  {
    id: 4,
    name: 'Seoul Office REIT I',
    type: 'Real Estate',
    category: 'Core',
    targetIrr: '8.2%',
    minInvestment: '$10,000',
    term: '3 Yrs',
    raised: 75000000,
    target: 75000000,
    status: 'Funded',
    icon: Building2,
  },
]

function formatCurrency(value: number): string {
  if (value >= 1000000) {
    return `$${(value / 1000000).toFixed(1)}M`
  }
  return `$${(value / 1000).toFixed(0)}K`
}

export default function MarketsPage() {
  const [selectedCategory, setSelectedCategory] = useState('All Assets')

  const filteredOfferings = selectedCategory === 'All Assets' 
    ? offerings 
    : offerings.filter(o => o.type.toLowerCase().includes(selectedCategory.toLowerCase().replace(' ', '')))

  return (
    <div className="max-w-[1440px] mx-auto space-y-6">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:justify-between md:items-end gap-4">
        <div>
          <h2 className="text-headline-md font-bold text-foreground mb-1">Primary Markets</h2>
          <p className="text-sm text-muted-foreground">Explore currently funding Security Token Offerings.</p>
        </div>
        <div className="flex gap-3">
          <button className="flex items-center gap-2 px-4 py-2 border border-outline-variant rounded bg-card text-foreground text-sm hover:bg-surface-container-low transition-colors">
            <Filter className="w-4 h-4" />
            Filters
          </button>
          <button className="flex items-center gap-2 px-4 py-2 border border-outline-variant rounded bg-card text-foreground text-sm hover:bg-surface-container-low transition-colors">
            <ArrowUpDown className="w-4 h-4" />
            Sort: Ending Soon
          </button>
        </div>
      </div>

      {/* Filter Chips */}
      <div className="flex gap-2 overflow-x-auto pb-2">
        {categories.map((category) => (
          <button
            key={category}
            onClick={() => setSelectedCategory(category)}
            className={cn(
              "px-4 py-1.5 rounded-full border text-sm cursor-pointer whitespace-nowrap transition-colors",
              selectedCategory === category
                ? "border-secondary bg-secondary/10 text-secondary font-medium"
                : "border-outline-variant bg-card text-muted-foreground hover:bg-surface-container-low"
            )}
          >
            {category}
          </button>
        ))}
      </div>

      {/* Grid Layout for Offerings */}
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        {filteredOfferings.map((offering) => {
          const Icon = offering.icon
          const progress = (offering.raised / offering.target) * 100

          return (
            <div 
              key={offering.id}
              className="bg-card border border-outline-variant rounded p-4 flex flex-col shadow-sm hover:border-secondary transition-colors cursor-pointer group"
            >
              <div className="flex justify-between items-start mb-4">
                <div className="flex gap-4 items-center">
                  <div className="w-12 h-12 rounded bg-surface-container flex items-center justify-center border border-surface-variant">
                    <Icon className="w-6 h-6 text-muted-foreground" />
                  </div>
                  <div>
                    <h3 className="text-title-sm font-semibold text-foreground group-hover:text-secondary transition-colors">
                      {offering.name}
                    </h3>
                    <p className="text-sm text-muted-foreground">{offering.type} • {offering.category}</p>
                  </div>
                </div>
                <span className={cn(
                  "px-2 py-0.5 text-[10px] uppercase font-bold tracking-wider rounded border",
                  offering.status === 'Funding'
                    ? "border-secondary text-secondary bg-secondary/10"
                    : offering.status === 'Funded'
                      ? "border-green-600 text-green-600 bg-green-50"
                      : "border-outline text-muted-foreground bg-surface-container"
                )}>
                  {offering.status}
                </span>
              </div>

              <div className="grid grid-cols-3 gap-4 mb-4 border-y border-surface-container-highest py-3">
                <div>
                  <p className="text-label-caps text-muted-foreground mb-1">Target IRR</p>
                  <p className="font-mono text-foreground text-base font-semibold">{offering.targetIrr}</p>
                </div>
                <div>
                  <p className="text-label-caps text-muted-foreground mb-1">Min Inv.</p>
                  <p className="font-mono text-foreground text-base font-semibold">{offering.minInvestment}</p>
                </div>
                <div className="text-right">
                  <p className="text-label-caps text-muted-foreground mb-1">Term</p>
                  <p className="font-mono text-foreground text-base font-semibold">{offering.term}</p>
                </div>
              </div>

              <div className="mt-auto">
                <div className="flex justify-between text-sm mb-1">
                  <span className="text-foreground font-medium">{formatCurrency(offering.raised)} Raised</span>
                  <span className="text-muted-foreground">of {formatCurrency(offering.target)} Target</span>
                </div>
                {/* Progress Bar */}
                <div className="w-full h-1 bg-surface-container-highest rounded overflow-hidden">
                  <div 
                    className={cn(
                      "h-full transition-all",
                      offering.status === 'Funded' ? "bg-green-600" : "bg-secondary"
                    )}
                    style={{ width: `${Math.min(progress, 100)}%` }}
                  />
                </div>
                {offering.daysUntil && (
                  <p className="text-sm text-muted-foreground mt-2 text-right">
                    Opens in {offering.daysUntil} Days
                  </p>
                )}
              </div>
            </div>
          )
        })}
      </div>

      {/* Load More */}
      <div className="flex justify-center mt-8">
        <button className="px-6 py-2 border border-outline-variant bg-card text-foreground text-sm hover:bg-surface-container-low transition-colors rounded">
          Load More Assets
        </button>
      </div>
    </div>
  )
}
