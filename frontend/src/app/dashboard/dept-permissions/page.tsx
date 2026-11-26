import type { Metadata } from "next";
import { Suspense } from "react";
import { ApiRulesTab } from "@/components/dept-permissions/api-rules-tab";
import { PermissionDefinitionsTab } from "@/components/dept-permissions/permission-definitions-tab";
import { TableSkeleton } from "@/components/iam/parts";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { hasPermission } from "@/lib/permissions";
import { getCurrentUser } from "@/lib/server/session";

export const metadata: Metadata = { title: "權限定義與規則" };

export default function DeptPermissionsPage() {
  return (
    <div className="mx-auto grid w-full max-w-6xl gap-6">
      <header className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight">權限定義與規則</h1>
        <p className="mt-1 text-sm break-words text-muted-foreground">
          權限檢查分兩道：程式裡標註的固定權限，以及這裡可以隨時調整、不必重新部署的 API 規則。
        </p>
      </header>
      <Suspense fallback={<TableSkeleton rows={6} />}>
        <DeptPermissionsSection />
      </Suspense>
    </div>
  );
}

async function DeptPermissionsSection() {
  const user = await getCurrentUser();
  const can = (p: string) => hasPermission(user.permissions, p);
  const tabs = [
    { value: "definitions", label: "權限定義", visible: can("DEPT:TREE_READ") || can("DEPT:PERMISSION_WRITE") },
    { value: "rules", label: "API 規則", visible: can("DEPT:API_RULE_WRITE") },
  ].filter((t) => t.visible);

  if (tabs.length === 0) {
    return <p className="text-sm text-muted-foreground">你沒有管理權限定義或 API 規則的權限，請聯絡租戶管理員。</p>;
  }
  // 分頁切換是 Radix Tabs（client component），內容本身也都是 client component，這層只決定要顯示哪些
  return (
    <Tabs defaultValue={tabs[0].value} className="gap-6">
      <TabsList>
        {tabs.map((t) => <TabsTrigger key={t.value} value={t.value}>{t.label}</TabsTrigger>)}
      </TabsList>
      {tabs.some((t) => t.value === "definitions") && (
        <TabsContent value="definitions">
          <PermissionDefinitionsTab canWrite={can("DEPT:PERMISSION_WRITE")} />
        </TabsContent>
      )}
      {tabs.some((t) => t.value === "rules") && (
        <TabsContent value="rules">
          <ApiRulesTab />
        </TabsContent>
      )}
    </Tabs>
  );
}
