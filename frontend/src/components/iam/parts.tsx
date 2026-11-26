"use client";

import { AlertTriangle, ChevronLeft, ChevronRight, X } from "lucide-react";
import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api";
import type { UserStatus } from "@/lib/iam-types";
import { cn } from "@/lib/utils";

const STATUS: Record<UserStatus, { label: string; className: string }> = {
  ACTIVE: { label: "啟用", className: "border-success/30 bg-success/10 text-success" },
  LOCKED: { label: "已鎖定", className: "border-warning/30 bg-warning/10 text-warning" },
  DEACTIVATED: { label: "已停用", className: "border-border bg-muted text-muted-foreground" },
};

export function StatusBadge({ status }: { status: UserStatus }) {
  const s = STATUS[status] ?? STATUS.DEACTIVATED;
  return <Badge variant="outline" className={s.className}>{s.label}</Badge>;
}

/** 可移除的代碼標籤（角色、權限、成員）。onRemove 為 undefined 時只讀。 */
export function Chip({ children, onRemove, removeLabel, mono }: {
  children: ReactNode;
  onRemove?: () => void;
  removeLabel?: string;
  mono?: boolean;
}) {
  return (
    <span className={cn("inline-flex max-w-full min-w-0 items-center gap-1 rounded-md border border-border bg-secondary/60 py-0.5 pr-1 pl-2 text-xs",
      mono && "font-mono", !onRemove && "pr-2")}>
      <span className="truncate">{children}</span>
      {onRemove && (
        <button type="button" onClick={onRemove} aria-label={removeLabel}
          className="rounded-sm p-0.5 text-muted-foreground hover:bg-destructive/15 hover:text-destructive focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
          <X className="size-3" aria-hidden />
        </button>
      )}
    </span>
  );
}

export function SectionHeader({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h2 className="text-base font-semibold">{title}</h2>
        {description && <p className="text-sm break-words text-muted-foreground">{description}</p>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

export function TableSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="grid gap-2" aria-label="載入中">
      {Array.from({ length: rows }).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}
    </div>
  );
}

export function LoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const forbidden = error instanceof ApiError && error.status === 403;
  return (
    <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm sm:flex-row sm:items-center">
      <AlertTriangle className="size-4 shrink-0 text-destructive" aria-hidden />
      <p className="min-w-0 flex-1 break-words">
        {forbidden ? "你沒有檢視這個項目的權限。" : error instanceof ApiError ? error.message : "載入失敗"}
      </p>
      {!forbidden && <Button size="sm" variant="outline" onClick={onRetry}>重試</Button>}
    </div>
  );
}

export function EmptyRow({ colSpan, children }: { colSpan: number; children: ReactNode }) {
  return (
    <tr>
      <td colSpan={colSpan} className="py-10 text-center text-sm text-muted-foreground">{children}</td>
    </tr>
  );
}

/** 表格分頁：顯示目前範圍與上一頁 / 下一頁。page 從 0 開始。 */
export function Pager({ page, size, total, onChange }: {
  page: number;
  size: number;
  total: number;
  onChange: (page: number) => void;
}) {
  if (total <= size) return null;
  const pages = Math.ceil(total / size);
  const from = page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return (
    <nav aria-label="分頁" className="flex items-center justify-between gap-3 text-sm text-muted-foreground">
      <span className="tabular-nums">第 {from}–{to} 筆，共 {total} 筆</span>
      <div className="flex items-center gap-1">
        <Button variant="outline" size="icon" aria-label="上一頁" disabled={page === 0} onClick={() => onChange(page - 1)}>
          <ChevronLeft className="size-4" />
        </Button>
        <span className="px-2 tabular-nums">{page + 1} / {pages}</span>
        <Button variant="outline" size="icon" aria-label="下一頁" disabled={page >= pages - 1} onClick={() => onChange(page + 1)}>
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </nav>
  );
}
