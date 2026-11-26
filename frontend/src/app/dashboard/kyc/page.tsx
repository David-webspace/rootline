import type { Metadata } from "next";
import { Suspense } from "react";
import { TableSkeleton } from "@/components/iam/parts";
import { MyKyc } from "@/components/kyc/my-kyc";
import { ReviewQueue } from "@/components/kyc/review-queue";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { hasPermission } from "@/lib/permissions";
import { getCurrentUser } from "@/lib/server/session";

export const metadata: Metadata = { title: "實名認證" };

export default function KycPage() {
  return (
    <div className="mx-auto grid w-full max-w-5xl gap-6">
      <header className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight">實名認證</h1>
        <p className="mt-1 text-sm break-words text-muted-foreground">
          送出身分資料給租戶審核。審核結果會以事件同步到身分服務與個人檔案。
        </p>
      </header>
      <Suspense fallback={<TableSkeleton rows={4} />}>
        <KycSection />
      </Suspense>
    </div>
  );
}

async function KycSection() {
  const user = await getCurrentUser();
  if (!hasPermission(user.permissions, "KYC:REVIEW")) return <MyKyc />;
  return (
    <Tabs defaultValue="mine" className="gap-6">
      <TabsList>
        <TabsTrigger value="mine">我的認證</TabsTrigger>
        <TabsTrigger value="queue">審核佇列</TabsTrigger>
      </TabsList>
      <TabsContent value="mine"><MyKyc /></TabsContent>
      <TabsContent value="queue"><ReviewQueue currentUser={user.username} /></TabsContent>
    </Tabs>
  );
}
