"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { LoadError, TableSkeleton } from "@/components/iam/parts";
import { formatTime } from "@/components/org-tree/event-labels";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAction } from "@/hooks/use-action";
import { api } from "@/lib/api";
import { countryLabel, type KycView } from "@/lib/platform-types";
import { KycForm } from "./kyc-form";
import { documentLabel, KYC_ERRORS, KycStatusBadge } from "./kyc-parts";

const COPY = {
  UNVERIFIED: "尚未送出實名認證。部分功能（例如申請較高權限）可能要求完成認證。",
  PENDING_REVIEW: "資料已送出，等待審核人員確認。審核期間不能修改。",
  VERIFIED: "實名認證已完成，資料已鎖定。",
  REJECTED: "申請被駁回，請依原因修正後重新送出。",
} as const;

export function MyKyc() {
  const me = useQuery({ queryKey: ["kyc", "me"], queryFn: () => api<KycView>("kyc/me") });
  const [resubmitting, setResubmitting] = useState(false);
  const submit = useAction((body: unknown) => api<KycView>("kyc/submit", { method: "POST", body }), {
    success: "已送出，等待審核",
    invalidate: [["kyc"]],
    errors: KYC_ERRORS,
  });

  if (me.isPending) return <TableSkeleton rows={3} />;
  if (me.isError) return <LoadError error={me.error} onRetry={() => me.refetch()} />;

  const view = me.data;
  const showForm = view.status === "UNVERIFIED" || (view.status === "REJECTED" && resubmitting);

  return (
    <Card className="min-w-0">
      <CardHeader>
        <div className="flex flex-wrap items-center gap-2">
          <CardTitle>我的實名認證</CardTitle>
          <KycStatusBadge status={view.status} />
        </div>
        <CardDescription className="break-words">{COPY[view.status]}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-6">
        {view.status === "REJECTED" && view.rejectReason && (
          <div role="status" className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm">
            <p className="font-medium text-destructive">駁回原因</p>
            <p className="mt-1 break-words">{view.rejectReason}</p>
          </div>
        )}

        {showForm ? (
          <KycForm
            previous={view.status === "REJECTED" ? view : undefined}
            pending={submit.isPending}
            onSubmit={async (values) => {
              await submit.mutateAsync(values);
              setResubmitting(false);
            }}
          />
        ) : (
          view.status !== "UNVERIFIED" && <Summary view={view} />
        )}

        {view.status === "REJECTED" && !resubmitting && (
          <div><Button onClick={() => setResubmitting(true)}>修正並重新送出</Button></div>
        )}
      </CardContent>
    </Card>
  );
}

function Summary({ view }: { view: KycView }) {
  const address = view.address
    ? [countryLabel(view.address.countryCode), view.address.state, view.address.city, view.address.detail].filter(Boolean).join(" ")
    : "—";
  const rows: [string, string][] = [
    ["姓名", `${view.lastName ?? ""} ${view.firstName ?? ""}`.trim() || "—"],
    ["出生日期", view.dateOfBirth ?? "—"],
    ["證件", `${documentLabel(view.documentType)}（${countryLabel(view.countryCode)}）`],
    ["證件號碼", view.maskedIdNumber ?? "—"],
    ["居住地址", address],
    ["送出時間", view.submittedAt ? formatTime(view.submittedAt) : "—"],
  ];
  if (view.reviewedAt) rows.push(["審核", `${formatTime(view.reviewedAt)} · ${view.reviewedBy ?? ""}`]);

  return (
    <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-[8rem_minmax(0,1fr)]">
      {rows.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-muted-foreground">{label}</dt>
          <dd className={label === "證件號碼" ? "font-mono break-all" : "break-words"}>{value}</dd>
        </div>
      ))}
    </dl>
  );
}
