package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.config.S3Config.S3StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Utilities;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleContentSanitizerTest {

    private final S3ObjectUrlResolver urlResolver = new S3ObjectUrlResolver(
            new S3StorageProperties(
                    "projectdublin-test-images",
                    "ap-northeast-2",
                    "",
                    "",
                    "articles",
                    "",
                    DataSize.ofMegabytes(10),
                    Duration.ofHours(24)
            ),
            S3Utilities.builder().region(Region.AP_NORTHEAST_2).build()
    );
    private final ArticleContentSummarizer sanitizer =
            new ArticleContentSummarizer(urlResolver);

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

    @DisplayName("Summernote 구분선은 보존하고 위험한 속성은 제거한다")
    @Test
    void preserveHorizontalRule() {
        String sanitizedHtml = sanitizer.sanitize("""
                <p>구분선 위</p>
                <hr class="note-hr" onclick="alert('xss')">
                <p>구분선 아래</p>
                """);

        assertThat(sanitizedHtml)
                .contains("<hr>")
                .doesNotContain("class=")
                .doesNotContain("onclick");
    }

    @DisplayName("Summernote 글자 도구의 서식은 보존하고 위험한 CSS는 제거한다")
    @Test
    void preserveSummernoteFontToolbarStyles() {
        String sanitizedHtml = sanitizer.sanitize("""
                <p>
                    <b>굵게</b>
                    <strike>취소선</strike>
                    <u>밑줄</u>
                    <span style="font-size: 24px; color: rgb(255, 0, 0);
                        background-color: #ffff00; position: fixed;
                        background-image: url(javascript:alert('xss'))">크기와 색상</span>
                    <span style="font-weight: bold;
                        text-decoration: underline line-through">인라인 서식</span>
                </p>
                """);

        assertThat(sanitizedHtml)
                .contains("<b>굵게</b>")
                .contains("<strike>취소선</strike>")
                .contains("<u>밑줄</u>")
                .contains("font-size: 24px")
                .contains("color: rgb(255, 0, 0)")
                .contains("background-color: #ffff00")
                .contains("font-weight: bold")
                .contains("text-decoration: underline line-through")
                .doesNotContain("position")
                .doesNotContain("background-image")
                .doesNotContain("javascript");
    }

    @DisplayName("새 창 링크는 안전 속성과 함께 보존하고 다른 target은 제거한다")
    @Test
    void preserveSafeNewWindowLink() {
        String sanitizedHtml = sanitizer.sanitize("""
                <a href="https://example.com/new" target="_blank">새 창 링크</a>
                <a href="https://example.com/same" target="_self" rel="opener">현재 창 링크</a>
                """);

        assertThat(sanitizedHtml)
                .contains("href=\"https://example.com/new\"")
                .contains("target=\"_blank\"")
                .contains("rel=\"noopener noreferrer\"")
                .doesNotContain("target=\"_self\"")
                .doesNotContain("rel=\"opener\"");
    }

    @DisplayName("S3 게시글 이미지는 보존하고 외부 이미지는 제거한다")
    @Test
    void allowOnlyManagedS3Image() {
        String managedImageUrl = urlResolver.resolve("articles/2026/07/image.png");
        String sanitizedHtml = sanitizer.sanitize(
                """
                <img src="%s" alt="S3 image" loading="lazy" onerror="alert('xss')">
                <img src="https://example.com/image.png" alt="external image">
                <img src="https://projectdublin-test-images.s3.ap-northeast-2.amazonaws.com/articles-evil/image.png">
                """.formatted(managedImageUrl)
        );

        assertThat(sanitizedHtml)
                .contains(managedImageUrl)
                .contains("alt=\"S3 image\"")
                .doesNotContain("https://example.com/image.png")
                .doesNotContain("articles-evil")
                .doesNotContain("onerror");
    }

    @DisplayName("내용이 없는 Summernote HTML은 거절한다")
    @Test
    void rejectEmptyContent() {
        assertThatThrownBy(() -> sanitizer.sanitize("<p><br></p>"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
