"use client";

import { Lock, Pencil, Plus } from "lucide-react";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { iamKeys, path, useIamMutation, usePermissionDictionary, useRoles } from "@/hooks/use-iam";
import { api } from "@/lib/api";
import { authority, type Permission, type Role } from "@/lib/iam-types";
import { ConfirmDialog, FormDialog, SelectDialog } from "./dialogs";
import { Chip, EmptyRow, LoadError, SectionHeader, TableSkeleton } from "./parts";

const CODE = /^[A-Za-z][A-Za-z0-9_]{1,63}$/;

export function RolesTab({ canWrite }: { canWrite: boolean }) {
  const roles = useRoles();
  const dictionary = usePermissionDictionary(canWrite);
  const [creating, setCreating] = useState(false);
  const [renaming, setRenaming] = useState<Role | null>(null);
  const [granting, setGranting] = useState<Role | null>(null);
  const [revoking, setRevoking] = useState<{ role: Role; permission: Permission } | null>(null);

  const invalidate = [iamKeys.roles];
  const create = useIamMutation((v: Record<string, string>) => api("roles", { method: "POST", body: v }),
    { success: (v) => `已建立 ${v.roleCode.toUpperCase()}`, invalidate });
  const rename = useIamMutation((v: { code: string; name: string }) =>
    api(path("roles", v.code, "name"), { method: "PUT", body: { name: v.name } }), { success: "已更新名稱", invalidate });
  const grant = useIamMutation((v: { code: string; authority: string }) => {
    const [systemCode, permissionCode] = v.authority.split(":");
    return api(path("roles", v.code, "permissions"), { method: "POST", body: { systemCode, permissionCode } });
  }, { success: "已授予權限", invalidate });
  const revoke = useIamMutation((v: { code: string; permission: Permission }) =>
    api(path("roles", v.code, "permissions", v.permission.systemCode, v.permission.permissionCode), { method: "DELETE" }),
  { success: "已收回權限", invalidate });

  const grantOptions = (dictionary.data ?? [])
    .filter((d) => !granting?.permissions.some((p) => authority(p) === authority(d)))
    .map((d) => ({ value: authority(d), label: d.permissionName, detail: authority(d) }));

  return (
    <div className="grid gap-4">
      <SectionHeader
        title="角色"
        description="角色是一組權限。系統角色（例如租戶管理員）不可修改，避免把自己鎖在門外。"
        action={canWrite && <Button size="sm" onClick={() => setCreating(true)}><Plus className="size-4" />新增角色</Button>}
      />

      {roles.isPending ? <TableSkeleton /> : roles.isError ? (
        <LoadError error={roles.error} onRetry={() => roles.refetch()} />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>角色</TableHead>
                <TableHead>權限</TableHead>
                {canWrite && <TableHead className="w-28"><span className="sr-only">操作</span></TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {roles.data.length === 0 && <EmptyRow colSpan={3}>還沒有角色</EmptyRow>}
              {roles.data.map((role) => (
                <TableRow key={role.id}>
                  <TableCell className="max-w-56 align-top">
                    <div className="flex min-w-0 items-center gap-2">
                      <span className="truncate font-medium">{role.roleName}</span>
                      {role.systemRoot && (
                        <Badge variant="outline" className="shrink-0 gap-1 text-muted-foreground">
                          <Lock className="size-3" aria-hidden />系統
                        </Badge>
                      )}
                    </div>
                    <span className="font-mono text-xs text-muted-foreground">{role.roleCode}</span>
                  </TableCell>
                  <TableCell className="min-w-56 align-top">
                    <div className="flex flex-wrap gap-1.5">
                      {role.permissions.length === 0 && <span className="text-xs text-muted-foreground">尚未授予權限</span>}
                      {role.permissions.map((p) => (
                        <Tooltip key={authority(p)}>
                          <TooltipTrigger asChild>
                            <span>
                              <Chip mono removeLabel={`收回 ${authority(p)}`}
                                onRemove={canWrite && !role.systemRoot ? () => setRevoking({ role, permission: p }) : undefined}>
                                {authority(p)}
                              </Chip>
                            </span>
                          </TooltipTrigger>
                          <TooltipContent>{p.permissionName}</TooltipContent>
                        </Tooltip>
                      ))}
                    </div>
                  </TableCell>
                  {canWrite && (
                    <TableCell className="align-top">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" aria-label={`重新命名 ${role.roleCode}`}
                          disabled={role.systemRoot} onClick={() => setRenaming(role)}>
                          <Pencil className="size-4" />
                        </Button>
                        <Button variant="ghost" size="icon" aria-label={`授予權限給 ${role.roleCode}`}
                          disabled={role.systemRoot} onClick={() => setGranting(role)}>
                          <Plus className="size-4" />
                        </Button>
                      </div>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <FormDialog
        open={creating}
        onOpenChange={setCreating}
        title="新增角色"
        submitLabel="建立"
        pending={create.isPending}
        onSubmit={(v) => create.mutateAsync(v)}
        fields={[
          { name: "roleCode", label: "角色代碼", placeholder: "FINANCE_VIEWER", hint: "建立後不可修改",
            validate: (v) => (CODE.test(v.trim()) ? undefined : "英文字母開頭，可用英數字與底線") },
          { name: "roleName", label: "名稱", placeholder: "財務檢視" },
        ]}
      />

      <FormDialog
        open={!!renaming}
        onOpenChange={(open) => !open && setRenaming(null)}
        title={`重新命名 ${renaming?.roleCode ?? ""}`}
        submitLabel="儲存"
        initial={{ name: renaming?.roleName ?? "" }}
        pending={rename.isPending}
        onSubmit={(v) => rename.mutateAsync({ code: renaming!.roleCode, name: v.name })}
        fields={[{ name: "name", label: "名稱" }]}
      />

      <SelectDialog
        open={!!granting}
        onOpenChange={(open) => !open && setGranting(null)}
        title={`授予權限給 ${granting?.roleName ?? ""}`}
        description="清單來自權限字典（由組織樹服務定義並同步過來）。"
        label="權限"
        options={grantOptions}
        emptyText={dictionary.isPending ? "載入中…" : "沒有可授予的權限。"}
        submitLabel="授予"
        pending={grant.isPending}
        onSubmit={(value) => grant.mutateAsync({ code: granting!.roleCode, authority: value })}
      />

      <ConfirmDialog
        open={!!revoking}
        onOpenChange={(open) => !open && setRevoking(null)}
        destructive
        title="收回權限"
        confirmLabel="收回"
        description={`擁有 ${revoking?.role.roleCode} 的人將失去 ${revoking ? authority(revoking.permission) : ""}（重新登入後生效）。`}
        onConfirm={() => {
          if (revoking) revoke.mutate({ code: revoking.role.roleCode, permission: revoking.permission });
          setRevoking(null);
        }}
      />
    </div>
  );
}
