"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { ApiError } from "@/lib/api";

export function Providers({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [client] = useState(() => {
    // session 過期或被撤銷：任何查詢或操作拿到 401 都導回登入頁，登入後回到原頁
    const onError = (error: unknown) => {
      if (error instanceof ApiError && error.status === 401) {
        router.replace(`/login?next=${encodeURIComponent(window.location.pathname)}`);
      }
    };
    return new QueryClient({
      queryCache: new QueryCache({ onError }),
      mutationCache: new MutationCache({ onError }),
      defaultOptions: {
        queries: {
          staleTime: 15_000,
          // 4xx 是請求本身的問題，重試也不會成功
          retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
          refetchOnWindowFocus: false,
        },
      },
    });
  });
  return (
    <QueryClientProvider client={client}>
      <TooltipProvider delayDuration={200}>{children}</TooltipProvider>
      {/* 放右下角：右側滑出的面板標題在上方，避免被提示蓋住 */}
      <Toaster theme="dark" position="bottom-right" richColors />
    </QueryClientProvider>
  );
}
