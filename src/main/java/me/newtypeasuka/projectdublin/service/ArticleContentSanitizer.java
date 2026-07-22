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
public class ArticleContentSanitizer {

    private static final Set<String> YOUTUBE_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com"
    );

    private final Safelist safelist = Safelist.relaxed()
            .removeTags("img")
            .addTags("iframe")
            .addAttributes("iframe", "src", "width", "height", "title", "frameborder",
                    "allow", "allowfullscreen", "referrerpolicy")
            .addProtocols("iframe", "src", "https")
            .preserveRelativeLinks(true);

    public String sanitize(String rawHtml) {
        if (rawHtml == null) {
            throw invalidContent();
        }

        Document document = Jsoup.parseBodyFragment(rawHtml);
        document.select("iframe").stream()
                .filter(iframe -> !isAllowedYoutubeEmbed(iframe))
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
        boolean hasMedia = !sanitizedDocument.select("iframe").isEmpty();
        if (!hasText && !hasMedia) {
            throw invalidContent();
        }

        return sanitizedHtml;
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
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "본문을 입력해주세요.");
    }
}
