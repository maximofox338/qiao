package com.qiao.common.web;

import com.qiao.common.util.Texts;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client IP and user agent of the current HTTP request (empty outside a request).
 * The IP honours X-Forwarded-For only from trusted proxies ({@code server.forward-headers-strategy=native}).
 */
public record RequestMeta(String ip, String userAgent) {

    private static final RequestMeta NONE = new RequestMeta(null, null);

    public static RequestMeta current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return new RequestMeta(request.getRemoteAddr(), Texts.truncate(request.getHeader("User-Agent"), 512));
        }
        return NONE;
    }
}
