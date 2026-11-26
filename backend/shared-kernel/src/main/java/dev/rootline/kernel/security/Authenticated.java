package dev.rootline.kernel.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 只要求「已登入」，不要求特定權限。用於本人操作自己資料的 endpoint（例如送出自己的實名資料）。
 * 動態 API 規則仍然會套用。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Authenticated {
}
