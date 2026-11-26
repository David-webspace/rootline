/** 對應 tenant-service、dept-tree（權限定義、API 規則）、kyc-service 的回應格式。 */

export type Page<T> = { content: T[]; totalElements: number; page: number; size: number };

export type Plan = "FREE" | "PRO" | "ENTERPRISE";
export const PLANS: { value: Plan; label: string }[] = [
  { value: "FREE", label: "免費" },
  { value: "PRO", label: "專業" },
  { value: "ENTERPRISE", label: "企業" },
];
export const planLabel = (plan: string) => PLANS.find((p) => p.value === plan)?.label ?? plan;

export type Tenant = {
  tenantId: string;
  companyName: string;
  plan: Plan;
  status: "ACTIVE" | "SUSPENDED";
  suspendReason: string | null;
  createdAt: string;
  expiresAt: string | null;
};

export type PermissionDefinition = {
  systemCode: string;
  permissionCode: string;
  name: string;
  description: string | null;
  version: number;
};

export const HTTP_METHODS = ["GET", "POST", "PUT", "PATCH", "DELETE", "*"] as const;

export type ApiRule = {
  id: string;
  method: (typeof HTTP_METHODS)[number];
  pathPattern: string;
  requiredPermission: string;
  enabled: boolean;
  description: string | null;
  version: number;
};

export type KycStatus = "UNVERIFIED" | "PENDING_REVIEW" | "VERIFIED" | "REJECTED";
export type DocumentType = "NATIONAL_ID" | "PASSPORT" | "RESIDENT_PERMIT";

export type KycAddress = {
  countryCode: string;
  state: string | null;
  city: string;
  postalCode: string | null;
  detail: string;
};

export type KycView = {
  userId: string;
  status: KycStatus;
  firstName: string | null;
  lastName: string | null;
  dateOfBirth: string | null;
  documentType: DocumentType | null;
  countryCode: string | null;
  maskedIdNumber: string | null;
  address: KycAddress | null;
  flags: string[];
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectReason: string | null;
};

export const DOCUMENT_TYPES: { value: DocumentType; label: string }[] = [
  { value: "NATIONAL_ID", label: "身分證" },
  { value: "PASSPORT", label: "護照" },
  { value: "RESIDENT_PERMIT", label: "居留證" },
];

export const COUNTRIES = [
  { value: "TW", label: "台灣" },
  { value: "JP", label: "日本" },
  { value: "KR", label: "韓國" },
  { value: "HK", label: "香港" },
  { value: "SG", label: "新加坡" },
  { value: "MY", label: "馬來西亞" },
  { value: "US", label: "美國" },
  { value: "GB", label: "英國" },
];
export const countryLabel = (code: string | null) => COUNTRIES.find((c) => c.value === code)?.label ?? code ?? "—";

export const KYC_FLAGS: Record<string, string> = {
  DUPLICATE_DOCUMENT: "證件已被其他帳號使用",
  UNDERAGE: "未滿 18 歲",
};
