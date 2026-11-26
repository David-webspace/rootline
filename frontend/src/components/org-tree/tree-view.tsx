"use client";

import {
  ArrowDown,
  ArrowUp,
  Ban,
  ChevronRight,
  FolderInput,
  GitMerge,
  History,
  MoreHorizontal,
  Pencil,
  Plus,
  Trash2,
  Users,
} from "lucide-react";
import { useState, type DragEvent } from "react";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { TreeNode } from "@/lib/dept-types";
import { cn } from "@/lib/utils";

export type NodeAction = "create" | "rename" | "up" | "down" | "move" | "merge" | "disable" | "delete" | "restore" | "history";

type Props = {
  root: TreeNode;
  expanded: Set<string>;
  onToggle: (id: string) => void;
  selectedId: string | null;
  onSelect: (id: string) => void;
  /** 搜尋時只顯示這些節點；null 代表不篩選。 */
  visible: Set<string> | null;
  matches: Set<string> | null;
  canWrite: boolean;
  onAction: (action: NodeAction, node: TreeNode) => void;
  /** 拖拉結束：把 sourceId 搬到 targetId 底下。由呼叫端確認後才真的送出。 */
  onDropMove: (sourceId: string, targetId: string) => void;
  /** 拖到 target 是否合法（不能拖到自己或子孫、已刪除或停用的部門）。 */
  canDrop: (sourceId: string, targetId: string) => boolean;
};

/**
 * 可展開的組織樹。拖拉用原生 HTML5 Drag and Drop（桌機）；
 * 每個節點的選單也有「搬移到…」，觸控裝置與鍵盤使用者走選單。
 */
export function TreeView(props: Props) {
  const [dragging, setDragging] = useState<string | null>(null);
  const [dropTarget, setDropTarget] = useState<string | null>(null);

  return (
    <ul role="tree" aria-label="組織樹" className="grid gap-0.5">
      <TreeRow
        {...props}
        node={props.root}
        level={0}
        siblings={[props.root]}
        dragging={dragging}
        dropTarget={dropTarget}
        setDragging={setDragging}
        setDropTarget={setDropTarget}
      />
    </ul>
  );
}

type RowProps = Props & {
  node: TreeNode;
  level: number;
  siblings: TreeNode[];
  dragging: string | null;
  dropTarget: string | null;
  setDragging: (id: string | null) => void;
  setDropTarget: (id: string | null) => void;
};

function TreeRow(props: RowProps) {
  const { node, level, siblings, expanded, selectedId, visible, matches, canWrite, dragging, dropTarget } = props;
  if (visible && !visible.has(node.id)) return null;

  const children = node.children.filter((c) => !visible || visible.has(c.id));
  const hasChildren = children.length > 0;
  // 搜尋時一律展開，才看得到命中的子孫
  const isOpen = hasChildren && (!!visible || expanded.has(node.id));
  const selected = selectedId === node.id;
  const draggable = canWrite && !node.deleted && node.parentId !== null;
  const isDropTarget = dropTarget === node.id;

  const onDragOver = (event: DragEvent) => {
    if (!dragging || !props.canDrop(dragging, node.id)) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
    if (dropTarget !== node.id) props.setDropTarget(node.id);
  };

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    const source = dragging;
    props.setDragging(null);
    props.setDropTarget(null);
    if (source && source !== node.id && props.canDrop(source, node.id)) props.onDropMove(source, node.id);
  };

  const index = siblings.findIndex((s) => s.id === node.id);

  return (
    <li role="treeitem" aria-expanded={hasChildren ? isOpen : undefined} aria-selected={selected} className="min-w-0">
      <div
        draggable={draggable}
        onDragStart={(event) => {
          event.dataTransfer.setData("text/plain", node.id);
          event.dataTransfer.effectAllowed = "move";
          props.setDragging(node.id);
        }}
        onDragEnd={() => {
          props.setDragging(null);
          props.setDropTarget(null);
        }}
        onDragOver={onDragOver}
        onDragLeave={() => isDropTarget && props.setDropTarget(null)}
        onDrop={onDrop}
        style={{ paddingLeft: `${level * 1.125}rem` }}
        className={cn(
          "group flex min-w-0 items-center gap-1 rounded-md pr-1 transition-colors",
          selected ? "bg-primary/12 ring-1 ring-primary/40" : "hover:bg-accent/60",
          isDropTarget && "bg-primary/20 ring-2 ring-primary",
          dragging === node.id && "opacity-40",
          draggable && "cursor-grab active:cursor-grabbing",
        )}
      >
        <button
          type="button"
          onClick={() => hasChildren && props.onToggle(node.id)}
          aria-label={hasChildren ? (isOpen ? `收合 ${node.name}` : `展開 ${node.name}`) : undefined}
          tabIndex={hasChildren ? 0 : -1}
          className={cn(
            "grid size-7 shrink-0 place-items-center rounded text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
            !hasChildren && "invisible",
          )}
        >
          <ChevronRight className={cn("size-4 transition-transform", isOpen && "rotate-90")} aria-hidden />
        </button>

        <button
          type="button"
          onClick={() => props.onSelect(node.id)}
          className="flex min-w-0 flex-1 items-center gap-2 rounded py-1.5 text-left focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        >
          <span
            className={cn(
              "truncate text-sm",
              node.deleted && "text-muted-foreground line-through",
              !node.deleted && node.status === "DISABLED" && "text-muted-foreground",
              matches?.has(node.id) && "font-semibold text-primary",
            )}
          >
            {node.name}
          </span>
          <span className="hidden shrink-0 font-mono text-[11px] text-muted-foreground sm:inline">{node.code}</span>
          {node.deleted ? (
            <Badge variant="outline" className="shrink-0 border-destructive/40 text-destructive">已刪除</Badge>
          ) : node.status === "DISABLED" ? (
            <Badge variant="outline" className="shrink-0 text-muted-foreground">停用</Badge>
          ) : null}
        </button>

        <span
          className="flex shrink-0 items-center gap-1 text-xs text-muted-foreground tabular-nums"
          title={`本部門 ${node.directEmployeeCount} 人，含子部門共 ${node.totalEmployeeCount} 人`}
        >
          <Users className="size-3" aria-hidden />
          {node.totalEmployeeCount}
        </span>

        <NodeMenu
          node={node}
          canWrite={canWrite}
          canMoveUp={index > 0}
          canMoveDown={index >= 0 && index < siblings.length - 1}
          onAction={(action) => props.onAction(action, node)}
        />
      </div>

      {isOpen && (
        <ul role="group" className="grid gap-0.5">
          {children.map((child) => (
            <TreeRow key={child.id} {...props} node={child} level={level + 1} siblings={children} />
          ))}
        </ul>
      )}
    </li>
  );
}

function NodeMenu({
  node,
  canWrite,
  canMoveUp,
  canMoveDown,
  onAction,
}: {
  node: TreeNode;
  canWrite: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onAction: (action: NodeAction) => void;
}) {
  const active = !node.deleted && node.status === "ACTIVE";
  const isTop = node.parentId === null;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`${node.name} 的操作`}
        className="grid size-7 shrink-0 place-items-center rounded text-muted-foreground opacity-100 hover:bg-accent hover:text-foreground focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none data-[state=open]:opacity-100 sm:opacity-0 sm:group-hover:opacity-100"
      >
        <MoreHorizontal className="size-4" aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-48">
        {canWrite && node.deleted && (
          <DropdownMenuItem onSelect={() => onAction("restore")}>
            <History aria-hidden /> 時光機復活
          </DropdownMenuItem>
        )}
        {canWrite && active && (
          <>
            <DropdownMenuItem onSelect={() => onAction("create")}><Plus aria-hidden /> 新增子部門</DropdownMenuItem>
            <DropdownMenuItem onSelect={() => onAction("rename")}><Pencil aria-hidden /> 改名</DropdownMenuItem>
            <DropdownMenuItem disabled={!canMoveUp} onSelect={() => onAction("up")}><ArrowUp aria-hidden /> 往上排</DropdownMenuItem>
            <DropdownMenuItem disabled={!canMoveDown} onSelect={() => onAction("down")}><ArrowDown aria-hidden /> 往下排</DropdownMenuItem>
            <DropdownMenuItem disabled={isTop} onSelect={() => onAction("move")}><FolderInput aria-hidden /> 搬移到…</DropdownMenuItem>
            <DropdownMenuItem disabled={isTop} onSelect={() => onAction("merge")}><GitMerge aria-hidden /> 合併到…</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={() => onAction("disable")}><Ban aria-hidden /> 停用（含子部門）</DropdownMenuItem>
            <DropdownMenuItem variant="destructive" disabled={isTop} onSelect={() => onAction("delete")}>
              <Trash2 aria-hidden /> 刪除（含子部門）
            </DropdownMenuItem>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuItem onSelect={() => onAction("history")}><History aria-hidden /> 查看歷程</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
