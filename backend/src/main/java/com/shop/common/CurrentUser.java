package com.shop.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Ten nguoi dang thao tac (ho ten neu co), de ghi vao so no: ai thu, ai sua no. */
public final class CurrentUser {
    private CurrentUser() {}

    public static String name() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return null;
        return a.getDetails() instanceof String s ? s : a.getName();
    }
}
