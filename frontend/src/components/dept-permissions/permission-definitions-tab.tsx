"use client";

import { useQuery } from "@tanstack/react-query";
import { Pencil, Plus } from "lucide-react";
import { useState } from "react";
import { FormDialog } from "@/components/iam/dialogs";
import { EmptyRow, LoadError, Pager, SectionHeader, TableSkeleton } from "@/components/iam/parts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAction } from "@/hooks/use-action";
import { path } from "@/hooks/use-iam";
import { api } from "@/lib/api";
import type { PermissionDefinition } from "@/lib/platform-types";

const PAGE_SIZE = 20;

export const PERMISSION_ERRORS: Record<string, string> = {
  PERMISSION_INVALID: "系統代碼或權限代碼格式不正確",
  PERMISSION_CODE_RESERVED: "ADMIN_ALL 是萬用權限的保留字，不能自行定義",
  PERMISSION_NAME_INVALID: "名稱必填，最多 100 字",
  PERMISSION_DESCRIPTION_INVALID: "說明最多 500 字",
  PERMISSION_NOT_FOUND: "找不到這個權限",
  DUPLICATE_RESOURCE: "這個權限已經定義過",
  CONCURRENT_MODIFICATION: "資料剛被其他人修改，請重新整理後再試",
};

const SYSTEM = /^[A-Z][A-Z0-9_]{1,31}$/;
const CODE = /^[A-Z][A-Z0-9_]{1,63}$/;

export function PermissionDefinitionsTab({ canWrite }: { canWrite: boolean }) {
  const list = useQuery({
    queryKey: ["dept-permissions"],
    queryFn: () => api<PermissionDefinition[]>("departments/permissions"),
  });
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<PermissionDefinition | null>(null);

  // 權限也會投影到身分服務的權限字典，讓角色可以授予
  const invalidate = [["dept-permissions"], ["iam", "dictionary"]];
  const define = useAction(
    (v: Record<string, string>) => api("departments/permissions", { method: "POST", body: v }),
    { success: (v) => `已定義 ${v.systemCode}:${v.permissionCode}，約一秒內可在角色授權中選用`, invalidate, errors: PERMISSION_ERRORS },
  );
  const update = useAction(
    (v: { systemCode: string; permissionCode: string; name: string; description: string }) =>
      api(`departments/permissions/${path(v.systemCode, v.permissionCode)}`, {
        method: "PUT",
        body: { name: v.name, description: v.description },
      }),
    { success: "已更新權限說明", invalidate, errors: PERMISSION_ERRORS },
  );

  const needle = keyword.trim().toLowerCase();
  const filtered = (list.data ?? []).filter(
    (p) => !needle || `${p.systemCode}:${p.permissionCode} ${p.name} ${p.description ?? ""}`.toLowerCase().includes(needle),
  );
  const current = Math.min(page, Math.max(0, Math.ceil(filtered.length / PAGE_SIZE) - 1));
  const rows = filtered.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE);

  return (
    <div className="grid gap-4">
      <SectionHeader
        title="權限定義"
        description="每個權限都是一個有版本的聚合，異動以事件發出，身分服務的權限字典會跟著更新。"
        action={canWrite && (
          <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />定義權限</Button>
        )}
      />
      <Input
        aria-label="搜尋權限"
        placeholder="搜尋代碼、名稱或說明"
        value={keyword}
        onChange={(e) => { setKeyword(e.target.value); setPage(0); }}
        className="sm:max-w-xs"
      />

      {list.isPending ? <TableSkeleton /> : list.isError ? (
        <LoadError error={list.error} onRetry={() => list.refetch()} />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>權限</TableHead>
                  <TableHead>名稱</TableHead>
                  <TableHead className="hidden md:table-cell">說明</TableHead>
                  <TableHead className="hidden sm:table-cell">版本</TableHead>
                  {canWrite && <TableHead className="w-12"><span className="sr-only">操作</span></TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.length === 0 && (
                  <EmptyRow colSpan={canWrite ? 5 : 4}>{needle ? "沒有符合的權限" : "還沒有自訂權限"}</EmptyRow>
                )}
                {rows.map((p) => (
                  <TableRow key={`${p.systemCode}:${p.permissionCode}`}>
                    <TableCell className="max-w-64 truncate font-mono text-xs">{p.systemCode}:{p.permissionCode}</TableCell>
                    <TableCell className="max-w-48 truncate font-medium">{p.name}</TableCell>
                    <TableCell className="hidden max-w-80 truncate text-muted-foreground md:table-cell" title={p.description ?? undefined}>
                      {p.description || "—"}
                    </TableCell>
                    <TableCell className="hidden text-muted-foreground tabular-nums sm:table-cell">v{p.version}</TableCell>
                    {canWrite && (
                      <TableCell>
                        <Button variant="ghost" size="icon" aria-label={`編輯 ${p.systemCode}:${p.permissionCode}`} onClick={() => setEditing(p)}>
                          <Pencil className="size-4" />
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager page={current} size={PAGE_SIZE} total={filtered.length} onChange={setPage} />
        </>
      )}

      <FormDialog
        open={creating}
        onOpenChange={setCreating}
        title="定義權限"
        description="系統代碼與權限代碼建立後不能修改，用大寫英數與底線。"
        submitLabel="定義"
        pending={define.isPending}
        onSubmit={(v) => define.mutateAsync({
          systemCode: v.systemCode.trim().toUpperCase(),
          permissionCode: v.permissionCode.trim().toUpperCase(),
          name: v.name.trim(),
          description: v.description.trim(),
        })}
        fields={[
          { name: "systemCode", label: "系統代碼", placeholder: "例如 REPORT",
            validate: (v) => (SYSTEM.test(v.trim().toUpperCase()) ? undefined : "大寫英文開頭，2–32 字的大寫英數與 _") },
          { name: "permissionCode", label: "權限代碼", placeholder: "例如 EXPORT",
            validate: (v) => v.trim().toUpperCase() === "ADMIN_ALL" ? PERMISSION_ERRORS.PERMISSION_CODE_RESERVED
              : CODE.test(v.trim().toUpperCase()) ? undefined : "大寫英文開頭，2–64 字的大寫英數與 _" },
          { name: "name", label: "名稱", placeholder: "例如 匯出報表",
            validate: (v) => (v.trim().length <= 100 ? undefined : "最多 100 字") },
          { name: "description", label: "說明", optional: true, placeholder: "選填",
            validate: (v) => (v.trim().length <= 500 ? undefined : "最多 500 字") },
        ]}
      />

      <FormDialog
        open={!!editing}
        onOpenChange={(open) => !open && setEditing(null)}
        title="編輯權限"
        submitLabel="儲存"
        pending={update.isPending}
        initial={editing ? {
          key: `${editing.systemCode}:${editing.permissionCode}`,
          name: editing.name,
          description: editing.description ?? "",
        } : undefined}
        onSubmit={(v) => update.mutateAsync({
          systemCode: editing!.systemCode,
          permissionCode: editing!.permissionCode,
          name: v.name.trim(),
          description: v.description.trim(),
        })}
        fields={[
          { name: "key", label: "權限", readOnly: true },
          { name: "name", label: "名稱", validate: (v) => (v.trim().length <= 100 ? undefined : "最多 100 字") },
          { name: "description", label: "說明", optional: true,
            validate: (v) => (v.trim().length <= 500 ? undefined : "最多 500 字") },
        ]}
      />
    </div>
  );
}
