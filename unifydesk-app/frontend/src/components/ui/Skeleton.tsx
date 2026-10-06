type SkeletonProps = {
  className?: string
}

export function Skeleton({ className = '' }: SkeletonProps) {
  return <span aria-hidden="true" className={`skeleton-block block rounded-lg ${className}`} />
}

export function PageSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Loading page">
      <div className="space-y-2">
        <Skeleton className="h-7 w-56" />
        <Skeleton className="h-4 w-full max-w-xl" />
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <div key={index} className="skeleton-panel rounded-2xl border p-5">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="mt-4 h-8 w-32" />
            <Skeleton className="mt-3 h-3 w-full" />
          </div>
        ))}
      </div>
    </div>
  )
}

export function TableSkeleton({ rows = 6, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div
      className="skeleton-panel overflow-hidden rounded-2xl border"
      role="status"
      aria-label="Loading table"
    >
      <div
        className="skeleton-panel-muted grid gap-4 border-b px-5 py-3"
        style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
      >
        {Array.from({ length: columns }).map((_, index) => (
          <Skeleton key={index} className="h-3 w-20 max-w-full" />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, row) => (
        <div
          key={row}
          className="grid gap-4 border-b px-5 py-4 last:border-0"
          style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
        >
          {Array.from({ length: columns }).map((_, column) => (
            <Skeleton key={column} className={`h-4 ${column === 0 ? 'w-3/4' : 'w-2/3'}`} />
          ))}
        </div>
      ))}
    </div>
  )
}

export function ListSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <div
      className="skeleton-panel divide-y rounded-2xl border"
      role="status"
      aria-label="Loading list"
    >
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="flex items-center gap-3 p-4">
          <Skeleton className="size-10 shrink-0 rounded-full" />
          <div className="min-w-0 flex-1">
            <Skeleton className="h-4 w-40 max-w-2/3" />
            <Skeleton className="mt-2 h-3 w-64 max-w-full" />
          </div>
        </div>
      ))}
    </div>
  )
}

export function CardSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="skeleton-panel rounded-2xl border p-5" role="status" aria-label="Loading card">
      <Skeleton className="h-5 w-36" />
      {Array.from({ length: rows }).map((_, index) => (
        <Skeleton key={index} className={`mt-3 h-4 ${index === rows - 1 ? 'w-2/3' : 'w-full'}`} />
      ))}
    </div>
  )
}
