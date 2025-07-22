package com.example.winauth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import waffle.servlet.WindowsPrincipal;

@Controller
public class AuthController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/home")
    public String homePage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.getPrincipal() instanceof WindowsPrincipal) {
            WindowsPrincipal principal = (WindowsPrincipal) auth.getPrincipal();
            model.addAttribute("username", principal.getName());
            model.addAttribute("domain", principal.getName());
            model.addAttribute("authenticated", true);
            
            // ユーザーのグループ情報
            model.addAttribute("groups", principal.getGroups());
        } else {
            model.addAttribute("authenticated", false);
        }
        
        return "home";
    }

    @GetMapping("/secure")
    public String securePage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.getPrincipal() instanceof WindowsPrincipal) {
            WindowsPrincipal principal = (WindowsPrincipal) auth.getPrincipal();
            model.addAttribute("username", principal.getName());
            model.addAttribute("domain", principal.getName());
            model.addAttribute("sid", principal.getSid());
            model.addAttribute("groups", principal.getGroups());
            
            return "success";
        }
        
        return "redirect:/login-error";
    }

    @GetMapping("/login-error")
    public String loginError(Model model) {
        model.addAttribute("loginError", true);
        model.addAttribute("errorMessage", "Windows認証に失敗しました。ドメインアカウントでログインしているか確認してください。");
        return "error";
    }
}