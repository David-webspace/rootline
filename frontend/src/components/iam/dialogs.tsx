"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { FormField } from "@/components/form-field";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

export type FieldSpec = {
  name: string;
  label: string;
  type?: "text" | "email" | "password" | "select";
  /** type 為 select 時的選項。 */
  options?: Option[];
  /** 選填欄位不做「必填」檢查。 */
  optional?: boolean;
  /** 編輯時不可改的欄位（例如識別碼）。 */
  readOnly?: boolean;
  placeholder?: string;
  hint?: string;
  /** 回傳錯誤訊息，或 undefined 表示通過。只做基本檢查，完整規則由後端負責。 */
  validate?: (value: string) => string | undefined;
};

/**
 * 通用表單對話框。欄位少（2–3 個）且規則單純的建立 / 改名表單共用這一個，
 * 不必每個都寫一份 react-hook-form 設定。
 */
export function FormDialog({
  open,
  onOpenChange,
  title,
  description,
  fields,
  initial,
  submitLabel,
  pending,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  fields: FieldSpec[];
  initial?: Record<string, string>;
  submitLabel: string;
  pending: boolean;
  onSubmit: (values: Record<string, string>) => Promise<unknown>;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[calc(100%-2rem)] sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        {/* key 讓每次打開都是乾淨的初始值 */}
        {open && (
          <FormBody
            fields={fields}
            initial={initial}
            submitLabel={submitLabel}
            pending={pending}
            onCancel={() => onOpenChange(false)}
            onSubmit={async (values) => {
              await onSubmit(values);
              onOpenChange(false);
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function FormBody({
  fields,
  initial,
  submitLabel,
  pending,
  onCancel,
  onSubmit,
}: {
  fields: FieldSpec[];
  initial?: Record<string, string>;
  submitLabel: string;
  pending: boolean;
  onCancel: () => void;
  onSubmit: (values: Record<string, string>) => Promise<void>;
}) {
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(fields.map((f) => [f.name, initial?.[f.name] ?? ""])),
  );
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found = Object.fromEntries(
      fields.map((f) => [
        f.name,
        values[f.name].trim() === "" ? (f.optional ? undefined : "此欄位必填") : f.validate?.(values[f.name]),
      ]),
    );
    setErrors(found);
    if (Object.values(found).some(Boolean)) return;
    // 失敗時對話框保持開啟，讓使用者修正（錯誤訊息由 mutation 的 toast 顯示）
    await onSubmit(values).catch(() => undefined);
  }

  return (
    <form onSubmit={submit} className="grid gap-4" noValidate>
      {fields.map((field, index) => (
        <FormField key={field.name} id={`f-${field.name}`} label={field.label} error={errors[field.name]} hint={field.hint}>
          {field.type === "select" ? (
            <Select value={values[field.name]} onValueChange={(value) => setValues((v) => ({ ...v, [field.name]: value }))}>
              <SelectTrigger id={`f-${field.name}`} className="w-full min-w-0" aria-invalid={!!errors[field.name]}>
                <SelectValue placeholder={field.placeholder ?? "請選擇"} />
              </SelectTrigger>
              <SelectContent>
                {(field.options ?? []).map((o) => (
                  <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : (
            <Input
              id={`f-${field.name}`}
              type={field.type ?? "text"}
              placeholder={field.placeholder}
              autoFocus={index === 0 && !field.readOnly}
              readOnly={field.readOnly}
              autoComplete={field.type === "password" ? "new-password" : "off"}
              aria-invalid={!!errors[field.name]}
              className={field.readOnly ? "bg-muted text-muted-foreground" : undefined}
              value={values[field.name]}
              onChange={(e) => setValues((v) => ({ ...v, [field.name]: e.target.value }))}
            />
          )}
        </FormField>
      ))}
      <DialogFooter>
        <Button type="button" variant="ghost" onClick={onCancel}>取消</Button>
        <Button type="submit" disabled={pending}>{pending ? "處理中…" : submitLabel}</Button>
      </DialogFooter>
    </form>
  );
}

export type Option = { value: string; label: string; detail?: string };

/** 從清單挑一個（指派角色、授予權限、加入成員）。已具備的項目由呼叫端先排除。 */
export function SelectDialog({
  open,
  onOpenChange,
  title,
  description,
  label,
  options,
  emptyText,
  submitLabel,
  pending,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: ReactNode;
  label: string;
  options: Option[];
  emptyText: string;
  submitLabel: string;
  pending: boolean;
  onSubmit: (value: string) => Promise<unknown>;
}) {
  const [value, setValue] = useState("");
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) setValue("");
        onOpenChange(next);
      }}
    >
      <DialogContent className="max-w-[calc(100%-2rem)] sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        {options.length === 0 ? (
          <p className="text-sm text-muted-foreground">{emptyText}</p>
        ) : (
          <FormField id="select-dialog" label={label}>
            <Select value={value} onValueChange={setValue}>
              <SelectTrigger id="select-dialog" className="w-full min-w-0">
                <SelectValue placeholder="請選擇" />
              </SelectTrigger>
              <SelectContent>
                {options.map((o) => (
                  <SelectItem key={o.value} value={o.value}>
                    <span className="truncate">{o.label}</span>
                    {o.detail && <span className="ml-2 truncate text-xs text-muted-foreground">{o.detail}</span>}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>
        )}
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>取消</Button>
          <Button
            disabled={!value || pending}
            onClick={async () => {
              await onSubmit(value)
                .then(() => {
                  setValue("");
                  onOpenChange(false);
                })
                .catch(() => undefined);
            }}
          >
            {pending ? "處理中…" : submitLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** 破壞性或不易察覺的動作先確認（停用帳號、移除角色等）。 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  destructive,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  destructive?: boolean;
  onConfirm: () => void;
}) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="max-w-[calc(100%-2rem)] sm:max-w-md">
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription className="break-words">{description}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction
            onClick={onConfirm}
            className={destructive ? "bg-destructive text-white hover:bg-destructive/90" : undefined}
          >
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
