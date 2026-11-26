import { Network } from "lucide-react";

export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <main className="relative flex min-h-svh items-center justify-center overflow-hidden px-4 py-10">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(60%_50%_at_50%_0%,oklch(0.74_0.16_162/0.14),transparent)]"
      />
      <div className="relative w-full max-w-sm">
        <div className="mb-8 flex items-center justify-center gap-2.5">
          <span className="flex size-9 items-center justify-center rounded-lg bg-primary/15 text-primary">
            <Network className="size-5" aria-hidden />
          </span>
          <span className="text-xl font-semibold tracking-tight">rootline</span>
        </div>
        {children}
      </div>
    </main>
  );
}
