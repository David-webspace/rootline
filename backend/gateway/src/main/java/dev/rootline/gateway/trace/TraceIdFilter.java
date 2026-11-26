package dev.rootline.gateway.trace;

import dev.rootline.gateway.security.TrustedHeadersRequest;
import dev.rootline.kernel.security.RootlineHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每個請求一個 trace id：沿用 client 帶來且格式正確的，否則產生新的。
 * 轉給下游、寫進回應 header，並放進 MDC 讓 Gateway 的 log 都帶著它。
 * 格式限制是為了防止 log injection（client 塞換行或超長字串進 log）。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(RootlineHeaders.TRACE_ID);
        String traceId = incoming != null && VALID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        response.setHeader(RootlineHeaders.TRACE_ID, traceId);
        MDC.put(MDC_KEY, traceId);
        try {
            chain.doFilter(new TrustedHeadersRequest(request, Map.of(RootlineHeaders.TRACE_ID, traceId)), response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
