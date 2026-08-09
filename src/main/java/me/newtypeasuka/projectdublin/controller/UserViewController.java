package me.newtypeasuka.projectdublin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.dto.UserDto.NicknameRequest;
import me.newtypeasuka.projectdublin.service.UserService;
import me.newtypeasuka.projectdublin.service.UserService.NicknameAlreadyExistsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@RequiredArgsConstructor
@Controller
public class UserViewController {

    private final UserService userService;

    // Google OAuth 로그인 화면 조회 API
    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) { // 로그인했는지 검증
            String email = getAttribute(authentication, "email");
            return userService.isRegistered(email)
                    ? "redirect:/articles"
                    : "redirect:/signup/nickname";
        }

        return "oauthLogin"; // 로그인이 안되었다면 oauthLogin.html 페이지로 이동
    }

    // 최초 Google 로그인 사용자의 닉네임 설정 화면 조회 API
    @GetMapping("/signup/nickname")
    public String nicknameSignup(Authentication authentication, Model model) {
        String email = getAttribute(authentication, "email");
        if (userService.isRegistered(email)) {
            return "redirect:/articles";
        }

        if (!model.containsAttribute("nicknameForm")) {
            model.addAttribute("nicknameForm", new NicknameRequest(""));
        }
        model.addAttribute("googleName", getAttribute(authentication, "name"));
        return "nicknameSignup";
    }

    // 최초 Google 로그인 사용자의 필수 닉네임 등록 API
    @PostMapping("/signup/nickname")
    public String completeNicknameSignup(
            @Valid @ModelAttribute("nicknameForm") NicknameRequest request,
            BindingResult bindingResult,
            Authentication authentication,
            Model model) {
        String email = getAttribute(authentication, "email");
        String name = getAttribute(authentication, "name");

        if (userService.isRegistered(email)) {
            return "redirect:/articles";
        }

        if (!bindingResult.hasErrors()
                && userService.isNicknameTaken(request.nickname())) {
            bindingResult.rejectValue(
                    "nickname",
                    "duplicate",
                    "이미 사용 중인 닉네임입니다."
            );
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("googleName", name);
            return "nicknameSignup";
        }

        try {
            userService.completeRegistration(
                    email,
                    name,
                    request.nickname()
            );
        } catch (NicknameAlreadyExistsException exception) {
            bindingResult.rejectValue(
                    "nickname",
                    "duplicate",
                    exception.getMessage()
            );
            model.addAttribute("googleName", name);
            return "nicknameSignup";
        } catch (DataIntegrityViolationException exception) {
            // 동시 가입 요청으로 고유 제약조건이 충돌한 경우 가입 여부를 다시 확인
            if (userService.isRegistered(email)) {
                return "redirect:/articles";
            }
            bindingResult.rejectValue(
                    "nickname",
                    "duplicate",
                    "이미 사용 중인 닉네임입니다."
            );
            model.addAttribute("googleName", name);
            return "nicknameSignup";
        }

        return "redirect:/articles";
    }

    private String getAttribute(Authentication authentication, String attributeName) {
        if (authentication != null
                && authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute(attributeName);
        }
        return null;
    }

}
