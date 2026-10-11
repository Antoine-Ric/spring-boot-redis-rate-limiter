package com.ricardi.ratelimiter.filter;

import com.ricardi.ratelimiter.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that rate-limits incoming API traffic.
 *
 * <p>Plain English: this sits in front of your controllers like a bouncer.
 * Every request to /api/** gets checked against the Redis token bucket.
 * Over the limit? The request gets a 429 ("slow down") response and never
 * reaches your controller code.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiter;

    public RateLimitFilter(RateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only rate-limit API traffic; leave everything else alone.
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String clientKey = clientKey(request);
        if (!rateLimiter.tryAcquire(clientKey)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "1");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate limit exceeded, try again shortly\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Identify the caller. Uses X-Forwarded-For when behind a proxy/load
     * balancer, otherwise the direct remote address. In production you would
     * more likely key by API key or authenticated user id.
     */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
