/**
 * 與後端 PermissionEvaluator 相同的規則，只用來決定畫面上要不要顯示按鈕；
 * 真正的授權永遠在後端。
 */
export function hasPermission(granted: readonly string[], required: string): boolean {
  if (granted.includes("*:ADMIN_ALL") || granted.includes(required)) return true;
  const separator = required.indexOf(":");
  return separator > 0 && granted.includes(`${required.slice(0, separator)}:ADMIN_ALL`);
}

export function hasAny(granted: readonly string[], required: readonly string[]): boolean {
  return required.some((permission) => hasPermission(granted, permission));
}
