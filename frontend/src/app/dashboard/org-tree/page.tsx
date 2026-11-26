import type { Metadata } from "next";
import { Suspense } from "react";
import { TableSkeleton } from "@/components/iam/parts";
import { OrgTreeWorkspace } from "@/components/org-tree/org-tree-workspace";
import { hasPermission } from "@/lib/permissions";
import { getCurrentUser } from "@/lib/server/session";

export const metadata: Metadata = { title: "組織樹" };

export default function OrgTreePage() {
  return (
    <div className="mx-auto grid w-full max-w-7xl gap-6">
      <header className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight">組織樹</h1>
        <p className="mt-1 text-sm break-words text-muted-foreground">
          每一次異動都是一個事件：可以搬移、合併、刪除後復活，也能回到任一時間點查看當時的組織。
        </p>
      </header>
      <Suspense fallback={<TableSkeleton rows={8} />}>
        <OrgTreeSection />
      </Suspense>
    </div>
  );
}

async function OrgTreeSection() {
  const user = await getCurrentUser();
  if (!hasPermission(user.permissions, "DEPT:TREE_READ")) {
    return <p className="text-sm text-muted-foreground">你沒有檢視組織樹的權限，請聯絡租戶管理員。</p>;
  }
  return <OrgTreeWorkspace permissions={user.permissions} />;
}
