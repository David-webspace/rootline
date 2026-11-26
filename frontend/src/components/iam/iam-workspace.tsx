"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { hasPermission } from "@/lib/permissions";
import { GroupsTab } from "./groups-tab";
import { RolesTab } from "./roles-tab";
import { UsersTab } from "./users-tab";

/**
 * 依呼叫者權限決定顯示哪些分頁與按鈕。這只影響畫面；每個操作後端都會再檢查權限。
 */
export function IamWorkspace({ permissions, username }: { permissions: string[]; username: string }) {
  const can = (p: string) => hasPermission(permissions, p);
  const tabs = [
    { value: "users", label: "成員", visible: can("IAM:USER_READ") },
    { value: "roles", label: "角色", visible: can("IAM:ROLE_READ") },
    { value: "groups", label: "群組", visible: can("IAM:GROUP_READ") },
  ].filter((t) => t.visible);

  if (tabs.length === 0) {
    return <p className="text-sm text-muted-foreground">你沒有檢視身分與權限資料的權限，請聯絡租戶管理員。</p>;
  }

  return (
    <Tabs defaultValue={tabs[0].value} className="gap-6">
      <TabsList>
        {tabs.map((t) => <TabsTrigger key={t.value} value={t.value}>{t.label}</TabsTrigger>)}
      </TabsList>
      <TabsContent value="users">
        <UsersTab canWrite={can("IAM:USER_WRITE")} canReadRoles={can("IAM:ROLE_READ")} currentUser={username} />
      </TabsContent>
      <TabsContent value="roles">
        <RolesTab canWrite={can("IAM:ROLE_WRITE")} />
      </TabsContent>
      <TabsContent value="groups">
        <GroupsTab canWrite={can("IAM:GROUP_WRITE")} canReadUsers={can("IAM:USER_READ")} canReadRoles={can("IAM:ROLE_READ")} />
      </TabsContent>
    </Tabs>
  );
}
