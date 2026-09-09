package com.example.wallet.transfer.demo.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthFilter implements Filter {

    public static final String USER_ID_ATTR = "userId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        String auth = httpReq.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String userId = auth.substring(7).trim();
            httpReq.setAttribute(USER_ID_ATTR, userId);
            MDC.put(USER_ID_ATTR, userId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_ATTR);
        }
    }
}
