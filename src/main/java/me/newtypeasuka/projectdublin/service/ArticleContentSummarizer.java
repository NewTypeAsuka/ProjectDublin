package me.newtypeasuka.projectdublin.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

@Component
public class ArticleContentSummarizer {

    private static final Set<String> YOUTUBE_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com"
    );

    private final S3ObjectUrlResolver s3ObjectUrlResolver;

    private final Safelist safelist = Safelist.relaxed()
            .addAttributes("a", "target", "rel")
            .addAttributes("img", "src", "alt", "title", "width", "height", "class",
                    "loading", "decoding")
            .addProtocols("img", "src", "https")
            .addTags("iframe")
            .addAttributes("iframe", "src", "width", "height", "title", "frameborder",
                    "allow", "allowfullscreen", "referrerpolicy")
            .addProtocols("iframe", "src", "https")
            .preserveRelativeLinks(true);

    public ArticleContentSummarizer(S3ObjectUrlResolver s3ObjectUrlResolver) {
        this.s3ObjectUrlResolver = s3ObjectUrlResolver;
    }

    public String sanitize(String rawHtml) {
        if (rawHtml == null) {
            throw invalidContent();
        }

        Document document = Jsoup.parseBodyFragment(rawHtml);
        document.select("a").forEach(this::sanitizeLinkTarget);
        document.select("iframe").stream()
                .filter(iframe -> !isAllowedYoutubeEmbed(iframe))
                .forEach(Element::remove);
        document.select("img").stream()
                .filter(image -> !s3ObjectUrlResolver.isArticleImageUrl(image.attr("src")))
                .forEach(Element::remove);

        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String sanitizedHtml = Jsoup.clean(
                document.body().html(),
                "https://projectdublin.local",
                safelist,
                outputSettings
        ).trim();

        Document sanitizedDocument = Jsoup.parseBodyFragment(sanitizedHtml);
        boolean hasText = !sanitizedDocument.text().isBlank();
        boolean hasMedia = !sanitizedDocument.select("iframe, img").isEmpty();
        if (!hasText && !hasMedia) {
            throw invalidContent();
        }

        return sanitizedHtml;
    }

    // 새 창 링크만 target을 보존하고 탭 가로채기 방지 속성을 강제한다.
    private void sanitizeLinkTarget(Element link) {
        boolean opensNewWindow = "_blank".equalsIgnoreCase(link.attr("target"));
        link.removeAttr("target");
        link.removeAttr("rel");

        if (opensNewWindow) {
            link.attr("target", "_blank");
            link.attr("rel", "noopener noreferrer");
        }
    }

    private boolean isAllowedYoutubeEmbed(Element iframe) {
        try {
            String source = iframe.attr("src");
            if (source.startsWith("//")) {
                source = "https:" + source;
                iframe.attr("src", source);
            }

            URI uri = new URI(source);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && YOUTUBE_HOSTS.contains(host.toLowerCase())
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/embed/");
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private ResponseStatusException invalidContent() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "본문을 입력해주세요");
    }
}
