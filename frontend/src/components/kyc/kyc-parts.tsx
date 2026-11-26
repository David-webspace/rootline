"use client";

import { Badge } from "@/components/ui/badge";
import { DOCUMENT_TYPES, KYC_FLAGS, type KycStatus } from "@/lib/platform-types";

export const KYC_ERRORS: Record<string, string> = {
  KYC_ALREADY_VERIFIED: "已完成實名認證，不能再修改",
  KYC_UNDER_REVIEW: "已有送審中的資料，請等待審核結果",
  KYC_NOT_PENDING: "這筆申請已經審核過了",
  KYC_FLAGGED: "有風險標記的申請不能核准，只能駁回",
  KYC_NOT_FOUND: "找不到這筆申請",
  SELF_REVIEW_FORBIDDEN: "不能審核自己的申請",
  REJECT_REASON_REQUIRED: "駁回必須填寫原因（最多 500 字），讓申請人知道要修正什麼",
  DOCUMENT_NUMBER_INVALID: "證件號碼格式不正確",
  DOCUMENT_TYPE_INVALID: "不支援的證件類型",
  COUNTRY_CODE_INVALID: "國家代碼不正確",
  DATE_OF_BIRTH_INVALID: "出生日期不正確",
  NAME_INVALID: "姓名必填，最多 50 字",
  ADDRESS_INVALID: "地址資料不完整",
};

const STATUS: Record<KycStatus, { label: string; className: string }> = {
  UNVERIFIED: { label: "未認證", className: "border-border bg-muted text-muted-foreground" },
  PENDING_REVIEW: { label: "審核中", className: "border-warning/30 bg-warning/10 text-warning" },
  VERIFIED: { label: "已認證", className: "border-success/30 bg-success/10 text-success" },
  REJECTED: { label: "已駁回", className: "border-destructive/30 bg-destructive/10 text-destructive" },
};

export const kycStatusLabel = (status: KycStatus) => STATUS[status]?.label ?? status;

export function KycStatusBadge({ status }: { status: KycStatus }) {
  const s = STATUS[status] ?? STATUS.UNVERIFIED;
  return <Badge variant="outline" className={s.className}>{s.label}</Badge>;
}

export function FlagBadges({ flags }: { flags: string[] }) {
  if (flags.length === 0) return <span className="text-xs text-muted-foreground">—</span>;
  return (
    <div className="flex flex-wrap gap-1">
      {flags.map((f) => (
        <Badge key={f} variant="outline" className="border-destructive/30 bg-destructive/10 text-destructive">
          {KYC_FLAGS[f] ?? f}
        </Badge>
      ))}
    </div>
  );
}

export const documentLabel = (type: string | null) => DOCUMENT_TYPES.find((d) => d.value === type)?.label ?? type ?? "—";
