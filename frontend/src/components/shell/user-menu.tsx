"use client";

import { LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { api } from "@/lib/api";

export function UserMenu({ username, tenantId, roles }: { username: string; tenantId: string; roles: string[] }) {
  const router = useRouter();
  const [signingOut, setSigningOut] = useState(false);

  async function signOut() {
    setSigningOut(true);
    await api("/api/session/logout", { method: "POST" }).catch(() => undefined);
    router.replace("/login");
    router.refresh();
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className="flex w-full min-w-0 items-center gap-3 rounded-md px-2 py-2 text-left hover:bg-sidebar-accent/60 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
        <Avatar className="size-8">
          <AvatarFallback className="bg-primary/15 text-xs text-primary">{username.slice(0, 2).toUpperCase()}</AvatarFallback>
        </Avatar>
        <span className="grid min-w-0 flex-1">
          <span className="truncate text-sm font-medium">{username}</span>
          <span className="truncate text-xs text-muted-foreground">{tenantId}</span>
        </span>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="grid gap-0.5">
          <span className="truncate">{username}</span>
          <span className="truncate text-xs font-normal text-muted-foreground">
            {roles.length ? roles.join("、") : "尚未被授予角色"}
          </span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={signOut} disabled={signingOut}>
          <LogOut className="size-4" aria-hidden />
          {signingOut ? "登出中…" : "登出"}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
