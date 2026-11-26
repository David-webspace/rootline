"use client";

import { BadgeCheck, Building2, KeyRound, Network, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { NavItem } from "@/lib/navigation";
import { cn } from "@/lib/utils";

const ICONS = { tree: Network, shield: ShieldCheck, building: Building2, key: KeyRound, badge: BadgeCheck };

export function NavLinks({ items, onNavigate }: { items: NavItem[]; onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav aria-label="主選單" className="grid gap-1">
      {items.map((item) => {
        const Icon = ICONS[item.icon];
        const active = pathname.startsWith(item.href);
        const base = "flex min-w-0 items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors";
        if (item.comingIn) {
          return (
            <span key={item.href} aria-disabled className={cn(base, "cursor-not-allowed text-muted-foreground/60")}>
              <Icon className="size-4 shrink-0" aria-hidden />
              <span className="truncate">{item.label}</span>
              <span className="ml-auto shrink-0 rounded border border-border px-1.5 text-[10px] leading-4">
                {item.comingIn}
              </span>
            </span>
          );
        }
        return (
          <Link
            key={item.href}
            href={item.href}
            onClick={onNavigate}
            aria-current={active ? "page" : undefined}
            className={cn(
              base,
              active
                ? "bg-sidebar-accent font-medium text-sidebar-accent-foreground"
                : "text-sidebar-foreground/80 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground",
            )}
          >
            <Icon className={cn("size-4 shrink-0", active && "text-primary")} aria-hidden />
            <span className="truncate">{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
