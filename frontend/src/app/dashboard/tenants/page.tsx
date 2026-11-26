import type { Metadata } from "next";
import { Suspense } from "react";
import { TableSkeleton } from "@/components/iam/parts";
import { TenantsWorkspace } from "@/components/tenants/tenants-workspace";
import { hasPermission } from "@/lib/permissions";
import { getCurrentUser } from "@/lib/server/session";

export const metadata: Metadata = { title: "租戶管理" };

export default function TenantsPage() {
  return (
    <div className="mx-auto grid w-full max-w-6xl gap-6">
      <header className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight">租戶管理</h1>
        <p className="mt-1 text-sm break-words text-muted-foreground">
          平台營運專用：開通新租戶、停權或恢復、升級方案。每個動作都會發出事件，同步到身分與組織服務。
        </p>
      </header>
      <Suspense fallback={<TableSkeleton rows={6} />}>
        <TenantsSection />
      </Suspense>
    </div>
  );
}

async function TenantsSection() {
  const user = await getCurrentUser();
  // 一般租戶的 *:ADMIN_ALL 只代表「自己租戶內的全部權限」，後端也會擋，這裡只是不顯示畫面
  if (user.tenantId !== "platform" || !hasPermission(user.permissions, "PLATFORM:TENANT_READ")) {
    return <p className="text-sm text-muted-foreground">租戶管理只開放給平台營運人員。</p>;
  }
  return <TenantsWorkspace canWrite={hasPermission(user.permissions, "PLATFORM:TENANT_WRITE")} />;
}
