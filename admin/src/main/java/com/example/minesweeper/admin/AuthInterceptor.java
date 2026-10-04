package com.example.minesweeper.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req,
                             HttpServletResponse resp,
                             Object handler) throws Exception {
        String path = req.getRequestURI();

        // Пропускаем логин и статику
        if (path.equals("/login") || path.startsWith("/css/")
                || path.startsWith("/js/") || path.startsWith("/error")) {
            return true;
        }

        HttpSession session = req.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return true;
        }

        resp.sendRedirect("/login");
        return false;
    }
}