import { Network } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { MobileNavLoader, SidebarNav, UserMenuLoader } from "@/components/shell/session-parts";
import { Skeleton } from "@/components/ui/skeleton";

function Brand() {
  return (
    <Link href="/dashboard" className="flex min-w-0 items-center gap-2.5 px-2">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary/15 text-primary">
        <Network className="size-4" aria-hidden />
      </span>
      <span className="truncate font-semibold tracking-tight">rootline</span>
    </Link>
  );
}

function NavSkeleton() {
  return (
    <div className="grid gap-2 px-1" aria-hidden>
      {Array.from({ length: 4 }).map((_, i) => (
        <Skeleton key={i} className="h-8 w-full" />
      ))}
    </div>
  );
}

export default function DashboardLayout({ children }: LayoutProps<"/dashboard">) {
  return (
    <div className="flex min-h-svh">
      <aside className="sticky top-0 hidden h-svh w-64 shrink-0 flex-col gap-6 border-r border-sidebar-border bg-sidebar p-4 lg:flex">
        <Brand />
        <div className="min-h-0 flex-1 overflow-y-auto">
          <Suspense fallback={<NavSkeleton />}>
            <SidebarNav />
          </Suspense>
        </div>
        <Suspense fallback={<Skeleton className="h-12 w-full" />}>
          <UserMenuLoader />
        </Suspense>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-10 flex items-center gap-2 border-b border-border bg-background/80 px-4 py-2 backdrop-blur lg:hidden">
          <Suspense fallback={<Skeleton className="size-9" />}>
            <MobileNavLoader />
          </Suspense>
          <Brand />
        </header>
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-10 lg:py-8">{children}</main>
      </div>
    </div>
  );
}
