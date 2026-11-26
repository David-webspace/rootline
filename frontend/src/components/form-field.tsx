import type { ReactNode } from "react";
import { Label } from "@/components/ui/label";

/** 表單欄位：標籤、輸入元件、錯誤訊息。錯誤以 aria-describedby 連到輸入框，讀螢幕軟體也聽得到。 */
export function FormField({
  id,
  label,
  error,
  hint,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="grid min-w-0 gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? (
        <p id={`${id}-error`} role="alert" className="text-sm break-words text-destructive">
          {error}
        </p>
      ) : hint ? (
        <p className="text-xs break-words text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}
