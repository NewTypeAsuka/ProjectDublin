package me.newtypeasuka.projectdublin.config;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.config.oauth.OAuth2AuthorizationRequestBasedOnCookieRepository;
import me.newtypeasuka.projectdublin.config.oauth.OAuth2SuccessHandler;
import me.newtypeasuka.projectdublin.config.oauth.OAuth2UserCustomService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

import static org.springframework.boot.autoconfigure.security.servlet.PathRequest.toH2Console;

@RequiredArgsConstructor
@Configuration
public class WebOAuthSecurityConfig {

    private final OAuth2UserCustomService oAuth2UserCustomService;

    // 스프링 시큐리티 기능 비활성화
    @Bean
    public WebSecurityCustomizer configure() {
        return (web) -> web.ignoring()
                .requestMatchers(toH2Console()) // filterChain 보안 필터 체인 제외
                .requestMatchers( // filterChain 보안 필터 체인 제외
                        new AntPathRequestMatcher("/img/**"),
                        new AntPathRequestMatcher("/css/**"),
                        new AntPathRequestMatcher("/js/**")
                );
    }

    // 보안 필터 체인 설정: 보안 필터가 Controller보다 먼저 각 요청을 검사
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // csrf 설정 비활성화 -> 나중에 켜야 할 듯?
                .httpBasic(AbstractHttpConfigurer::disable) // HTTP Basic 인증 비활성화(OAuth2 인증이라 꺼둠)
                .formLogin(AbstractHttpConfigurer::disable) // 이메일/비밀번호 기반 로그인 폼 비활성화
                .logout(logout -> logout // 로그아웃 설정
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout")) // /logout 요청시 로그아웃 처리 -> 현재 csrf 비활성화 상태라 GET /logout 동작 가능
                        .logoutSuccessUrl("/login") // 로그아웃 성공 시 이동할 경로 설정
                        .invalidateHttpSession(true) /// 로그아웃 시 세션 무효화
                        .deleteCookies("JSESSIONID")) // 로그아웃 시 JSESSIONID 쿠키 삭제
                .authorizeRequests(auth -> auth
                        .requestMatchers( // 로그인 없이 접근 가능한 경로 설정
                                new AntPathRequestMatcher("/login"),
                                new AntPathRequestMatcher("/oauth2/**"),
                                new AntPathRequestMatcher("/login/oauth2/**")
                        ).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated() // 로그인하지 않으면 접근 불가(401 반환)
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2 // OAuth2 로그인 전체 흐름 설정
                        .loginPage("/login") // 인증되지 않은 사용자가 이동할 로그인 페이지
                        .authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint.authorizationRequestRepository(oAuth2AuthorizationRequestBasedOnCookieRepository())) // Google 리다이렉트 전 요청 저장 및 콜백 시 복원
                        .userInfoEndpoint(userInfoEndpoint -> userInfoEndpoint.userService(oAuth2UserCustomService)) // google 사용자 정보 조회 후 내부 User 저장 및 갱신
                        .successHandler(oAuth2SuccessHandler()) // google 로그인 성공 후 임시 쿠키 정리 및 /articles 이동
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

    @Bean
    public OAuth2SuccessHandler oAuth2SuccessHandler() {
        return new OAuth2SuccessHandler(oAuth2AuthorizationRequestBasedOnCookieRepository());
    }

    @Bean
    public OAuth2AuthorizationRequestBasedOnCookieRepository oAuth2AuthorizationRequestBasedOnCookieRepository() {
        return new OAuth2AuthorizationRequestBasedOnCookieRepository();
    }

}
