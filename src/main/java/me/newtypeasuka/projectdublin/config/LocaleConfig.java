package me.newtypeasuka.projectdublin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.Locale;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    public static final Locale DEFAULT_LOCALE = Locale.KOREAN;
    public static final Locale JAPANESE_LOCALE = Locale.JAPANESE;
    public static final String LANGUAGE_PARAMETER = "lang";
    public static final String LANGUAGE_COOKIE = "SITE_LANGUAGE";

    // 선택한 한국어·일본어를 쿠키에 저장하여 페이지를 이동해도 유지
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver localeResolver = new SupportedCookieLocaleResolver();
        localeResolver.setDefaultLocale(DEFAULT_LOCALE);
        localeResolver.setCookiePath("/");
        localeResolver.setCookieMaxAge(Duration.ofDays(365));
        localeResolver.setCookieHttpOnly(true);
        localeResolver.setCookieSameSite("Lax");
        localeResolver.setLanguageTagCompliant(true);
        localeResolver.setRejectInvalidCookies(true);
        return localeResolver;
    }

    // GET 요청의 lang 값이 ko 또는 ja일 때만 사이트 언어를 변경
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SupportedLocaleChangeInterceptor())
                .addPathPatterns("/**");
    }

    private static class SupportedCookieLocaleResolver extends CookieLocaleResolver {

        private SupportedCookieLocaleResolver() {
            super(LANGUAGE_COOKIE);
        }

        // 지원하지 않는 값으로 쿠키가 변조되어도 기본 한국어로 안전하게 복귀
        @Override
        protected Locale parseLocaleValue(String localeValue) {
            if (localeValue == null || localeValue.isBlank()) {
                return DEFAULT_LOCALE;
            }

            Locale parsedLocale = Locale.forLanguageTag(
                    localeValue.strip().replace('_', '-')
            );
            if (JAPANESE_LOCALE.getLanguage().equals(parsedLocale.getLanguage())) {
                return JAPANESE_LOCALE;
            }
            return DEFAULT_LOCALE;
        }
    }

    private static class SupportedLocaleChangeInterceptor extends LocaleChangeInterceptor {

        private SupportedLocaleChangeInterceptor() {
            setParamName(LANGUAGE_PARAMETER);
            setHttpMethods("GET");
            setIgnoreInvalidLocale(true);
        }

        @Override
        protected Locale parseLocaleValue(String localeValue) {
            String language = localeValue.strip().toLowerCase(Locale.ROOT);
            if (DEFAULT_LOCALE.getLanguage().equals(language)) {
                return DEFAULT_LOCALE;
            }
            if (JAPANESE_LOCALE.getLanguage().equals(language)) {
                return JAPANESE_LOCALE;
            }
            throw new IllegalArgumentException("unsupported locale");
        }
    }
}
