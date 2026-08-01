package me.newtypeasuka.projectdublin.config;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.config.oauth.OAuth2UserCustomService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@RequiredArgsConstructor
@Configuration
public class WebOAuthSecurityConfig {

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
                        .defaultSuccessUrl("/articles", true) // google 로그인 성공 후 /articles 이동
                )
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

    // OAuth 인증 요청을 클라이언트 쿠키가 아닌 서버 HttpSession에 저장
    @Bean
    public HttpSessionOAuth2AuthorizationRequestRepository oAuth2AuthorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }

}
