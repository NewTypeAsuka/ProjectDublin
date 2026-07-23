package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.config.S3StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Utilities;

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
                    DataSize.ofMegabytes(10)
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
