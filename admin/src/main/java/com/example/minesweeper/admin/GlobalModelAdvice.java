package com.example.minesweeper.admin;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("csrfToken")
    public String csrfToken(HttpSession session) {
        Object t = session.getAttribute("csrfToken");
        return t != null ? t.toString() : "";
    }
}