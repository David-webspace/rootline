import type { Metadata } from "next";
import { Suspense } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { LoginForm } from "./login-form";

export const metadata: Metadata = { title: "登入" };

export default function LoginPage() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">登入</CardTitle>
        <CardDescription>輸入你所屬的租戶與帳號</CardDescription>
      </CardHeader>
      <CardContent>
        {/* useSearchParams 讀的是請求資料，需在 Suspense 內 */}
        <Suspense>
          <LoginForm />
        </Suspense>
      </CardContent>
    </Card>
  );
}
