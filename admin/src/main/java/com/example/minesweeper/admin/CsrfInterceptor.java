package com.example.minesweeper.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Проверяет CSRF-токен на всех POST/PUT/DELETE-запросах.
 *
 * Логин — единственное исключение: на момент его отправки в сессии
 * ещё нет токена (он создаётся при успешной аутентификации).
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req,
                             HttpServletResponse resp,
                             Object handler) throws Exception {
        String method = req.getMethod();
        // Проверяем только изменяющие методы
        if (!"POST".equalsIgnoreCase(method)
                && !"PUT".equalsIgnoreCase(method)
                && !"DELETE".equalsIgnoreCase(method)) {
            return true;
        }

        String path = req.getRequestURI();
        // Логин — до появления токена в сессии
        if (path.equals("/login")) {
            return true;
        }

        HttpSession session = req.getSession(false);
        if (session == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Нет сессии");
            return false;
        }
        String expected = (String) session.getAttribute("csrfToken");
        String actual = req.getParameter("_csrf");

        if (expected == null || !expected.equals(actual)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Недействительный CSRF-токен");
            return false;
        }
        return true;
    }
}