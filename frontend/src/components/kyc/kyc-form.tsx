"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import type { ComponentProps } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { COUNTRIES, DOCUMENT_TYPES, type KycView } from "@/lib/platform-types";

/** 台灣身分證檢查碼（與後端 IdentityDocument 同一套規則），送出前先擋掉打錯的號碼。 */
export function isValidTaiwanId(id: string): boolean {
  if (!/^[A-Z][1289][0-9]{8}$/.test(id)) return false;
  const letters = "ABCDEFGHJKLMNPQRSTUVXYWZIO";
  const code = letters.indexOf(id[0]) + 10;
  const digits = [Math.floor(code / 10), code % 10, ...id.slice(1).split("").map(Number)];
  const weights = [1, 9, 8, 7, 6, 5, 4, 3, 2, 1, 1];
  return digits.reduce((sum, d, i) => sum + d * weights[i], 0) % 10 === 0;
}

const name = (label: string) => z.string().trim().min(1, `請輸入${label}`).max(50, "最多 50 字");

const schema = z
  .object({
    lastName: name("姓"),
    firstName: name("名"),
    dateOfBirth: z.string().min(1, "請選擇出生日期").refine((v) => v < new Date().toISOString().slice(0, 10), "出生日期必須早於今天"),
    documentType: z.enum(["NATIONAL_ID", "PASSPORT", "RESIDENT_PERMIT"]),
    countryCode: z.string().regex(/^[A-Z]{2}$/, "請選擇發證國家"),
    idNumber: z.string().trim().toUpperCase().min(1, "請輸入證件號碼"),
    address: z.object({
      countryCode: z.string().regex(/^[A-Z]{2}$/, "請選擇國家"),
      state: z.string().trim().max(200, "最多 200 字"),
      city: z.string().trim().min(1, "請輸入城市").max(200, "最多 200 字"),
      postalCode: z.string().trim().max(20, "最多 20 字"),
      detail: z.string().trim().min(1, "請輸入地址").max(200, "最多 200 字"),
    }),
  })
  .superRefine((v, ctx) => {
    const ok =
      v.documentType === "PASSPORT" ? /^[A-Z0-9]{6,9}$/.test(v.idNumber)
        : v.documentType === "NATIONAL_ID" && v.countryCode === "TW" ? isValidTaiwanId(v.idNumber)
          : /^[A-Z0-9-]{4,20}$/.test(v.idNumber);
    if (!ok) {
      ctx.addIssue({
        code: "custom",
        path: ["idNumber"],
        message: v.documentType === "NATIONAL_ID" && v.countryCode === "TW" ? "身分證字號格式或檢查碼不正確" : "證件號碼格式不正確",
      });
    }
  });

export type KycFormValues = z.infer<typeof schema>;

/** 被駁回後重新送審時，沿用上次的資料（證件號碼只存遮罩，必須重新輸入）。 */
function defaults(previous: KycView | undefined): KycFormValues {
  return {
    lastName: previous?.lastName ?? "",
    firstName: previous?.firstName ?? "",
    dateOfBirth: previous?.dateOfBirth ?? "",
    documentType: previous?.documentType ?? "NATIONAL_ID",
    countryCode: previous?.countryCode ?? "TW",
    idNumber: "",
    address: {
      countryCode: previous?.address?.countryCode ?? "TW",
      state: previous?.address?.state ?? "",
      city: previous?.address?.city ?? "",
      postalCode: previous?.address?.postalCode ?? "",
      detail: previous?.address?.detail ?? "",
    },
  };
}

export function KycForm({ previous, pending, onSubmit }: {
  previous?: KycView;
  pending: boolean;
  onSubmit: (values: KycFormValues) => Promise<unknown>;
}) {
  const form = useForm<KycFormValues>({ resolver: zodResolver(schema), defaultValues: defaults(previous) });
  const { errors } = form.formState;
  const text = (id: string, label: string, error: string | undefined, field: Parameters<typeof form.register>[0],
    props: ComponentProps<typeof Input> = {}) => (
    <FormField id={id} label={label} error={error}>
      <Input id={id} aria-invalid={!!error} {...props} {...form.register(field)} />
    </FormField>
  );
  const select = (id: string, label: string, error: string | undefined,
    field: "documentType" | "countryCode" | "address.countryCode", options: { value: string; label: string }[]) => (
    <FormField id={id} label={label} error={error}>
      <Controller
        control={form.control}
        name={field}
        render={({ field: f }) => (
          <Select value={f.value} onValueChange={f.onChange}>
            <SelectTrigger id={id} className="w-full min-w-0" aria-invalid={!!error}><SelectValue /></SelectTrigger>
            <SelectContent>
              {options.map((o) => <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>)}
            </SelectContent>
          </Select>
        )}
      />
    </FormField>
  );

  return (
    <form
      noValidate
      className="grid gap-6"
      onSubmit={form.handleSubmit((v) => onSubmit(v).catch(() => undefined))}
    >
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium">個人資料</legend>
        {text("lastName", "姓", errors.lastName?.message, "lastName", { autoComplete: "family-name" })}
        {text("firstName", "名", errors.firstName?.message, "firstName", { autoComplete: "given-name" })}
        {text("dateOfBirth", "出生日期", errors.dateOfBirth?.message, "dateOfBirth", { type: "date", autoComplete: "bday" })}
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium">證件</legend>
        {select("documentType", "證件類型", errors.documentType?.message, "documentType", DOCUMENT_TYPES)}
        {select("countryCode", "發證國家", errors.countryCode?.message, "countryCode", COUNTRIES)}
        <div className="sm:col-span-2">
          {text("idNumber", "證件號碼", errors.idNumber?.message, "idNumber", {
            autoComplete: "off",
            spellCheck: false,
            className: "font-mono uppercase",
          })}
        </div>
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="mb-3 text-sm font-medium">居住地址</legend>
        {select("addressCountry", "國家", errors.address?.countryCode?.message, "address.countryCode", COUNTRIES)}
        {text("addressState", "縣市／州（選填）", errors.address?.state?.message, "address.state", { autoComplete: "address-level1" })}
        {text("addressCity", "城市／鄉鎮區", errors.address?.city?.message, "address.city", { autoComplete: "address-level2" })}
        {text("addressPostal", "郵遞區號（選填）", errors.address?.postalCode?.message, "address.postalCode", { autoComplete: "postal-code" })}
        <div className="sm:col-span-2">
          {text("addressDetail", "地址", errors.address?.detail?.message, "address.detail", { autoComplete: "street-address" })}
        </div>
      </fieldset>

      <p className="text-xs break-words text-muted-foreground">
        證件號碼只以遮罩形式保存與顯示；另存一份 HMAC 指紋（無法還原號碼），用來偵測同一份證件被多個帳號使用。
      </p>
      <div>
        <Button type="submit" disabled={pending}>{pending ? "送出中…" : "送出審核"}</Button>
      </div>
    </form>
  );
}
