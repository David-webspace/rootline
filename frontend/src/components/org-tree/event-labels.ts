import type { HistoryEntry } from "@/lib/dept-types";

type Described = { title: string; detail?: string; tone: "neutral" | "positive" | "negative" };

const text = (payload: Record<string, unknown>, key: string) => {
  const value = payload[key];
  return typeof value === "string" ? value : value == null ? null : String(value);
};

/**
 * 把事件轉成給人看的一句話。部門 id 透過 nameOf 換成名稱（找不到時顯示 id，
 * 例如上層已被刪除且目前沒顯示已刪除部門）。
 */
export function describeEvent(entry: HistoryEntry, nameOf: (id: string | null) => string): Described {
  const p = entry.payload;
  switch (entry.eventType) {
    case "DepartmentCreated":
      return {
        title: "建立部門",
        detail: `${text(p, "name")}（${text(p, "code")}），上層：${nameOf(text(p, "parentId"))}`,
        tone: "positive",
      };
    case "DepartmentRenamed":
      return { title: "改名", detail: `${text(p, "oldName")} → ${text(p, "newName")}`, tone: "neutral" };
    case "DepartmentMoved":
      return {
        title: "搬移",
        detail: `${nameOf(text(p, "oldParentId"))} → ${nameOf(text(p, "newParentId"))}`,
        tone: "neutral",
      };
    case "DepartmentSortOrderChanged":
      return { title: "調整排序", detail: `排序值改為 ${text(p, "sortOrder")}`, tone: "neutral" };
    case "DepartmentDisabled":
      return { title: "停用", tone: "negative" };
    case "EmployeeAssigned":
      return { title: "加入成員", detail: text(p, "employeeId") ?? undefined, tone: "positive" };
    case "EmployeeUnassigned":
      return { title: "移出成員", detail: text(p, "employeeId") ?? undefined, tone: "negative" };
    case "DepartmentDeleted":
      return { title: "刪除", tone: "negative" };
    case "DepartmentRestored":
      return {
        title: "時光機復活",
        detail: p.reattachedToRoot ? "原上層已不存在，改掛到最上層" : undefined,
        tone: "positive",
      };
    case "DepartmentMerged":
      return { title: "併入其他部門", detail: `併入 ${nameOf(text(p, "targetDepartmentId"))}`, tone: "negative" };
    default:
      return { title: entry.eventType, tone: "neutral" };
  }
}

const formatter = new Intl.DateTimeFormat("zh-TW", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
});

export const formatTime = (iso: string) => formatter.format(new Date(iso));

/** `<input type="datetime-local">` 的值（本地時間，到秒）。 */
export function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
