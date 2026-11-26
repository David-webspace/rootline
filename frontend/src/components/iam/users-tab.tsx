"use client";

import { MoreHorizontal, Plus } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { iamKeys, path, useIamMutation, useRoles, useUsers } from "@/hooks/use-iam";
import { api } from "@/lib/api";
import type { User } from "@/lib/iam-types";
import { ConfirmDialog, FormDialog, SelectDialog } from "./dialogs";
import { Chip, EmptyRow, LoadError, SectionHeader, StatusBadge, TableSkeleton } from "./parts";
import { UserContextSheet } from "./user-context-sheet";

type Pending = { kind: "deactivate" | "revoke"; user: User; role?: string } | null;

export function UsersTab({ canWrite, canReadRoles, currentUser }: {
  canWrite: boolean;
  canReadRoles: boolean;
  currentUser: string;
}) {
  const users = useUsers();
  const roles = useRoles(canWrite && canReadRoles);
  const [creating, setCreating] = useState(false);
  const [assigning, setAssigning] = useState<User | null>(null);
  const [viewing, setViewing] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<Pending>(null);

  const invalidate = [iamKeys.users];
  const create = useIamMutation((v: Record<string, string>) => api("users", { method: "POST", body: v }),
    { success: (v) => `已建立 ${v.username}`, invalidate });
  const assign = useIamMutation((v: { user: string; role: string }) =>
    api(path("users", v.user, "roles", v.role), { method: "POST" }), { success: "已指派角色", invalidate });
  const revoke = useIamMutation((v: { user: string; role: string }) =>
    api(path("users", v.user, "roles", v.role), { method: "DELETE" }), { success: "已移除角色", invalidate });
  const deactivate = useIamMutation((user: string) => api(path("users", user), { method: "DELETE" }),
    { success: (u) => `已停用 ${u}`, invalidate });
  const reactivate = useIamMutation((user: string) => api(path("users", user, "reactivate"), { method: "POST" }),
    { success: (u) => `已復權 ${u}`, invalidate });
  const unlock = useIamMutation((user: string) => api(path("users", user, "unlock"), { method: "POST" }),
    { success: (u) => `已解鎖 ${u}`, invalidate });

  const roleOptions = (roles.data ?? [])
    .filter((r) => !assigning?.roles.includes(r.roleCode))
    .map((r) => ({ value: r.roleCode, label: r.roleName, detail: r.roleCode }));

  return (
    <div className="grid gap-4">
      <SectionHeader
        title="成員"
        description="租戶內的帳號。停用為邏輯刪除，可隨時復權。"
        action={canWrite && <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />新增成員</Button>}
      />

      {users.isPending ? <TableSkeleton /> : users.isError ? (
        <LoadError error={users.error} onRetry={() => users.refetch()} />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>帳號</TableHead>
                <TableHead className="hidden md:table-cell">Email</TableHead>
                <TableHead>狀態</TableHead>
                <TableHead>角色</TableHead>
                <TableHead className="w-12"><span className="sr-only">操作</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.data.length === 0 && <EmptyRow colSpan={5}>還沒有成員</EmptyRow>}
              {users.data.map((user) => (
                <TableRow key={user.id}>
                  <TableCell className="max-w-40 font-medium">
                    <button type="button" className="truncate text-left hover:text-primary hover:underline"
                      onClick={() => setViewing(user.username)}>
                      {user.username}
                    </button>
                    {user.username === currentUser && <span className="ml-2 text-xs text-muted-foreground">（你）</span>}
                  </TableCell>
                  <TableCell className="hidden max-w-56 truncate text-muted-foreground md:table-cell">{user.email}</TableCell>
                  <TableCell><StatusBadge status={user.status} /></TableCell>
                  <TableCell className="min-w-40">
                    <div className="flex flex-wrap gap-1.5">
                      {user.roles.length === 0 && <span className="text-xs text-muted-foreground">—</span>}
                      {user.roles.map((role) => (
                        <Chip key={role} mono removeLabel={`移除 ${user.username} 的 ${role}`}
                          onRemove={canWrite ? () => setConfirm({ kind: "revoke", user, role }) : undefined}>
                          {role}
                        </Chip>
                      ))}
                    </div>
                  </TableCell>
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" aria-label={`${user.username} 的操作`}>
                          <MoreHorizontal className="size-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => setViewing(user.username)}>檢視權限脈絡</DropdownMenuItem>
                        {canWrite && (
                          <>
                            <DropdownMenuItem disabled={user.status === "DEACTIVATED" || !canReadRoles}
                              onSelect={() => setAssigning(user)}>指派角色</DropdownMenuItem>
                            <DropdownMenuSeparator />
                            {user.status === "LOCKED" && (
                              <DropdownMenuItem onSelect={() => unlock.mutate(user.username)}>解除鎖定</DropdownMenuItem>
                            )}
                            {user.status === "DEACTIVATED" ? (
                              <DropdownMenuItem onSelect={() => reactivate.mutate(user.username)}>復權</DropdownMenuItem>
                            ) : (
                              <DropdownMenuItem variant="destructive" disabled={user.username === currentUser}
                                onSelect={() => setConfirm({ kind: "deactivate", user })}>停用帳號</DropdownMenuItem>
                            )}
                          </>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
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
        title="新增成員"
        description="新帳號預設沒有任何角色。"
        submitLabel="建立"
        pending={create.isPending}
        onSubmit={(v) => create.mutateAsync(v)}
        fields={[
          { name: "username", label: "帳號", placeholder: "alex.chen",
            validate: (v) => (/^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$/.test(v.trim()) ? undefined : "3–64 字元，可用英數字與 . _ -") },
          { name: "email", label: "Email", type: "email", placeholder: "alex@northwind.example",
            validate: (v) => (/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v.trim()) ? undefined : "Email 格式不正確") },
          { name: "password", label: "初始密碼", type: "password", hint: "至少 8 個字元，請另行安全地告知對方",
            validate: (v) => (v.length >= 8 ? undefined : "至少 8 個字元") },
        ]}
      />

      <SelectDialog
        open={!!assigning}
        onOpenChange={(open) => !open && setAssigning(null)}
        title={`指派角色給 ${assigning?.username ?? ""}`}
        label="角色"
        options={roleOptions}
        emptyText="所有角色都已指派給這位成員。"
        submitLabel="指派"
        pending={assign.isPending}
        onSubmit={(role) => assign.mutateAsync({ user: assigning!.username, role })}
      />

      <ConfirmDialog
        open={!!confirm}
        onOpenChange={(open) => !open && setConfirm(null)}
        destructive
        title={confirm?.kind === "deactivate" ? "停用帳號" : "移除角色"}
        confirmLabel={confirm?.kind === "deactivate" ? "停用" : "移除"}
        description={confirm?.kind === "deactivate"
          ? `${confirm.user.username} 將無法登入，之後可以復權。`
          : `${confirm?.user.username} 將失去 ${confirm?.role} 的所有權限（重新登入後生效）。`}
        onConfirm={() => {
          if (confirm?.kind === "deactivate") deactivate.mutate(confirm.user.username);
          else if (confirm?.role) revoke.mutate({ user: confirm.user.username, role: confirm.role });
          setConfirm(null);
        }}
      />

      <UserContextSheet username={viewing} onClose={() => setViewing(null)} />
    </div>
  );
}
