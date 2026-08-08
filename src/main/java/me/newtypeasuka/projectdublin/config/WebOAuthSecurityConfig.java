package me.newtypeasuka.projectdublin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.config.oauth.OAuth2UserCustomService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Configuration
public class WebOAuthSecurityConfig {

    private static final String REGISTRATION_COMPLETE_SESSION_ATTRIBUTE =
            WebOAuthSecurityConfig.class.getName() + ".REGISTRATION_COMPLETE";

    private final OAuth2UserCustomService oAuth2UserCustomService;

    // 정적 리소스는 인증 없이 제공할 수 있도록 보안 필터에서 제외
    @Bean
    public WebSecurityCustomizer configure() {
        return (web) -> web.ignoring()
                .requestMatchers( // filterChain 보안 필터 체인 제외
                        new AntPathRequestMatcher("/img/**"), // 브라우저는 기본적으로 /src/main/resources/static/을 정적 리소스 루트로 이용, 그러므로 /img/**
                        new AntPathRequestMatcher("/css/**"),
                        new AntPathRequestMatcher("/js/**")
                );
    }

    // 보안 필터 체인 설정: 보안 필터가 Controller보다 먼저 각 요청을 검사
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(Customizer.withDefaults()) // 세션 기반 CSRF 토큰으로 상태 변경 요청을 검증
                .httpBasic(AbstractHttpConfigurer::disable) // HTTP Basic 인증 비활성화(OAuth2 인증이라 꺼둠)
                .formLogin(AbstractHttpConfigurer::disable) // 이메일/비밀번호 기반 로그인 폼 비활성화
                .logout(logout -> logout // 로그아웃 설정
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST")) // CSRF 토큰이 포함된 POST 요청만 로그아웃 처리
                        .logoutSuccessUrl("/login") // 로그아웃 성공 시 이동할 경로 설정
                        .invalidateHttpSession(true) /// 로그아웃 시 세션 무효화
                        .deleteCookies("JSESSIONID")) // 로그아웃 시 JSESSIONID 쿠키 삭제
                .authorizeRequests(auth -> auth
                        .requestMatchers( // 로그인 없이 접근 가능한 경로 설정
                                new AntPathRequestMatcher("/login"),
                                new AntPathRequestMatcher("/oauth2/**"),
                                new AntPathRequestMatcher("/login/oauth2/**"),
                                new AntPathRequestMatcher("/health") // 헬스 체크용 엔드포인트는 인증 없이 접근 가능하도록 설정
                        ).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated() // 로그인하지 않으면 접근 불가(401 반환)
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2 // OAuth2 로그인 전체 흐름 설정
                        .loginPage("/login") // 인증되지 않은 사용자가 이동할 로그인 페이지
                        .authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint.authorizationRequestRepository(oAuth2AuthorizationRequestRepository())) // Google 리다이렉트 전 요청을 HttpSession에 저장하고 콜백 시 복원
                        .userInfoEndpoint(userInfoEndpoint -> userInfoEndpoint.userService(oAuth2UserCustomService)) // google 사용자 정보 조회 후 내부 User 저장 및 갱신
                        .successHandler(oAuth2AuthenticationSuccessHandler()) // 신규 사용자는 닉네임 설정, 기존 사용자는 게시글 목록으로 이동
                )
                .addFilterAfter(nicknameRegistrationFilter(), OAuth2LoginAuthenticationFilter.class) // 닉네임 설정 전 다른 화면과 API 접근 차단
                .exceptionHandling(exceptionHandling -> exceptionHandling // 인증 실패 상황 시 응답 흐름 설정
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**") // /api/** 경로에 인증되지 않은 사용자가 접근하면 401 상태 반환
                        )
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"), // /api/** 경로 외 인증되지 않은 사용자가 접근하면 /login 페이지로 이동
                                AnyRequestMatcher.INSTANCE
                        ))
                .build();
    }

    // Google 인증 후 내부 가입 여부에 따라 닉네임 설정 화면 또는 게시글 목록으로 이동
    @Bean
    public AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            String email = getEmail(authentication);
            boolean registered = oAuth2UserCustomService.isRegistered(email);

            if (registered) {
                request.getSession().setAttribute(
                        REGISTRATION_COMPLETE_SESSION_ATTRIBUTE,
                        true
                );
            } else {
                request.getSession().removeAttribute(
                        REGISTRATION_COMPLETE_SESSION_ATTRIBUTE
                );
            }

            String targetPath = registered ? "/articles" : "/signup/nickname";
            response.sendRedirect(request.getContextPath() + targetPath);
        };
    }

    // 최초 로그인 사용자가 닉네임 설정을 건너뛰고 서비스 기능을 사용하는 것을 방지
    @Bean
    public OncePerRequestFilter nicknameRegistrationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {
                Authentication authentication =
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext()
                                .getAuthentication();

                if (!isAuthenticatedOAuthUser(authentication)
                        || isAllowedBeforeRegistration(request)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Object registrationComplete = request.getSession(false) == null
                        ? null
                        : request.getSession(false).getAttribute(
                                REGISTRATION_COMPLETE_SESSION_ATTRIBUTE
                        );
                if (Boolean.TRUE.equals(registrationComplete)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                if (oAuth2UserCustomService.isRegistered(getEmail(authentication))) {
                    request.getSession().setAttribute(
                            REGISTRATION_COMPLETE_SESSION_ATTRIBUTE,
                            true
                    );
                    filterChain.doFilter(request, response);
                    return;
                }

                if (request.getRequestURI().startsWith(
                        request.getContextPath() + "/api/"
                )) {
                    response.sendError(
                            HttpServletResponse.SC_FORBIDDEN,
                            "닉네임 설정이 필요합니다."
                    );
                    return;
                }

                response.sendRedirect(
                        request.getContextPath() + "/signup/nickname"
                );
            }
        };
    }

    private boolean isAuthenticatedOAuthUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof OAuth2User;
    }

    private boolean isAllowedBeforeRegistration(HttpServletRequest request) {
        String path = request.getRequestURI().substring(
                request.getContextPath().length()
        );
        return path.equals("/signup/nickname")
                || path.equals("/login")
                || path.equals("/logout")
                || path.equals("/health")
                || path.equals("/error")
                || path.equals("/favicon.ico")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/");
    }

    private String getEmail(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        }
        return null;
    }

    // OAuth 인증 요청을 클라이언트 쿠키가 아닌 서버 HttpSession에 저장
    @Bean
    public HttpSessionOAuth2AuthorizationRequestRepository oAuth2AuthorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }

}
