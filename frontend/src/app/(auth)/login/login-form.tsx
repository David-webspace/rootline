"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api, ApiError } from "@/lib/api";

const schema = z.object({
  tenantCode: z.string().trim().min(2, "請輸入租戶代碼"),
  username: z.string().trim().min(1, "請輸入帳號"),
  password: z.string().min(1, "請輸入密碼"),
});

type Values = z.infer<typeof schema>;

/** 只接受站內相對路徑，避免 ?next=//evil.example 變成開放式重新導向。 */
function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/dashboard";
}

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { tenantCode: "", username: "", password: "" },
  });

  async function onSubmit(values: Values) {
    setError(null);
    try {
      await api("/api/session/login", { method: "POST", body: values });
      router.replace(safeNext(params.get("next")));
      router.refresh();
    } catch (e) {
      setError(e instanceof ApiError ? messageFor(e) : "登入失敗，請稍後再試");
    }
  }

  const { errors, isSubmitting } = form.formState;
  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" noValidate>
      <FormField id="tenantCode" label="租戶代碼" error={errors.tenantCode?.message}>
        <Input id="tenantCode" autoComplete="organization" placeholder="例如 northwind" autoFocus
          aria-invalid={!!errors.tenantCode} {...form.register("tenantCode")} />
      </FormField>
      <FormField id="username" label="帳號" error={errors.username?.message}>
        <Input id="username" autoComplete="username" aria-invalid={!!errors.username} {...form.register("username")} />
      </FormField>
      <FormField id="password" label="密碼" error={errors.password?.message}>
        <Input id="password" type="password" autoComplete="current-password" aria-invalid={!!errors.password}
          {...form.register("password")} />
      </FormField>

      {error && (
        <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm break-words text-destructive">
          {error}
        </p>
      )}

      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? "登入中…" : "登入"}
      </Button>
      <p className="text-center text-sm text-muted-foreground">
        還沒有帳號？<Link href="/register" className="text-primary underline-offset-4 hover:underline">註冊</Link>
      </p>
    </form>
  );
}

function messageFor(error: ApiError): string {
  switch (error.code) {
    case "INVALID_CREDENTIALS":
      return "租戶代碼、帳號或密碼不正確";
    case "ACCOUNT_LOCKED":
      return "帳號因多次登入失敗已被鎖定，請聯絡管理員解鎖";
    case "RATE_LIMITED":
      return "嘗試次數過多，請稍後再試";
    default:
      return error.message;
  }
}
