"use client";

import { ChevronRight, History, UserPlus } from "lucide-react";
import { useState } from "react";
import { ConfirmDialog, FormDialog, SelectDialog } from "@/components/iam/dialogs";
import { Chip, LoadError } from "@/components/iam/parts";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useDeptMutation, useDeptState } from "@/hooks/use-dept";
import { path, useUsers } from "@/hooks/use-iam";
import { api } from "@/lib/api";
import type { TreeNode } from "@/lib/dept-types";
import { formatTime } from "./event-labels";
import { TimeMachine } from "./time-machine";
import { pathTo, type TreeIndex } from "./tree-utils";

export type DetailTab = "overview" | "history";

export function DepartmentDetail({
  node,
  index,
  tab,
  onTabChange,
  onSelect,
  onRestore,
  canWrite,
  canStaff,
  canReadUsers,
}: {
  node: TreeNode;
  index: TreeIndex;
  tab: DetailTab;
  onTabChange: (tab: DetailTab) => void;
  onSelect: (id: string) => void;
  onRestore: (node: TreeNode) => void;
  canWrite: boolean;
  canStaff: boolean;
  canReadUsers: boolean;
}) {
  const state = useDeptState(node.id);
  const nameOf = (id: string | null) => (id === null ? "（最上層）" : index.byId.get(id)?.name ?? id);
  const crumbs = pathTo(index, node.id);

  return (
    <Card className="min-w-0 gap-4">
      <CardHeader className="gap-2">
        <nav aria-label="部門路徑" className="flex min-w-0 flex-wrap items-center gap-1 text-xs text-muted-foreground">
          {crumbs.map((c, i) => (
            <span key={c.id} className="flex min-w-0 items-center gap-1">
              {i > 0 && <ChevronRight className="size-3 shrink-0" aria-hidden />}
              {i === crumbs.length - 1 ? (
                <span className="truncate text-foreground">{c.name}</span>
              ) : (
                <button type="button" onClick={() => onSelect(c.id)} className="truncate hover:text-foreground hover:underline">
                  {c.name}
                </button>
              )}
            </span>
          ))}
        </nav>
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <h2 className="min-w-0 text-lg font-semibold break-words">{node.name}</h2>
          <span className="font-mono text-xs text-muted-foreground">{node.code}</span>
          {node.deleted ? (
            <Badge variant="outline" className="border-destructive/40 text-destructive">已刪除</Badge>
          ) : node.status === "DISABLED" ? (
            <Badge variant="outline" className="text-muted-foreground">停用</Badge>
          ) : (
            <Badge variant="outline" className="border-success/30 bg-success/10 text-success">啟用</Badge>
          )}
        </div>
      </CardHeader>

      <CardContent className="grid gap-4">
        {node.deleted && (
          <div className="flex flex-col gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm sm:flex-row sm:items-center">
            <p className="min-w-0 flex-1 break-words">
              這個部門已被刪除。事件都還在，可以用時光機把它連同已刪除的子部門一起復活。
            </p>
            {canWrite && (
              <Button size="sm" onClick={() => onRestore(node)}>
                <History aria-hidden /> 時光機復活
              </Button>
            )}
          </div>
        )}

        <Tabs value={tab} onValueChange={(v) => onTabChange(v as DetailTab)} className="gap-4">
          <TabsList>
            <TabsTrigger value="overview">概要</TabsTrigger>
            <TabsTrigger value="history">時光機</TabsTrigger>
          </TabsList>
          <TabsContent value="overview" className="grid gap-4">
            <dl className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <Stat label="本部門人數" value={node.directEmployeeCount} />
              <Stat label="含子部門人數" value={node.totalEmployeeCount} />
              <Stat label="直屬子部門" value={node.children.length} />
              <Stat label="事件版本" value={state.data ? `v${state.data.version}` : "—"} />
            </dl>
            {state.isError ? (
              <LoadError error={state.error} onRetry={() => state.refetch()} />
            ) : (
              <Members
                departmentId={node.id}
                employees={state.data?.employees}
                editable={canStaff && !node.deleted && node.status === "ACTIVE"}
                canReadUsers={canReadUsers}
              />
            )}
            {state.data && (
              <p className="text-xs text-muted-foreground">
                最後修改：{formatTime(state.data.lastModifiedAt)} · {state.data.lastModifiedBy}
              </p>
            )}
          </TabsContent>
          <TabsContent value="history">
            {/* 換部門時重新掛載，時間點選擇不會帶到別的部門 */}
            <TimeMachine key={node.id} departmentId={node.id} current={state.data} nameOf={nameOf} />
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="min-w-0 rounded-lg border border-border bg-muted/30 px-3 py-2">
      <dt className="truncate text-xs text-muted-foreground">{label}</dt>
      <dd className="text-lg font-semibold tabular-nums">{value}</dd>
    </div>
  );
}

function Members({
  departmentId,
  employees,
  editable,
  canReadUsers,
}: {
  departmentId: string;
  employees: string[] | undefined;
  editable: boolean;
  canReadUsers: boolean;
}) {
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<string | null>(null);
  const users = useUsers(editable && canReadUsers && adding);
  const assign = useDeptMutation(
    (employeeId: string) => api(`departments/${path(departmentId)}/employees`, { method: "POST", body: { employeeId } }),
    (employeeId) => `已將 ${employeeId} 加入部門`,
  );
  const unassign = useDeptMutation(
    (employeeId: string) => api(`departments/${path(departmentId, "employees", employeeId)}`, { method: "DELETE" }),
    (employeeId) => `已將 ${employeeId} 移出部門`,
  );

  return (
    <section aria-label="部門成員" className="grid gap-2">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-medium">成員{employees ? `（${employees.length}）` : ""}</h3>
        {editable && (
          <Button size="sm" variant="outline" onClick={() => setAdding(true)}>
            <UserPlus aria-hidden /> 加入成員
          </Button>
        )}
      </div>
      {!employees ? (
        <Skeleton className="h-8 w-full" />
      ) : employees.length === 0 ? (
        <p className="text-sm text-muted-foreground">這個部門目前沒有直屬成員。</p>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {employees.map((e) => (
            <Chip key={e} mono onRemove={editable ? () => setRemoving(e) : undefined} removeLabel={`將 ${e} 移出部門`}>
              {e}
            </Chip>
          ))}
        </div>
      )}

      {canReadUsers ? (
        <SelectDialog
          open={adding}
          onOpenChange={setAdding}
          title="加入成員"
          description="成員的部門會同步到身分服務，權限脈絡約一秒內更新。"
          label="成員"
          options={(users.data ?? [])
            .filter((u) => u.status === "ACTIVE" && !employees?.includes(u.username))
            .map((u) => ({ value: u.username, label: u.username, detail: u.email }))}
          emptyText={users.isPending ? "載入中…" : "沒有可加入的成員。"}
          submitLabel="加入"
          pending={assign.isPending}
          onSubmit={(username) => assign.mutateAsync(username)}
        />
      ) : (
        <FormDialog
          open={adding}
          onOpenChange={setAdding}
          title="加入成員"
          fields={[{ name: "employeeId", label: "帳號", placeholder: "例如 mia.chen" }]}
          submitLabel="加入"
          pending={assign.isPending}
          onSubmit={(v) => assign.mutateAsync(v.employeeId.trim())}
        />
      )}

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => !open && setRemoving(null)}
        title="移出部門"
        description={`確定將 ${removing} 移出這個部門？`}
        confirmLabel="移出"
        destructive
        onConfirm={() => removing && unassign.mutate(removing)}
      />
    </section>
  );
}
