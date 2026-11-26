"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Pencil, Plus, RefreshCw } from "lucide-react";
import { useState } from "react";
import { FormDialog } from "@/components/iam/dialogs";
import { EmptyRow, LoadError, Pager, SectionHeader, TableSkeleton } from "@/components/iam/parts";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAction } from "@/hooks/use-action";
import { path } from "@/hooks/use-iam";
import { api } from "@/lib/api";
import { HTTP_METHODS, type ApiRule, type Page } from "@/lib/platform-types";

const PAGE_SIZE = 20;

const ERRORS: Record<string, string> = {
  API_RULE_METHOD_INVALID: "HTTP 方法不正確",
  API_RULE_PATH_INVALID: "路徑必須以 /api/ 開頭，只能用英數、- _ . / { } 與 *，不可有連續的 /",
  API_RULE_PERMISSION_INVALID: "權限格式為 系統:權限，例如 REPORT:EXPORT",
  API_RULE_DESCRIPTION_INVALID: "說明最多 300 字",
  API_RULE_NOT_FOUND: "找不到這條規則",
  CONCURRENT_MODIFICATION: "規則剛被其他人修改，請重新整理後再試",
};

const PATH = /^\/api\/[A-Za-z0-9_\-*/{}.]*$/;
const PERMISSION = /^[A-Z][A-Z0-9_]{1,31}:[A-Z][A-Z0-9_]{1,63}$/;
const METHOD_OPTIONS = HTTP_METHODS.map((m) => ({ value: m, label: m === "*" ? "*（所有方法）" : m }));

type RuleInput = { method: string; pathPattern: string; requiredPermission: string; description: string };

export function ApiRulesTab() {
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<ApiRule | "new" | null>(null);
  const rules = useQuery({
    queryKey: ["api-rules", page],
    queryFn: () => api<Page<ApiRule>>(`departments/api-rules?page=${page}&size=${PAGE_SIZE}`),
    placeholderData: keepPreviousData,
  });

  const invalidate = [["api-rules"]];
  const save = useAction(
    (v: RuleInput & { id?: string }) =>
      v.id
        ? api(`departments/api-rules/${path(v.id)}`, { method: "PUT", body: v })
        : api("departments/api-rules", { method: "POST", body: v }),
    { success: (v) => (v.id ? "已更新規則" : "已新增規則，各服務最多 30 秒內生效"), invalidate, errors: ERRORS },
  );
  const toggle = useAction(
    (v: { id: string; enabled: boolean }) =>
      api(`departments/api-rules/${path(v.id)}/status`, { method: "PATCH", body: { enabled: v.enabled } }),
    { success: (v) => (v.enabled ? "已啟用規則，最多 30 秒內生效" : "已停用規則，最多 30 秒內生效"), invalidate, errors: ERRORS },
  );
  const republish = useAction(() => api<number>("departments/api-rules/publish", { method: "POST" }), {
    success: (_v, count) => `已重新發佈 ${count} 條啟用中的規則到 Redis`,
    invalidate,
    errors: ERRORS,
  });

  const target = editing === "new" ? null : editing;

  return (
    <div className="grid gap-4">
      <SectionHeader
        title="API 規則"
        description="第二道權限檢查：請求的方法與路徑符合規則時，呼叫者必須具備指定權限。同一個端點符合多條規則時要全部通過。"
        action={
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" disabled={republish.isPending} onClick={() => republish.mutate(undefined)}>
              <RefreshCw className="size-4" />重新發佈
            </Button>
            <Button size="sm" onClick={() => setEditing("new")}><Plus className="size-4" />新增規則</Button>
          </div>
        }
      />

      {rules.isPending ? <TableSkeleton /> : rules.isError ? (
        <LoadError error={rules.error} onRetry={() => rules.refetch()} />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-20">方法</TableHead>
                  <TableHead>路徑</TableHead>
                  <TableHead>所需權限</TableHead>
                  <TableHead className="hidden lg:table-cell">說明</TableHead>
                  <TableHead className="w-16">啟用</TableHead>
                  <TableHead className="w-12"><span className="sr-only">操作</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rules.data.content.length === 0 && <EmptyRow colSpan={6}>還沒有 API 規則</EmptyRow>}
                {rules.data.content.map((r) => (
                  <TableRow key={r.id} className={r.enabled ? undefined : "text-muted-foreground"}>
                    <TableCell><Badge variant="outline" className="font-mono">{r.method}</Badge></TableCell>
                    <TableCell className="max-w-72 truncate font-mono text-xs" title={r.pathPattern}>{r.pathPattern}</TableCell>
                    <TableCell className="max-w-56 truncate font-mono text-xs">{r.requiredPermission}</TableCell>
                    <TableCell className="hidden max-w-64 truncate lg:table-cell" title={r.description ?? undefined}>
                      {r.description || "—"}
                    </TableCell>
                    <TableCell>
                      <Switch
                        checked={r.enabled}
                        disabled={toggle.isPending}
                        aria-label={`${r.enabled ? "停用" : "啟用"} ${r.method} ${r.pathPattern}`}
                        onCheckedChange={(enabled) => toggle.mutate({ id: r.id, enabled })}
                      />
                    </TableCell>
                    <TableCell>
                      <Button variant="ghost" size="icon" aria-label={`編輯 ${r.method} ${r.pathPattern}`} onClick={() => setEditing(r)}>
                        <Pencil className="size-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager page={page} size={PAGE_SIZE} total={rules.data.totalElements} onChange={setPage} />
        </>
      )}

      <FormDialog
        open={editing !== null}
        onOpenChange={(open) => !open && setEditing(null)}
        title={target ? "編輯規則" : "新增規則"}
        description="規則存在資料庫並同步寫入 Redis；各服務每 30 秒重新讀取，修改後最多 30 秒生效。"
        submitLabel={target ? "儲存" : "新增"}
        pending={save.isPending}
        initial={target
          ? { method: target.method, pathPattern: target.pathPattern, requiredPermission: target.requiredPermission, description: target.description ?? "" }
          : { method: "GET" }}
        onSubmit={(v) => save.mutateAsync({
          id: target?.id,
          method: v.method,
          pathPattern: v.pathPattern.trim(),
          requiredPermission: v.requiredPermission.trim().toUpperCase(),
          description: v.description.trim(),
        })}
        fields={[
          { name: "method", label: "HTTP 方法", type: "select", options: METHOD_OPTIONS },
          { name: "pathPattern", label: "路徑", placeholder: "/api/departments/*/tree", hint: "* 代表一層路徑，** 代表任意層",
            validate: (v) => (PATH.test(v.trim()) && !v.includes("//") && v.trim().length <= 200 ? undefined : ERRORS.API_RULE_PATH_INVALID) },
          { name: "requiredPermission", label: "所需權限", placeholder: "DEPT:TREE_READ",
            validate: (v) => (PERMISSION.test(v.trim().toUpperCase()) ? undefined : ERRORS.API_RULE_PERMISSION_INVALID) },
          { name: "description", label: "說明", optional: true, placeholder: "選填",
            validate: (v) => (v.trim().length <= 300 ? undefined : "最多 300 字") },
        ]}
      />
    </div>
  );
}
