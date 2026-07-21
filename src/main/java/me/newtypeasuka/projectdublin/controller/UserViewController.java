package me.newtypeasuka.projectdublin.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserViewController {

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) { // 로그인했는지 검증
            return "redirect:/articles"; // 로그인했다면 /articles 페이지로 리다이렉트
        }

        return "oauthLogin"; // 로그인이 안되었다면 oauthLogin.html 페이지로 이동
    }

}
