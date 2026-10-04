package com.example.minesweeper.admin;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    @Value("${admin.username}")
    private String adminUser;

    @Value("${admin.password-hash}")
    private String adminHash;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
        // Сравниваем логин как строку, пароль — через bcrypt.matches
        if (adminUser.equals(username) && encoder.matches(password, adminHash)) {
            session.setAttribute("admin", true);
            session.setAttribute("csrfToken", java.util.UUID.randomUUID().toString());
            return "redirect:/";
        }
        model.addAttribute("error", "Неверный логин или пароль");
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}