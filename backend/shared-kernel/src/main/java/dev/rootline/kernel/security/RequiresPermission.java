package dev.rootline.kernel.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 第一軌（靜態）權限檢查：標在 controller 方法或類別上。
 * 沒有標註的 endpoint 會改查第二軌 {@link DynamicRulePort}。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {

    /** 需要的權限，例如 {@code IAM:USER_READ}。 */
    String value();

    /**
     * 若指定 path variable 名稱，且其值等於呼叫者本人（X-User-Id），則不需權限即可放行。
     * 用於「查自己的資料」這類 endpoint。
     */
    String selfPathVariable() default "";
}
