"use client";

import { CalendarClock, RotateCcw } from "lucide-react";
import { useState } from "react";
import { LoadError, TableSkeleton } from "@/components/iam/parts";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { describeDeptError, useDeptEvents, useDeptStateAt } from "@/hooks/use-dept";
import type { DeptState } from "@/lib/dept-types";
import { cn } from "@/lib/utils";
import { describeEvent, formatTime, toLocalInput } from "./event-labels";

/**
 * 時光機：左邊是這個部門的完整事件時間軸，右邊是任一時間點重播出來的狀態，
 * 並標出與現在不同的欄位。點時間軸上的事件會直接跳到那一刻。
 */
export function TimeMachine({
  departmentId,
  current,
  nameOf,
}: {
  departmentId: string;
  current: DeptState | undefined;
  nameOf: (id: string | null) => string;
}) {
  const events = useDeptEvents(departmentId);
  // at 保留後端原始字串（微秒），自訂時間則取該秒的最後一毫秒，讓同一秒內的事件都算進去
  const [at, setAt] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const snapshot = useDeptStateAt(departmentId, at);

  if (events.isPending) return <TableSkeleton rows={4} />;
  if (events.isError) return <LoadError error={events.error} onRetry={() => events.refetch()} />;

  const timeline = [...events.data].reverse();

  return (
    <div className="grid gap-4">
      <form
        className="flex flex-col gap-2 sm:flex-row sm:items-end"
        onSubmit={(event) => {
          event.preventDefault();
          if (!draft) return;
          setAt(new Date(new Date(draft).getTime() + 999).toISOString());
        }}
      >
        <label className="grid min-w-0 flex-1 gap-1.5 text-sm">
          <span className="text-muted-foreground">指定時間點</span>
          <Input type="datetime-local" step={1} value={draft} onChange={(e) => setDraft(e.target.value)} />
        </label>
        <div className="flex gap-2">
          <Button type="submit" variant="secondary" disabled={!draft}>
            <CalendarClock aria-hidden /> 回到這一刻
          </Button>
          {at && (
            <Button type="button" variant="ghost" onClick={() => { setAt(null); setDraft(""); }}>
              <RotateCcw aria-hidden /> 回到現在
            </Button>
          )}
        </div>
      </form>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <section aria-label="事件時間軸" className="min-w-0">
          <h3 className="mb-2 text-sm font-medium">事件時間軸（{events.data.length}）</h3>
          <ol className="relative grid gap-1 border-l border-border pl-4">
            {timeline.map((entry) => {
              const d = describeEvent(entry, nameOf);
              const active = at === entry.occurredAt;
              return (
                <li key={entry.version} className="relative min-w-0">
                  <span
                    aria-hidden
                    className={cn(
                      "absolute top-3 -left-[1.3rem] size-2.5 rounded-full border-2 border-background",
                      d.tone === "positive" ? "bg-success" : d.tone === "negative" ? "bg-destructive" : "bg-muted-foreground",
                      active && "ring-2 ring-primary",
                    )}
                  />
                  <button
                    type="button"
                    onClick={() => { setAt(entry.occurredAt); setDraft(toLocalInput(entry.occurredAt)); }}
                    aria-pressed={active}
                    className={cn(
                      "grid w-full min-w-0 gap-0.5 rounded-md px-2 py-1.5 text-left transition-colors hover:bg-accent/60 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
                      active && "bg-primary/12",
                    )}
                  >
                    <span className="flex min-w-0 items-center gap-2 text-sm">
                      <span className="font-medium">{d.title}</span>
                      <span className="font-mono text-[11px] text-muted-foreground">v{entry.version}</span>
                    </span>
                    {d.detail && <span className="text-xs break-words text-muted-foreground">{d.detail}</span>}
                    <span className="text-[11px] text-muted-foreground tabular-nums">
                      {formatTime(entry.occurredAt)} · {entry.operator}
                    </span>
                  </button>
                </li>
              );
            })}
          </ol>
        </section>

        <section aria-label="指定時間點的狀態" className="min-w-0">
          <h3 className="mb-2 text-sm font-medium">
            {at ? `${formatTime(at)} 的狀態` : "選一個事件或時間點，看當時的部門狀態"}
          </h3>
          {at && snapshot.isPending && <Skeleton className="h-48 w-full" />}
          {at && snapshot.isError && (
            <p role="alert" className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground">
              {describeDeptError(snapshot.error)}
            </p>
          )}
          {snapshot.data && <StateCompare past={snapshot.data} current={current} nameOf={nameOf} />}
        </section>
      </div>
    </div>
  );
}

function StateCompare({
  past,
  current,
  nameOf,
}: {
  past: DeptState;
  current: DeptState | undefined;
  nameOf: (id: string | null) => string;
}) {
  const describeStatus = (s: DeptState) => (s.deleted ? "已刪除" : s.status === "ACTIVE" ? "啟用" : "停用");
  const rows: { label: string; then: string; now?: string }[] = [
    { label: "名稱", then: past.name, now: current?.name },
    { label: "代碼", then: past.code, now: current?.code },
    { label: "上層", then: nameOf(past.parentId), now: current && nameOf(current.parentId) },
    { label: "狀態", then: describeStatus(past), now: current && describeStatus(current) },
    { label: "成員", then: past.employees.join("、") || "（無）", now: current && (current.employees.join("、") || "（無）") },
    { label: "版本", then: `v${past.version}`, now: current && `v${current.version}` },
  ];
  return (
    <dl className="grid gap-px overflow-hidden rounded-lg border border-border bg-border text-sm">
      {rows.map((row) => {
        const changed = row.now !== undefined && row.now !== row.then;
        return (
          <div key={row.label} className="grid grid-cols-[4.5rem_minmax(0,1fr)] bg-card">
            <dt className="px-3 py-2 text-muted-foreground">{row.label}</dt>
            <dd className="grid min-w-0 gap-1 px-3 py-2">
              <span className="break-words">{row.then}</span>
              {changed && (
                <span className="flex min-w-0 items-start gap-1.5 text-xs text-warning">
                  <Badge variant="outline" className="shrink-0 border-warning/40 px-1 text-[10px] text-warning">現在</Badge>
                  <span className="break-words">{row.now}</span>
                </span>
              )}
            </dd>
          </div>
        );
      })}
    </dl>
  );
}
