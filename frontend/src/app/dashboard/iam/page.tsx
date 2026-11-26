import type { Metadata } from "next";
import { Suspense } from "react";
import { IamWorkspace } from "@/components/iam/iam-workspace";
import { TableSkeleton } from "@/components/iam/parts";
import { getCurrentUser } from "@/lib/server/session";

export const metadata: Metadata = { title: "身分與權限" };

export default function IamPage() {
  return (
    <div className="mx-auto grid w-full max-w-6xl gap-6">
      <header className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight">身分與權限</h1>
        <p className="mt-1 text-sm break-words text-muted-foreground">
          管理成員、角色與群組。權限調整在成員下次登入（或 token 更新）後生效。
        </p>
      </header>
      {/* 標題屬於靜態外殼，立即顯示；讀 session 的部分在 Suspense 裡串流進來 */}
      <Suspense fallback={<TableSkeleton rows={5} />}>
        <IamSection />
      </Suspense>
    </div>
  );
}

async function IamSection() {
  const user = await getCurrentUser();
  return <IamWorkspace permissions={user.permissions} username={user.username} />;
}
