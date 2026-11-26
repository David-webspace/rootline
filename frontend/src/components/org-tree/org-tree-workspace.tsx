"use client";

import { ChevronsDownUp, ChevronsUpDown, Eye, EyeOff, Plus, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { ConfirmDialog, FormDialog, SelectDialog } from "@/components/iam/dialogs";
import { LoadError, TableSkeleton } from "@/components/iam/parts";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useDeptMutation, useRoots, useTree } from "@/hooks/use-dept";
import { path } from "@/hooks/use-iam";
import { api } from "@/lib/api";
import { DEPT_CODE_PATTERN, type MergeResult, type RestoreResult, type TreeNode } from "@/lib/dept-types";
import { hasPermission } from "@/lib/permissions";
import { DepartmentDetail, type DetailTab } from "./department-detail";
import { TreeView, type NodeAction } from "./tree-view";
import { defaultExpanded, indexTree, isAncestorOrSelf, isUsable, pathTo, searchTree } from "./tree-utils";

type Pending = { action: NodeAction; node: TreeNode } | null;

const CODE_FIELD = {
  name: "code",
  label: "部門代碼",
  placeholder: "例如 ENG-PLATFORM",
  hint: "大寫英數、- 與 _，建立後不能修改",
  validate: (v: string) => (DEPT_CODE_PATTERN.test(v.trim()) ? undefined : "格式不符：大寫英數、- 與 _，最多 50 字"),
};
const NAME_FIELD = { name: "name", label: "部門名稱", placeholder: "例如 平台工程部" };

export function OrgTreeWorkspace({ permissions }: { permissions: string[] }) {
  const can = (p: string) => hasPermission(permissions, p);
  const canWrite = can("DEPT:TREE_WRITE");
  const canStaff = can("DEPT:STAFF_WRITE");
  const canReadUsers = can("IAM:USER_READ");

  const roots = useRoots();
  const [pickedRoot, setPickedRoot] = useState<string | null>(null);
  const rootId = pickedRoot ?? roots.data?.content[0]?.id ?? null;
  const [showDeleted, setShowDeleted] = useState(false);
  const tree = useTree(rootId, showDeleted);

  const index = useMemo(() => indexTree(tree.data), [tree.data]);
  const [keyword, setKeyword] = useState("");
  const search = useMemo(() => searchTree(index, keyword), [index, keyword]);
  // null 代表使用者還沒動過展開狀態，用預設（前兩層）
  const [expandedState, setExpanded] = useState<Set<string> | null>(null);
  const expanded = expandedState ?? defaultExpanded(tree.data);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = (selectedId && index.byId.get(selectedId)) || tree.data;
  const [tab, setTab] = useState<DetailTab>("overview");

  const [pending, setPending] = useState<Pending>(null);
  const [dropMove, setDropMove] = useState<{ source: TreeNode; target: TreeNode } | null>(null);
  const [creatingTop, setCreatingTop] = useState(false);

  const close = () => setPending(null);
  const nameOf = (id: string) => index.byId.get(id)?.name ?? id;

  const create = useDeptMutation(
    (v: { parentId: string | null; code: string; name: string }) => api<string>("departments", { method: "POST", body: v }),
    (v) => `已建立「${v.name}」`,
  );
  const rename = useDeptMutation(
    (v: { id: string; name: string }) => api(`departments/${path(v.id)}/name`, { method: "PATCH", body: { name: v.name } }),
    (v) => `已改名為「${v.name}」`,
  );
  const reorder = useDeptMutation(
    // 同層全部重新編號（0、10、20…），只送有變動的；後端每次改排序都是一個事件
    async (changes: { id: string; sortOrder: number }[]) => {
      for (const c of changes) {
        await api(`departments/${path(c.id)}/sort-order`, { method: "PATCH", body: { sortOrder: c.sortOrder } });
      }
    },
    "已更新排序",
  );
  const move = useDeptMutation(
    (v: { departmentId: string; newParentId: string }) => api("departments/move", { method: "POST", body: v }),
    (v) => `已搬到「${nameOf(v.newParentId)}」底下`,
  );
  const merge = useDeptMutation(
    (v: { id: string; targetDeptId: string }) =>
      api<MergeResult>(`departments/${path(v.id)}/merge`, { method: "POST", body: { targetDeptId: v.targetDeptId } }),
    (v, r) => `已併入「${nameOf(v.targetDeptId)}」：搬移 ${r.movedChildren} 個子部門、${r.movedEmployees} 位成員`,
  );
  const disable = useDeptMutation(
    (id: string) => api<number>(`departments/${path(id)}/disable`, { method: "PATCH" }),
    (_id, count) => `已停用 ${count} 個部門`,
  );
  const remove = useDeptMutation(
    (id: string) => api<number>(`departments/${path(id)}`, { method: "DELETE" }),
    (_id, count) => `已刪除 ${count} 個部門，可在「顯示已刪除」中用時光機復活`,
  );
  const restore = useDeptMutation(
    (id: string) => api<RestoreResult>(`departments/${path(id)}/restore`, { method: "POST" }),
    (_id, r) => `已復活 ${r.restored} 個部門${r.reattachedToRoot ? "；原上層已不存在，改掛到最上層" : ""}`,
  );

  function onAction(action: NodeAction, node: TreeNode) {
    if (action === "history") {
      setSelectedId(node.id);
      setTab("history");
    } else if (action === "up" || action === "down") {
      const parent = node.parentId ? index.byId.get(node.parentId) : undefined;
      if (!parent) return;
      const order = [...parent.children];
      const i = order.findIndex((c) => c.id === node.id);
      const j = action === "up" ? i - 1 : i + 1;
      if (i < 0 || j < 0 || j >= order.length) return;
      [order[i], order[j]] = [order[j], order[i]];
      const changes = order
        .map((c, k) => ({ id: c.id, sortOrder: k * 10, before: c.sortOrder }))
        .filter((c) => c.sortOrder !== c.before)
        .map(({ id, sortOrder }) => ({ id, sortOrder }));
      reorder.mutate(changes);
    } else {
      setPending({ action, node });
    }
  }

  /** 可當作 node 的新上層 / 合併目標的部門，附上路徑方便辨認同名部門。 */
  function targets(node: TreeNode, kind: "move" | "merge") {
    return index.ordered
      .filter((c) => isUsable(c) && !isAncestorOrSelf(index, node.id, c.id))
      .filter((c) => kind === "merge" || c.id !== node.parentId)
      .map((c) => ({
        value: c.id,
        label: c.name,
        detail: pathTo(index, c.id).slice(0, -1).map((p) => p.name).join(" / ") || c.code,
      }));
  }

  const canDrop = (sourceId: string, targetId: string) => {
    const source = index.byId.get(sourceId);
    const target = index.byId.get(targetId);
    return !!source && !!target && isUsable(target) && target.id !== source.parentId
      && !isAncestorOrSelf(index, sourceId, targetId);
  };

  if (roots.isPending) return <TableSkeleton rows={6} />;
  if (roots.isError) return <LoadError error={roots.error} onRetry={() => roots.refetch()} />;

  if (!rootId) {
    return (
      <div className="grid justify-items-start gap-3 rounded-lg border border-dashed border-border p-8 text-sm text-muted-foreground">
        <p>這個租戶還沒有任何部門。</p>
        {canWrite && (
          <Button onClick={() => setCreatingTop(true)}><Plus aria-hidden /> 建立最上層部門</Button>
        )}
        <FormDialog
          open={creatingTop}
          onOpenChange={setCreatingTop}
          title="建立最上層部門"
          fields={[CODE_FIELD, NAME_FIELD]}
          submitLabel="建立"
          pending={create.isPending}
          onSubmit={(v) => create.mutateAsync({ parentId: null, code: v.code.trim(), name: v.name.trim() })}
        />
      </div>
    );
  }

  const toggle = (id: string) =>
    setExpanded(() => {
      const next = new Set(expanded);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,26rem)_minmax(0,1fr)]">
      <Card className="min-w-0 gap-3 xl:sticky xl:top-4">
        <CardHeader className="gap-3">
          {(roots.data.content.length ?? 0) > 1 && (
            <Select value={rootId} onValueChange={(v) => { setPickedRoot(v); setSelectedId(null); setExpanded(null); }}>
              <SelectTrigger aria-label="選擇組織" className="w-full min-w-0"><SelectValue /></SelectTrigger>
              <SelectContent>
                {roots.data.content.map((r) => <SelectItem key={r.id} value={r.id}>{r.name}</SelectItem>)}
              </SelectContent>
            </Select>
          )}
          <div className="relative min-w-0">
            <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              type="search"
              placeholder="搜尋部門名稱或代碼"
              aria-label="搜尋部門"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              className="pl-8"
            />
          </div>
          <div className="flex flex-wrap items-center gap-1">
            <Button
              size="sm"
              variant={showDeleted ? "secondary" : "ghost"}
              aria-pressed={showDeleted}
              onClick={() => setShowDeleted((v) => !v)}
            >
              {showDeleted ? <Eye aria-hidden /> : <EyeOff aria-hidden />} 顯示已刪除
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setExpanded(new Set(index.ordered.map((n) => n.id)))}>
              <ChevronsUpDown aria-hidden /> 全部展開
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setExpanded(new Set(tree.data ? [tree.data.id] : []))}>
              <ChevronsDownUp aria-hidden /> 收合
            </Button>
          </div>
          {canWrite && (
            <p className="hidden text-xs text-muted-foreground md:block">拖拉部門到另一個部門上即可搬移。</p>
          )}
        </CardHeader>
        <CardContent className="min-w-0 xl:max-h-[calc(100dvh-16rem)] xl:overflow-y-auto">
          {tree.isPending ? (
            <TableSkeleton rows={6} />
          ) : tree.isError ? (
            <LoadError error={tree.error} onRetry={() => tree.refetch()} />
          ) : search && search.matches.size === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">找不到符合「{keyword.trim()}」的部門。</p>
          ) : (
            <TreeView
              root={tree.data}
              expanded={expanded}
              onToggle={toggle}
              selectedId={selected?.id ?? null}
              onSelect={(id) => setSelectedId(id)}
              visible={search?.visible ?? null}
              matches={search?.matches ?? null}
              canWrite={canWrite}
              onAction={onAction}
              canDrop={canDrop}
              onDropMove={(sourceId, targetId) => {
                const source = index.byId.get(sourceId);
                const target = index.byId.get(targetId);
                if (source && target) setDropMove({ source, target });
              }}
            />
          )}
        </CardContent>
      </Card>

      {selected && (
        <DepartmentDetail
          node={selected}
          index={index}
          tab={tab}
          onTabChange={setTab}
          onSelect={setSelectedId}
          onRestore={(node) => setPending({ action: "restore", node })}
          canWrite={canWrite}
          canStaff={canStaff}
          canReadUsers={canReadUsers}
        />
      )}

      {/* ── 對話框：同一時間只會開一個，依 pending.action 決定 ── */}
      <FormDialog
        open={pending?.action === "create"}
        onOpenChange={(o) => !o && close()}
        title="新增子部門"
        description={pending ? `建立在「${pending.node.name}」底下` : undefined}
        fields={[CODE_FIELD, NAME_FIELD]}
        submitLabel="建立"
        pending={create.isPending}
        onSubmit={async (v) => {
          if (!pending) return;
          const parent = pending.node;
          await create.mutateAsync({ parentId: parent.id, code: v.code.trim(), name: v.name.trim() });
          setExpanded(new Set(expanded).add(parent.id));
        }}
      />
      <FormDialog
        open={pending?.action === "rename"}
        onOpenChange={(o) => !o && close()}
        title="改名"
        fields={[NAME_FIELD]}
        initial={{ name: pending?.node.name ?? "" }}
        submitLabel="儲存"
        pending={rename.isPending}
        onSubmit={(v) => rename.mutateAsync({ id: pending!.node.id, name: v.name.trim() })}
      />
      <SelectDialog
        open={pending?.action === "move"}
        onOpenChange={(o) => !o && close()}
        title="搬移部門"
        description={pending ? `把「${pending.node.name}」連同子部門搬到：` : undefined}
        label="新的上層部門"
        options={pending?.action === "move" ? targets(pending.node, "move") : []}
        emptyText="沒有可以搬過去的部門。"
        submitLabel="搬移"
        pending={move.isPending}
        onSubmit={(target) => move.mutateAsync({ departmentId: pending!.node.id, newParentId: target })}
      />
      <SelectDialog
        open={pending?.action === "merge"}
        onOpenChange={(o) => !o && close()}
        title="合併部門"
        description={
          pending
            ? `「${pending.node.name}」的子部門與成員會搬到目標部門，之後它本身會被標記為已合併（停用）。事件會完整保留。`
            : undefined
        }
        label="併入"
        options={pending?.action === "merge" ? targets(pending.node, "merge") : []}
        emptyText="沒有可以合併的目標。"
        submitLabel="合併"
        pending={merge.isPending}
        onSubmit={(target) => merge.mutateAsync({ id: pending!.node.id, targetDeptId: target })}
      />
      <ConfirmDialog
        open={pending?.action === "disable"}
        onOpenChange={(o) => !o && close()}
        title="停用部門"
        description={`「${pending?.node.name}」與所有子部門都會停用；只要其中任一部門還有成員，整批都不會生效。`}
        confirmLabel="停用"
        destructive
        onConfirm={() => pending && disable.mutate(pending.node.id)}
      />
      <ConfirmDialog
        open={pending?.action === "delete"}
        onOpenChange={(o) => !o && close()}
        title="刪除部門"
        description={`「${pending?.node.name}」與所有子部門都會刪除（邏輯刪除，之後可以用時光機復活）；只要其中任一部門還有成員，整批都不會生效。`}
        confirmLabel="刪除"
        destructive
        onConfirm={() => pending && remove.mutate(pending.node.id)}
      />
      <ConfirmDialog
        open={pending?.action === "restore"}
        onOpenChange={(o) => !o && close()}
        title="時光機復活"
        description={`復原「${pending?.node.name}」以及它底下已刪除的子部門。若原本的上層也已刪除，會改掛到最上層。`}
        confirmLabel="復活"
        onConfirm={() => pending && restore.mutate(pending.node.id)}
      />
      <ConfirmDialog
        open={dropMove !== null}
        onOpenChange={(o) => !o && setDropMove(null)}
        title="搬移部門"
        description={dropMove ? `把「${dropMove.source.name}」連同子部門搬到「${dropMove.target.name}」底下？` : ""}
        confirmLabel="搬移"
        onConfirm={() => {
          if (!dropMove) return;
          move.mutate({ departmentId: dropMove.source.id, newParentId: dropMove.target.id });
          setExpanded(new Set(expanded).add(dropMove.target.id));
        }}
      />
    </div>
  );
}
