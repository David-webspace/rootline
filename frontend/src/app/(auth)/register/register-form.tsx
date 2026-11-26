"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { api, ApiError } from "@/lib/api";

const schema = z
  .object({
    tenantCode: z.string().trim().min(2, "請輸入租戶代碼"),
    username: z
      .string()
      .trim()
      .regex(/^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$/, "3–64 字元，可用英數字與 . _ -"),
    email: z.email("Email 格式不正確"),
    password: z
      .string()
      .min(8, "至少 8 個字元")
      .refine((v) => new TextEncoder().encode(v).length <= 72, "最多 72 bytes"),
    confirm: z.string(),
  })
  .refine((v) => v.password === v.confirm, { path: ["confirm"], message: "兩次輸入的密碼不一致" });

type Values = z.infer<typeof schema>;

export function RegisterForm() {
  const [done, setDone] = useState<{ tenant: string; username: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { tenantCode: "", username: "", email: "", password: "", confirm: "" },
  });

  async function onSubmit(values: Values) {
    setError(null);
    // 「確認密碼」只在前端比對，不送到後端
    const payload = { tenantCode: values.tenantCode, username: values.username, email: values.email, password: values.password };
    try {
      const username = await api<string>("/api/session/register", { method: "POST", body: payload });
      setDone({ tenant: values.tenantCode, username });
    } catch (e) {
      setError(e instanceof ApiError ? messageFor(e) : "註冊失敗，請稍後再試");
    }
  }

  if (done) {
    return (
      <div className="grid gap-4 text-sm">
        <p className="break-words">
          已建立帳號 <span className="font-medium text-primary">{done.username}</span>（租戶 {done.tenant}）。
          新帳號預設沒有任何權限，請聯絡租戶管理員授權。
        </p>
        <Button asChild>
          <Link href="/login">前往登入</Link>
        </Button>
      </div>
    );
  }

  const { errors, isSubmitting } = form.formState;
  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="grid gap-4" noValidate>
      <FormField id="tenantCode" label="租戶代碼" error={errors.tenantCode?.message}>
        <Input id="tenantCode" autoFocus aria-invalid={!!errors.tenantCode} {...form.register("tenantCode")} />
      </FormField>
      <FormField id="username" label="帳號" error={errors.username?.message}>
        <Input id="username" autoComplete="username" aria-invalid={!!errors.username} {...form.register("username")} />
      </FormField>
      <FormField id="email" label="Email" error={errors.email?.message}>
        <Input id="email" type="email" autoComplete="email" aria-invalid={!!errors.email} {...form.register("email")} />
      </FormField>
      <FormField id="password" label="密碼" error={errors.password?.message} hint="至少 8 個字元">
        <Input id="password" type="password" autoComplete="new-password" aria-invalid={!!errors.password}
          {...form.register("password")} />
      </FormField>
      <FormField id="confirm" label="確認密碼" error={errors.confirm?.message}>
        <Input id="confirm" type="password" autoComplete="new-password" aria-invalid={!!errors.confirm}
          {...form.register("confirm")} />
      </FormField>

      {error && (
        <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm break-words text-destructive">
          {error}
        </p>
      )}
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? "建立中…" : "建立帳號"}
      </Button>
      <p className="text-center text-sm text-muted-foreground">
        已有帳號？<Link href="/login" className="text-primary underline-offset-4 hover:underline">登入</Link>
      </p>
    </form>
  );
}

function messageFor(error: ApiError): string {
  switch (error.code) {
    case "USERNAME_TAKEN":
      return "這個帳號已經有人使用";
    case "TENANT_NOT_AVAILABLE":
      return "找不到這個租戶，或租戶目前停用中";
    default:
      return error.message;
  }
}
