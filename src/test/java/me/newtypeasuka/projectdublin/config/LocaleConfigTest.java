package me.newtypeasuka.projectdublin.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LocaleConfigTest {

    private final LocaleResolver localeResolver = new LocaleConfig().localeResolver();

    @DisplayName("언어 쿠키가 없으면 한국어를 기본 언어로 사용한다")
    @Test
    void resolveDefaultLocale() {
        assertThat(localeResolver.resolveLocale(new MockHttpServletRequest()))
                .isEqualTo(Locale.KOREAN);
    }

    @DisplayName("일본어 쿠키는 일본어로 해석한다")
    @Test
    void resolveJapaneseLocale() {
        assertThat(localeResolver.resolveLocale(requestWithLanguageCookie("ja")))
                .isEqualTo(Locale.JAPANESE);
    }

    @DisplayName("지원하지 않는 언어 쿠키는 한국어로 안전하게 복귀한다")
    @Test
    void rejectUnsupportedLocaleCookie() {
        assertThat(localeResolver.resolveLocale(requestWithLanguageCookie("en-US")))
                .isEqualTo(Locale.KOREAN);
    }

    private MockHttpServletRequest requestWithLanguageCookie(String language) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(LocaleConfig.LANGUAGE_COOKIE, language));
        return request;
    }
}
