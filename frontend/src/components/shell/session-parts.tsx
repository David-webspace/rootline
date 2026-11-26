import "server-only";
import { NAV_ITEMS } from "@/lib/navigation";
import { hasAny } from "@/lib/permissions";
import { getCurrentUser } from "@/lib/server/session";
import { MobileNav } from "./mobile-nav";
import { NavLinks } from "./nav-links";
import { UserMenu } from "./user-menu";

/**
 * 需要讀 session 的外殼元件。各自放在 layout 的 <Suspense> 裡，外殼其他部分可以先顯示；
 * 多次呼叫 getCurrentUser 會命中 private cache，不會重複解析 cookie。
 */
async function visibleItems() {
  const user = await getCurrentUser();
  return NAV_ITEMS.filter(
    (item) =>
      (!item.onlyTenant || item.onlyTenant === user.tenantId) &&
      (item.permissions.length === 0 || hasAny(user.permissions, item.permissions)),
  );
}

export async function SidebarNav() {
  return <NavLinks items={await visibleItems()} />;
}

export async function MobileNavLoader() {
  return <MobileNav items={await visibleItems()} />;
}

export async function UserMenuLoader() {
  const user = await getCurrentUser();
  return <UserMenu username={user.username} tenantId={user.tenantId} roles={user.roles} />;
}
