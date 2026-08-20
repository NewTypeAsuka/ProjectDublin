package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.domain.Article;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleLanguageServiceTest {

    private final ArticleLanguageService articleLanguageService =
            new ArticleLanguageService();

    @DisplayName("한글이 주로 사용된 게시글을 한국어로 판별한다")
    @Test
    void detectKorean() {
        assertThat(articleLanguageService.detect(
                "봄날의 기록",
                "오늘은 따뜻한 바람을 맞으며 산책한 이야기를 기록합니다."
        )).isEqualTo(Article.Language.KOREAN);
    }

    @DisplayName("가나와 한자가 함께 사용된 게시글을 일본어로 판별한다")
    @Test
    void detectJapanese() {
        assertThat(articleLanguageService.detect(
                "春の日の記録",
                "今日は暖かい風を感じながら、ゆっくり散歩しました。"
        )).isEqualTo(Article.Language.JAPANESE);
    }

    @DisplayName("한국어와 일본어가 아닌 명확한 문장은 기타 언어로 판별한다")
    @Test
    void detectOtherLanguage() {
        assertThat(articleLanguageService.detect(
                "A Spring Day",
                "This article was written entirely in English."
        )).isEqualTo(Article.Language.OTHER);
        assertThat(articleLanguageService.detect(
                "中文文章",
                "这是一个使用中文编写的文章内容"
        )).isEqualTo(Article.Language.OTHER);
    }

    @DisplayName("언어 증거가 부족한 게시글은 판별 불가로 처리한다")
    @Test
    void detectUndeterminedLanguage() {
        assertThat(articleLanguageService.detect("2026", "🚀 1234"))
                .isEqualTo(Article.Language.UNDETERMINED);
        assertThat(articleLanguageService.detect("東京", "京都"))
                .isEqualTo(Article.Language.UNDETERMINED);
    }

    @DisplayName("한국어와 일본어가 비슷하게 섞이면 판별 불가로 처리한다")
    @Test
    void detectUndeterminedMixedLanguage() {
        assertThat(articleLanguageService.detect("한국日本", "한글かな"))
                .isEqualTo(Article.Language.UNDETERMINED);
    }
}
