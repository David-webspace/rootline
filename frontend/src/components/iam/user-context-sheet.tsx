"use client";

import type { ReactNode } from "react";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { usePermissionContext } from "@/hooks/use-iam";
import { authority } from "@/lib/iam-types";
import { Chip, LoadError, StatusBadge } from "./parts";

/** 使用者的完整權限脈絡：個人角色、各群組繼承的角色、展開後的權限、所屬部門。 */
export function UserContextSheet({ username, onClose }: { username: string | null; onClose: () => void }) {
  const query = usePermissionContext(username);
  const ctx = query.data;

  return (
    <Sheet open={!!username} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle className="truncate">{username}</SheetTitle>
          <SheetDescription>權限脈絡（實際生效的角色與權限）</SheetDescription>
        </SheetHeader>
        <div className="grid gap-6 px-4 pb-6">
          {query.isPending && <Skeleton className="h-40 w-full" />}
          {query.isError && <LoadError error={query.error} onRetry={() => query.refetch()} />}
          {ctx && (
            <>
              <div className="flex min-w-0 flex-wrap items-center gap-2 text-sm">
                <span className="truncate text-muted-foreground">{ctx.email}</span>
                <StatusBadge status={ctx.status} />
              </div>

              <Section title="個人角色" empty="沒有直接指派的角色">
                {ctx.personalRoles.map((r) => <Chip key={r} mono>{r}</Chip>)}
              </Section>

              <Section title="群組繼承" empty="不屬於任何群組">
                {ctx.groupRoles.map((g) => (
                  <div key={g.groupCode} className="grid w-full gap-1.5 rounded-md border border-border p-3">
                    <span className="truncate text-sm font-medium">{g.groupName}<span className="ml-2 font-mono text-xs text-muted-foreground">{g.groupCode}</span></span>
                    <div className="flex flex-wrap gap-1.5">
                      {g.roleCodes.length ? g.roleCodes.map((r) => <Chip key={r} mono>{r}</Chip>)
                        : <span className="text-xs text-muted-foreground">此群組沒有角色</span>}
                    </div>
                  </div>
                ))}
              </Section>

              <Section title={`生效權限（${ctx.permissions.length}）`} empty="沒有任何權限">
                {ctx.permissions.map((p) => (
                  <div key={authority(p)} className="flex w-full min-w-0 items-baseline justify-between gap-3 text-sm">
                    <span className="truncate">{p.permissionName}</span>
                    <span className="shrink-0 font-mono text-xs text-muted-foreground">{authority(p)}</span>
                  </div>
                ))}
              </Section>

              <Section title="所屬部門" empty="尚未指派部門" note="由組織樹同步，指派後約數百毫秒出現">
                {ctx.departments.map((d) => <Chip key={d.id}>{d.name}</Chip>)}
              </Section>
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

function Section({ title, empty, note, children }: { title: string; empty: string; note?: string; children: ReactNode[] }) {
  return (
    <section className="grid gap-2">
      <h3 className="text-xs font-medium tracking-wide text-muted-foreground uppercase">{title}</h3>
      <div className="flex flex-wrap gap-1.5">
        {children.length ? children : <span className="text-sm text-muted-foreground">{empty}</span>}
      </div>
      {note && <p className="text-xs text-muted-foreground/70">{note}</p>}
    </section>
  );
}
