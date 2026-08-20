package me.newtypeasuka.projectdublin.service;

import me.newtypeasuka.projectdublin.domain.Article;
import org.springframework.stereotype.Service;

import java.text.Normalizer;

@Service
public class ArticleLanguageService {

    private static final int MIN_LANGUAGE_SPECIFIC_CHARACTERS = 2;
    private static final int MIN_OTHER_ALPHABET_CHARACTERS = 3;
    private static final int MIN_HAN_ONLY_CHARACTERS_FOR_OTHER = 8;
    private static final double DOMINANT_LANGUAGE_RATIO = 0.7;

    // 정제된 제목과 평문 본문의 문자 분포로 게시글의 주 언어를 판별
    public Article.Language detect(String title, String plainContent) {
        ScriptCounts counts = countScripts(normalize(title, plainContent));
        int supportedLanguageCharacters = counts.hangul() + counts.japaneseKana();

        if (supportedLanguageCharacters >= MIN_LANGUAGE_SPECIFIC_CHARACTERS) {
            double koreanRatio = (double) counts.hangul() / supportedLanguageCharacters;
            if (koreanRatio >= DOMINANT_LANGUAGE_RATIO) {
                return Article.Language.KOREAN;
            }
            if (koreanRatio <= 1.0 - DOMINANT_LANGUAGE_RATIO) {
                return Article.Language.JAPANESE;
            }
            return Article.Language.UNDETERMINED;
        }

        // 한글이나 가나가 너무 적으면 인용문일 수 있으므로 다른 언어로 단정하지 않는다.
        if (supportedLanguageCharacters > 0) {
            return Article.Language.UNDETERMINED;
        }
        if (counts.otherAlphabet() >= MIN_OTHER_ALPHABET_CHARACTERS) {
            return Article.Language.OTHER;
        }
        if (counts.han() >= MIN_HAN_ONLY_CHARACTERS_FOR_OTHER) {
            return Article.Language.OTHER;
        }
        return Article.Language.UNDETERMINED;
    }

    private String normalize(String title, String plainContent) {
        String detectionText = (title == null ? "" : title)
                + '\n'
                + (plainContent == null ? "" : plainContent);
        return Normalizer.normalize(detectionText, Normalizer.Form.NFKC);
    }

    private ScriptCounts countScripts(String text) {
        int hangul = 0;
        int japaneseKana = 0;
        int han = 0;
        int otherAlphabet = 0;

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HANGUL) {
                hangul++;
            } else if (script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA) {
                japaneseKana++;
            } else if (script == Character.UnicodeScript.HAN) {
                han++;
            } else if (Character.isLetter(codePoint)) {
                otherAlphabet++;
            }
        }

        return new ScriptCounts(hangul, japaneseKana, han, otherAlphabet);
    }

    private record ScriptCounts(
            int hangul,
            int japaneseKana,
            int han,
            int otherAlphabet
    ) {
    }
}
