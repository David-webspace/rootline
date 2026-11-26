import type { TreeNode } from "@/lib/dept-types";

export type TreeIndex = {
  byId: Map<string, TreeNode>;
  /** 由上到下的順序（深度優先），給選單、合併目標清單用。 */
  ordered: TreeNode[];
};

export function indexTree(root: TreeNode | undefined): TreeIndex {
  const byId = new Map<string, TreeNode>();
  const ordered: TreeNode[] = [];
  const walk = (node: TreeNode) => {
    byId.set(node.id, node);
    ordered.push(node);
    node.children.forEach(walk);
  };
  if (root) walk(root);
  return { byId, ordered };
}

/** 從樹根到 id 的路徑（含自己）。 */
export function pathTo(index: TreeIndex, id: string): TreeNode[] {
  const path: TreeNode[] = [];
  let current = index.byId.get(id);
  while (current) {
    path.unshift(current);
    current = current.parentId ? index.byId.get(current.parentId) : undefined;
  }
  return path;
}

/** ancestor 是否為 node 本身或其祖先（搬移、合併時用來排除會成環的目標）。 */
export function isAncestorOrSelf(index: TreeIndex, ancestorId: string, nodeId: string): boolean {
  return pathTo(index, nodeId).some((n) => n.id === ancestorId);
}

/** 可以當作新上層或合併目標：沒刪、沒停用。 */
export const isUsable = (node: TreeNode) => !node.deleted && node.status === "ACTIVE";

/**
 * 搜尋：回傳符合的節點與它們的所有祖先（祖先要一起顯示並展開，才看得到結果在哪）。
 * keyword 為空時回傳 null，表示不篩選。
 */
export function searchTree(index: TreeIndex, keyword: string): { visible: Set<string>; matches: Set<string> } | null {
  const k = keyword.trim().toLowerCase();
  if (!k) return null;
  const matches = new Set<string>();
  const visible = new Set<string>();
  for (const node of index.ordered) {
    if (node.name.toLowerCase().includes(k) || node.code.toLowerCase().includes(k)) {
      matches.add(node.id);
      pathTo(index, node.id).forEach((n) => visible.add(n.id));
    }
  }
  return { visible, matches };
}

/** 預設展開前兩層。 */
export function defaultExpanded(root: TreeNode | undefined): Set<string> {
  const expanded = new Set<string>();
  if (!root) return expanded;
  expanded.add(root.id);
  root.children.forEach((c) => expanded.add(c.id));
  return expanded;
}
