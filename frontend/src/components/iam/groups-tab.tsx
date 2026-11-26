"use client";

import { Plus, Settings2 } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { iamKeys, path, useGroups, useIamMutation, useRoles, useUsers } from "@/hooks/use-iam";
import { api } from "@/lib/api";
import type { Group } from "@/lib/iam-types";
import { FormDialog, SelectDialog } from "./dialogs";
import { Chip, EmptyRow, LoadError, SectionHeader, TableSkeleton } from "./parts";

const CODE = /^[A-Za-z][A-Za-z0-9_]{1,63}$/;

export function GroupsTab({ canWrite, canReadUsers, canReadRoles }: {
  canWrite: boolean;
  canReadUsers: boolean;
  canReadRoles: boolean;
}) {
  const groups = useGroups();
  const [creating, setCreating] = useState(false);
  const [managing, setManaging] = useState<string | null>(null);
  const create = useIamMutation((v: Record<string, string>) => api("groups", { method: "POST", body: v }),
    { success: (v) => `已建立 ${v.groupCode.toUpperCase()}`, invalidate: [iamKeys.groups] });

  // 管理面板顯示最新資料：從清單查詢結果取，mutation 後清單會重新抓取
  const selected = groups.data?.find((g) => g.groupCode === managing) ?? null;

  return (
    <div className="grid gap-4">
      <SectionHeader
        title="群組"
        description="成員透過群組繼承角色，適合依部門或職能批次授權。"
        action={canWrite && <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />新增群組</Button>}
      />

      {groups.isPending ? <TableSkeleton /> : groups.isError ? (
        <LoadError error={groups.error} onRetry={() => groups.refetch()} />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>群組</TableHead>
                <TableHead className="w-20 text-right">成員</TableHead>
                <TableHead>角色</TableHead>
                <TableHead className="w-12"><span className="sr-only">操作</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {groups.data.length === 0 && <EmptyRow colSpan={4}>還沒有群組</EmptyRow>}
              {groups.data.map((group) => (
                <TableRow key={group.groupCode}>
                  <TableCell className="max-w-56">
                    <div className="truncate font-medium">{group.groupName}</div>
                    <span className="font-mono text-xs text-muted-foreground">{group.groupCode}</span>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{group.members.length}</TableCell>
                  <TableCell className="min-w-40">
                    <div className="flex flex-wrap gap-1.5">
                      {group.roles.length === 0 && <span className="text-xs text-muted-foreground">—</span>}
                      {group.roles.map((r) => <Chip key={r} mono>{r}</Chip>)}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" aria-label={`管理 ${group.groupCode}`}
                      onClick={() => setManaging(group.groupCode)}>
                      <Settings2 className="size-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <FormDialog
        open={creating}
        onOpenChange={setCreating}
        title="新增群組"
        submitLabel="建立"
        pending={create.isPending}
        onSubmit={(v) => create.mutateAsync(v)}
        fields={[
          { name: "groupCode", label: "群組代碼", placeholder: "FINANCE_TEAM", hint: "建立後不可修改",
            validate: (v) => (CODE.test(v.trim()) ? undefined : "英文字母開頭，可用英數字與底線") },
          { name: "groupName", label: "名稱", placeholder: "財務部" },
        ]}
      />

      <GroupSheet group={selected} onClose={() => setManaging(null)} canWrite={canWrite}
        canReadUsers={canReadUsers} canReadRoles={canReadRoles} />
    </div>
  );
}

function GroupSheet({ group, onClose, canWrite, canReadUsers, canReadRoles }: {
  group: Group | null;
  onClose: () => void;
  canWrite: boolean;
  canReadUsers: boolean;
  canReadRoles: boolean;
}) {
  const users = useUsers(!!group && canWrite && canReadUsers);
  const roles = useRoles(!!group && canWrite && canReadRoles);
  const [adding, setAdding] = useState<"member" | "role" | null>(null);

  const invalidate = [iamKeys.groups];
  const code = group?.groupCode ?? "";
  const addMember = useIamMutation((u: string) => api(path("groups", code, "members", u), { method: "POST" }),
    { success: (u) => `已加入 ${u}`, invalidate });
  const removeMember = useIamMutation((u: string) => api(path("groups", code, "members", u), { method: "DELETE" }),
    { success: (u) => `已移出 ${u}`, invalidate });
  const assignRole = useIamMutation((r: string) => api(path("groups", code, "roles", r), { method: "POST" }),
    { success: "已指派角色", invalidate });
  const revokeRole = useIamMutation((r: string) => api(path("groups", code, "roles", r), { method: "DELETE" }),
    { success: "已移除角色", invalidate });

  const memberOptions = (users.data ?? [])
    .filter((u) => u.status !== "DEACTIVATED" && !group?.members.includes(u.username))
    .map((u) => ({ value: u.username, label: u.username, detail: u.email }));
  const roleOptions = (roles.data ?? [])
    .filter((r) => !group?.roles.includes(r.roleCode))
    .map((r) => ({ value: r.roleCode, label: r.roleName, detail: r.roleCode }));

  return (
    <Sheet open={!!group} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle className="truncate">{group?.groupName}</SheetTitle>
          <SheetDescription className="font-mono">{group?.groupCode}</SheetDescription>
        </SheetHeader>
        {group && (
          <div className="grid gap-6 px-4 pb-6">
            <section className="grid gap-2">
              <div className="flex items-center justify-between gap-2">
                <h3 className="text-sm font-medium">角色（{group.roles.length}）</h3>
                {canWrite && canReadRoles && <Button size="sm" variant="outline" onClick={() => setAdding("role")}>指派角色</Button>}
              </div>
              <div className="flex flex-wrap gap-1.5">
                {group.roles.length === 0 && <span className="text-sm text-muted-foreground">尚未指派角色</span>}
                {group.roles.map((r) => (
                  <Chip key={r} mono removeLabel={`移除 ${r}`} onRemove={canWrite ? () => revokeRole.mutate(r) : undefined}>{r}</Chip>
                ))}
              </div>
            </section>
            <section className="grid gap-2">
              <div className="flex items-center justify-between gap-2">
                <h3 className="text-sm font-medium">成員（{group.members.length}）</h3>
                {canWrite && canReadUsers && <Button size="sm" variant="outline" onClick={() => setAdding("member")}>加入成員</Button>}
              </div>
              <div className="flex flex-wrap gap-1.5">
                {group.members.length === 0 && <span className="text-sm text-muted-foreground">還沒有成員</span>}
                {group.members.map((m) => (
                  <Chip key={m} removeLabel={`移出 ${m}`} onRemove={canWrite ? () => removeMember.mutate(m) : undefined}>{m}</Chip>
                ))}
              </div>
            </section>
          </div>
        )}

        <SelectDialog
          open={adding === "member"}
          onOpenChange={(open) => !open && setAdding(null)}
          title="加入成員"
          label="成員"
          options={memberOptions}
          emptyText={users.isPending ? "載入中…" : "沒有可加入的成員。"}
          submitLabel="加入"
          pending={addMember.isPending}
          onSubmit={(u) => addMember.mutateAsync(u)}
        />
        <SelectDialog
          open={adding === "role"}
          onOpenChange={(open) => !open && setAdding(null)}
          title="指派角色給群組"
          description="群組內所有成員都會繼承這個角色。"
          label="角色"
          options={roleOptions}
          emptyText={roles.isPending ? "載入中…" : "所有角色都已指派。"}
          submitLabel="指派"
          pending={assignRole.isPending}
          onSubmit={(r) => assignRole.mutateAsync(r)}
        />
      </SheetContent>
    </Sheet>
  );
}
