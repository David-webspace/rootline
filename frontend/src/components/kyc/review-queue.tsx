"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ConfirmDialog, FormDialog } from "@/components/iam/dialogs";
import { EmptyRow, LoadError, Pager, SectionHeader, TableSkeleton } from "@/components/iam/parts";
import { formatTime } from "@/components/org-tree/event-labels";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAction } from "@/hooks/use-action";
import { path } from "@/hooks/use-iam";
import { api } from "@/lib/api";
import { countryLabel, type KycStatus, type KycView, type Page } from "@/lib/platform-types";
import { documentLabel, FlagBadges, KYC_ERRORS, kycStatusLabel, KycStatusBadge } from "./kyc-parts";

const PAGE_SIZE = 20;
const STATUSES: KycStatus[] = ["PENDING_REVIEW", "VERIFIED", "REJECTED"];

/** 審核佇列。職務分離：自己的申請不能自己審（後端會擋，畫面上先停用按鈕）。 */
export function ReviewQueue({ currentUser }: { currentUser: string }) {
  const [status, setStatus] = useState<KycStatus>("PENDING_REVIEW");
  const [flaggedOnly, setFlaggedOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [approving, setApproving] = useState<KycView | null>(null);
  const [rejecting, setRejecting] = useState<KycView | null>(null);

  const queue = useQuery({
    queryKey: ["kyc", "queue", status, flaggedOnly, page],
    queryFn: () =>
      api<Page<KycView>>(`kyc/review-queue?status=${status}&flaggedOnly=${flaggedOnly}&page=${page}&size=${PAGE_SIZE}`),
    placeholderData: keepPreviousData,
  });

  const invalidate = [["kyc"]];
  const approve = useAction((userId: string) => api(`kyc/${path(userId)}/approve`, { method: "POST" }), {
    success: (u) => `已核准 ${u} 的實名認證`,
    invalidate,
    errors: KYC_ERRORS,
  });
  const reject = useAction(
    (v: { userId: string; reason: string }) => api(`kyc/${path(v.userId)}/reject`, { method: "POST", body: { reason: v.reason } }),
    { success: (v) => `已駁回 ${v.userId} 的申請`, invalidate, errors: KYC_ERRORS },
  );

  const pending = status === "PENDING_REVIEW";

  return (
    <div className="grid gap-4">
      <SectionHeader
        title="審核佇列"
        description="送審時會自動檢查證件是否被其他帳號使用、是否未成年；有風險標記的申請只能駁回。"
      />
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Select value={status} onValueChange={(v) => { setStatus(v as KycStatus); setPage(0); }}>
          <SelectTrigger aria-label="狀態篩選" className="w-full sm:w-36"><SelectValue /></SelectTrigger>
          <SelectContent>
            {STATUSES.map((s) => <SelectItem key={s} value={s}>{kycStatusLabel(s)}</SelectItem>)}
          </SelectContent>
        </Select>
        <div className="flex items-center gap-2">
          <Switch id="flagged-only" checked={flaggedOnly} onCheckedChange={(v) => { setFlaggedOnly(v); setPage(0); }} />
          <Label htmlFor="flagged-only">只看有風險標記</Label>
        </div>
      </div>

      {queue.isPending ? <TableSkeleton /> : queue.isError ? (
        <LoadError error={queue.error} onRetry={() => queue.refetch()} />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>申請人</TableHead>
                  <TableHead className="hidden md:table-cell">證件</TableHead>
                  <TableHead>風險標記</TableHead>
                  <TableHead className="hidden lg:table-cell">送出時間</TableHead>
                  <TableHead>{pending ? <span className="sr-only">操作</span> : "結果"}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {queue.data.content.length === 0 && <EmptyRow colSpan={5}>沒有符合條件的申請</EmptyRow>}
                {queue.data.content.map((a) => {
                  const self = a.userId === currentUser;
                  const flagged = a.flags.length > 0;
                  return (
                    <TableRow key={a.userId}>
                      <TableCell className="max-w-48">
                        <div className="truncate font-medium">{`${a.lastName ?? ""} ${a.firstName ?? ""}`.trim() || "—"}</div>
                        <div className="truncate text-xs text-muted-foreground">
                          {a.userId}{self && "（你）"}
                        </div>
                      </TableCell>
                      <TableCell className="hidden max-w-56 md:table-cell">
                        <div className="truncate">{documentLabel(a.documentType)} · {countryLabel(a.countryCode)}</div>
                        <div className="truncate font-mono text-xs text-muted-foreground">{a.maskedIdNumber}</div>
                      </TableCell>
                      <TableCell className="min-w-28"><FlagBadges flags={a.flags} /></TableCell>
                      <TableCell className="hidden whitespace-nowrap text-muted-foreground lg:table-cell">
                        {a.submittedAt ? formatTime(a.submittedAt) : "—"}
                      </TableCell>
                      <TableCell>
                        {pending ? (
                          <div className="flex gap-2">
                            <Button size="sm" disabled={self || flagged || approve.isPending}
                              title={self ? "不能審核自己的申請" : flagged ? "有風險標記的申請只能駁回" : undefined}
                              onClick={() => setApproving(a)}>核准</Button>
                            <Button size="sm" variant="outline" disabled={self || reject.isPending}
                              title={self ? "不能審核自己的申請" : undefined}
                              onClick={() => setRejecting(a)}>駁回</Button>
                          </div>
                        ) : (
                          <div className="grid gap-1">
                            <KycStatusBadge status={a.status} />
                            {a.rejectReason && <span className="max-w-48 truncate text-xs text-muted-foreground" title={a.rejectReason}>{a.rejectReason}</span>}
                          </div>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
          <Pager page={page} size={PAGE_SIZE} total={queue.data.totalElements} onChange={setPage} />
        </>
      )}

      <ConfirmDialog
        open={!!approving}
        onOpenChange={(open) => !open && setApproving(null)}
        title="核准實名認證"
        description={`核准後 ${approving?.userId ?? ""} 的資料會鎖定，無法再修改。`}
        confirmLabel="核准"
        onConfirm={() => {
          if (approving) approve.mutate(approving.userId);
          setApproving(null);
        }}
      />
      <FormDialog
        open={!!rejecting}
        onOpenChange={(open) => !open && setRejecting(null)}
        title={`駁回 ${rejecting?.userId ?? ""} 的申請`}
        description="原因會顯示給申請人，請具體說明要修正的地方。"
        submitLabel="駁回"
        pending={reject.isPending}
        onSubmit={(v) => reject.mutateAsync({ userId: rejecting!.userId, reason: v.reason.trim() })}
        fields={[{ name: "reason", label: "駁回原因", placeholder: "例如 證件照片模糊，請重新上傳",
          validate: (v) => (v.trim().length <= 500 ? undefined : "最多 500 字") }]}
      />
    </div>
  );
}
