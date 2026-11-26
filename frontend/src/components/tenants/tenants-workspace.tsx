"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { MoreHorizontal, Plus, Search } from "lucide-react";
import { useState, type FormEvent } from "react";
import { ConfirmDialog, FormDialog, SelectDialog } from "@/components/iam/dialogs";
import { EmptyRow, LoadError, Pager, SectionHeader, TableSkeleton } from "@/components/iam/parts";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAction } from "@/hooks/use-action";
import { path } from "@/hooks/use-iam";
import { api } from "@/lib/api";
import { PLANS, planLabel, type Page, type Tenant } from "@/lib/platform-types";
import { formatTime } from "@/components/org-tree/event-labels";

const PAGE_SIZE = 20;

const ERRORS: Record<string, string> = {
  TENANT_ID_TAKEN: "這個租戶代碼已被使用",
  TENANT_ID_INVALID: "租戶代碼只能用小寫英數與 -，2–32 字",
  TENANT_ID_RESERVED: "這個租戶代碼是保留字，請換一個",
  COMPANY_NAME_INVALID: "公司名稱必填，最多 200 字",
  ADMIN_USERNAME_INVALID: "管理員帳號為 3–64 字的小寫英數與 . _ -",
  ADMIN_EMAIL_INVALID: "管理員 Email 格式不正確",
  ADMIN_PASSWORD_INVALID: "請設定管理員初始密碼",
  PLAN_INVALID: "不支援的方案",
  PLAN_NOT_UPGRADE: "只能升級到更高的方案",
  TENANT_NOT_ACTIVE: "停權中的租戶不能變更方案，請先恢復",
  SUSPEND_REASON_REQUIRED: "停權必須填寫原因",
  TENANT_NOT_FOUND: "找不到這個租戶",
  PLATFORM_ONLY: "只有平台營運人員可以管理租戶",
};

const STATUS_FILTERS = [
  { value: "ALL", label: "全部狀態" },
  { value: "ACTIVE", label: "啟用中" },
  { value: "SUSPENDED", label: "停權中" },
];

type Dialog =
  | { kind: "provision" }
  | { kind: "suspend" | "reactivate" | "plan"; tenant: Tenant }
  | null;

export function TenantsWorkspace({ canWrite }: { canWrite: boolean }) {
  const [keyword, setKeyword] = useState("");
  const [draft, setDraft] = useState("");
  const [status, setStatus] = useState("ALL");
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<Dialog>(null);

  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (keyword) params.set("keyword", keyword);
  if (status !== "ALL") params.set("status", status);
  const tenants = useQuery({
    queryKey: ["tenants", keyword, status, page],
    queryFn: () => api<Page<Tenant>>(`platform/tenants?${params}`),
    placeholderData: keepPreviousData,
  });

  const invalidate = [["tenants"]];
  const provision = useAction((v: Record<string, string>) => api<Tenant>("platform/tenants", { method: "POST", body: v }), {
    success: (v) => `已開通 ${v.tenantId}，管理員帳號 ${v.adminUsername} 約一秒內可登入`,
    invalidate,
    errors: ERRORS,
  });
  const suspend = useAction(
    (v: { id: string; reason: string }) =>
      api(`platform/tenants/${path(v.id)}/suspend`, { method: "POST", body: { reason: v.reason } }),
    { success: (v) => `已停權 ${v.id}，該租戶的使用者將無法登入`, invalidate, errors: ERRORS },
  );
  const reactivate = useAction((id: string) => api(`platform/tenants/${path(id)}/reactivate`, { method: "POST" }), {
    success: (id) => `已恢復 ${id}`,
    invalidate,
    errors: ERRORS,
  });
  const changePlan = useAction(
    (v: { id: string; plan: string }) =>
      api(`platform/tenants/${path(v.id)}/plan`, { method: "PUT", body: { plan: v.plan } }),
    { success: (v) => `已升級 ${v.id} 為${planLabel(v.plan)}方案`, invalidate, errors: ERRORS },
  );

  function search(event: FormEvent) {
    event.preventDefault();
    setKeyword(draft.trim());
    setPage(0);
  }

  const target = dialog && dialog.kind !== "provision" ? dialog.tenant : null;
  const higherPlans = target ? PLANS.slice(PLANS.findIndex((p) => p.value === target.plan) + 1) : [];

  return (
    <div className="grid gap-4">
      <SectionHeader
        title="租戶"
        description="開通後會自動建立租戶管理員帳號；停權的租戶所有使用者都無法登入。"
        action={canWrite && (
          <Button size="sm" onClick={() => setDialog({ kind: "provision" })}>
            <Plus className="size-4" />開通租戶
          </Button>
        )}
      />

      <div className="flex flex-col gap-2 sm:flex-row">
        <form onSubmit={search} role="search" className="flex min-w-0 flex-1 gap-2">
          <Input
            aria-label="搜尋租戶代碼或公司名稱"
            placeholder="搜尋租戶代碼或公司名稱"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            className="min-w-0"
          />
          <Button type="submit" variant="outline" size="icon" aria-label="搜尋">
            <Search className="size-4" />
          </Button>
        </form>
        <Select value={status} onValueChange={(v) => { setStatus(v); setPage(0); }}>
          <SelectTrigger aria-label="狀態篩選" className="w-full sm:w-36">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STATUS_FILTERS.map((s) => <SelectItem key={s.value} value={s.value}>{s.label}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      {tenants.isPending ? <TableSkeleton /> : tenants.isError ? (
        <LoadError error={tenants.error} onRetry={() => tenants.refetch()} />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>租戶</TableHead>
                  <TableHead>方案</TableHead>
                  <TableHead>狀態</TableHead>
                  <TableHead className="hidden lg:table-cell">開通時間</TableHead>
                  <TableHead className="hidden md:table-cell">到期</TableHead>
                  {canWrite && <TableHead className="w-12"><span className="sr-only">操作</span></TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenants.data.content.length === 0 && (
                  <EmptyRow colSpan={canWrite ? 6 : 5}>{keyword || status !== "ALL" ? "沒有符合條件的租戶" : "還沒有租戶"}</EmptyRow>
                )}
                {tenants.data.content.map((t) => (
                  <TableRow key={t.tenantId}>
                    <TableCell className="max-w-64">
                      <div className="truncate font-medium">{t.companyName}</div>
                      <div className="truncate font-mono text-xs text-muted-foreground">{t.tenantId}</div>
                    </TableCell>
                    <TableCell><Badge variant="secondary">{planLabel(t.plan)}</Badge></TableCell>
                    <TableCell className="max-w-56">
                      {t.status === "ACTIVE" ? (
                        <Badge variant="outline" className="border-success/30 bg-success/10 text-success">啟用中</Badge>
                      ) : (
                        <div className="grid gap-1">
                          <Badge variant="outline" className="border-destructive/30 bg-destructive/10 text-destructive">停權中</Badge>
                          {t.suspendReason && <span className="truncate text-xs text-muted-foreground" title={t.suspendReason}>{t.suspendReason}</span>}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="hidden whitespace-nowrap text-muted-foreground lg:table-cell">{formatTime(t.createdAt)}</TableCell>
                    <TableCell className="hidden whitespace-nowrap text-muted-foreground md:table-cell">
                      {t.expiresAt ? formatTime(t.expiresAt) : "—"}
                    </TableCell>
                    {canWrite && (
                      <TableCell>
                        {t.tenantId === "platform" ? null : (
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon" aria-label={`${t.tenantId} 的操作`}>
                                <MoreHorizontal className="size-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem disabled={t.status !== "ACTIVE" || t.plan === "ENTERPRISE"}
                                onSelect={() => setDialog({ kind: "plan", tenant: t })}>升級方案</DropdownMenuItem>
                              {t.status === "ACTIVE" ? (
                                <DropdownMenuItem variant="destructive" onSelect={() => setDialog({ kind: "suspend", tenant: t })}>
                                  停權
                                </DropdownMenuItem>
                              ) : (
                                <DropdownMenuItem onSelect={() => setDialog({ kind: "reactivate", tenant: t })}>恢復</DropdownMenuItem>
                              )}
                            </DropdownMenuContent>
                          </DropdownMenu>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <Pager page={page} size={PAGE_SIZE} total={tenants.data.totalElements} onChange={setPage} />
        </>
      )}

      <FormDialog
        open={dialog?.kind === "provision"}
        onOpenChange={(open) => !open && setDialog(null)}
        title="開通租戶"
        description="管理員帳號會在身分服務建立並授予租戶管理員角色，初始密碼請另行安全地告知對方。"
        submitLabel="開通"
        pending={provision.isPending}
        initial={{ plan: "FREE" }}
        onSubmit={(v) => provision.mutateAsync({ ...v, tenantId: v.tenantId.trim(), adminUsername: v.adminUsername.trim() })}
        fields={[
          { name: "tenantId", label: "租戶代碼", placeholder: "例如 acme", hint: "登入時輸入的代碼，開通後不能修改",
            validate: (v) => (/^[a-z0-9][a-z0-9-]{1,31}$/.test(v.trim()) ? undefined : ERRORS.TENANT_ID_INVALID) },
          { name: "companyName", label: "公司名稱", placeholder: "例如 Acme 股份有限公司",
            validate: (v) => (v.trim().length <= 200 ? undefined : "最多 200 字") },
          { name: "plan", label: "方案", type: "select", options: PLANS },
          { name: "adminUsername", label: "管理員帳號", placeholder: "例如 admin",
            validate: (v) => (/^[a-z0-9][a-z0-9._-]{2,63}$/.test(v.trim()) ? undefined : ERRORS.ADMIN_USERNAME_INVALID) },
          { name: "adminEmail", label: "管理員 Email", type: "email", placeholder: "admin@acme.example",
            validate: (v) => (/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v.trim()) ? undefined : ERRORS.ADMIN_EMAIL_INVALID) },
          { name: "adminPassword", label: "管理員初始密碼", type: "password", hint: "至少 8 個字元",
            validate: (v) => (v.length >= 8 ? undefined : "至少 8 個字元") },
        ]}
      />

      <FormDialog
        open={dialog?.kind === "suspend"}
        onOpenChange={(open) => !open && setDialog(null)}
        title={`停權 ${target?.companyName ?? ""}`}
        description="停權後該租戶的所有使用者都無法登入，之後可以恢復。原因會留在稽核紀錄。"
        submitLabel="停權"
        pending={suspend.isPending}
        onSubmit={(v) => suspend.mutateAsync({ id: target!.tenantId, reason: v.reason.trim() })}
        fields={[{ name: "reason", label: "停權原因", placeholder: "例如 帳款逾期未繳" }]}
      />

      <SelectDialog
        open={dialog?.kind === "plan"}
        onOpenChange={(open) => !open && setDialog(null)}
        title={`升級 ${target?.companyName ?? ""} 的方案`}
        description={target ? `目前是${planLabel(target.plan)}方案，只能往上升級。` : undefined}
        label="新方案"
        options={higherPlans}
        emptyText="已經是最高方案。"
        submitLabel="升級"
        pending={changePlan.isPending}
        onSubmit={(plan) => changePlan.mutateAsync({ id: target!.tenantId, plan })}
      />

      <ConfirmDialog
        open={dialog?.kind === "reactivate"}
        onOpenChange={(open) => !open && setDialog(null)}
        title="恢復租戶"
        description={`確定恢復 ${target?.companyName ?? ""}？該租戶的使用者將可以重新登入。`}
        confirmLabel="恢復"
        onConfirm={() => {
          if (target) reactivate.mutate(target.tenantId);
          setDialog(null);
        }}
      />
    </div>
  );
}
