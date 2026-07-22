package me.newtypeasuka.projectdublin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleContentSanitizerTest {

    private final ArticleContentSanitizer sanitizer = new ArticleContentSanitizer();

    @DisplayName("Summernote 서식과 YouTube 영상은 보존하고 위험한 코드는 제거한다")
    @Test
    void sanitizeSummernoteHtml() {
        String rawHtml = """
                <p onclick="alert('xss')">Hello <strong>Summernote</strong></p>
                <script>alert('xss')</script>
                <iframe src="//www.youtube.com/embed/video-id" allowfullscreen></iframe>
                <iframe src="https://example.com/unsafe"></iframe>
                """;

        String sanitizedHtml = sanitizer.sanitize(rawHtml);

        assertThat(sanitizedHtml)
                .contains("<strong>Summernote</strong>")
                .contains("https://www.youtube.com/embed/video-id")
                .doesNotContain("onclick")
                .doesNotContain("<script")
                .doesNotContain("https://example.com/unsafe");
    }

    @DisplayName("이미지 태그는 본문에서 제거한다")
    @Test
    void removeImage() {
        String sanitizedHtml = sanitizer.sanitize(
                "<p>본문<img src=\"https://example.com/image.png\" alt=\"image\"></p>"
        );

        assertThat(sanitizedHtml)
                .contains("본문")
                .doesNotContain("<img");
    }

    @DisplayName("내용이 없는 Summernote HTML은 거절한다")
    @Test
    void rejectEmptyContent() {
        assertThatThrownBy(() -> sanitizer.sanitize("<p><br></p>"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
