package me.newtypeasuka.projectdublin.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ArticleContentService {

    private static final Set<String> YOUTUBE_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com"
    );
    private static final Pattern FONT_SIZE_PATTERN = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)px$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile(
            "^#[0-9a-f]{3,8}$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RGB_COLOR_PATTERN = Pattern.compile(
            "^rgb\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*\\)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RGBA_COLOR_PATTERN = Pattern.compile(
            "^rgba\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,"
                    + "\\s*(?:0|1|0?\\.\\d+)\\s*\\)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> FONT_WEIGHT_VALUES = Set.of(
            "normal", "bold", "bolder", "500", "600", "700", "800", "900"
    );
    private static final Set<String> TEXT_DECORATION_VALUES = Set.of(
            "none", "underline", "line-through"
    );

    private final S3ObjectUrlService s3ObjectUrlService;

    private final Safelist safelist = Safelist.relaxed()
            .addAttributes("a", "target", "rel")
            .addAttributes("img", "src", "alt", "title", "width", "height", "class",
                    "loading", "decoding")
            .addProtocols("img", "src", "https")
            .addTags("iframe", "hr", "s")
            .addAttributes("iframe", "src", "width", "height", "title", "frameborder",
                    "allow", "allowfullscreen", "referrerpolicy")
            .addProtocols("iframe", "src", "https")
            .addAttributes("span", "style")
            .preserveRelativeLinks(true);

    public ArticleContentService(S3ObjectUrlService s3ObjectUrlService) {
        this.s3ObjectUrlService = s3ObjectUrlService;
    }

    public String sanitize(String rawHtml) {
        if (rawHtml == null) {
            throw invalidContent();
        }

        Document document = Jsoup.parseBodyFragment(rawHtml);
        document.select("p").forEach(this::removeTrailingMediaPlaceholder);
        document.select("a").forEach(this::sanitizeLinkTarget);
        document.select("font[color]").forEach(this::normalizeLegacyFontColor);
        document.select("span[style]").forEach(this::sanitizeInlineStyle);
        document.select("iframe").stream()
                .filter(iframe -> !isAllowedYoutubeEmbed(iframe))
                .forEach(Element::remove);
        document.select("img").stream()
                .filter(image -> !s3ObjectUrlService.isArticleImageUrl(image.attr("src")))
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

    // Summernote가 이미지·동영상 뒤의 커서 위치를 위해 붙인 빈 span과 br은 저장하지 않는다.
    private void removeTrailingMediaPlaceholder(Element paragraph) {
        List<Node> childNodes = new ArrayList<>(paragraph.childNodes());
        int lastMediaIndex = -1;
        for (int index = 0; index < childNodes.size(); index++) {
            if (isMediaNode(childNodes.get(index))) {
                lastMediaIndex = index;
            }
        }
        if (lastMediaIndex < 0 || lastMediaIndex == childNodes.size() - 1) {
            return;
        }

        List<Node> trailingNodes = childNodes.subList(lastMediaIndex + 1, childNodes.size());
        boolean hasPlaceholder = trailingNodes.stream().anyMatch(this::isMediaPlaceholder);
        boolean containsOnlyPlaceholder = trailingNodes.stream()
                .allMatch(node -> isBlankText(node) || isMediaPlaceholder(node));
        if (!hasPlaceholder || !containsOnlyPlaceholder) {
            return;
        }

        trailingNodes.forEach(Node::remove);
    }

    private boolean isMediaNode(Node node) {
        return node instanceof Element element
                && ("img".equals(element.normalName())
                || "iframe".equals(element.normalName()));
    }

    private boolean isMediaPlaceholder(Node node) {
        if (!(node instanceof Element element)) {
            return false;
        }
        if ("br".equals(element.normalName())) {
            return true;
        }
        if (!"span".equals(element.normalName())) {
            return false;
        }

        List<Node> spanNodes = element.childNodes();
        return spanNodes.stream().anyMatch(this::isMediaPlaceholder)
                && spanNodes.stream()
                .allMatch(child -> isBlankText(child) || isMediaPlaceholder(child));
    }

    private boolean isBlankText(Node node) {
        return node instanceof TextNode textNode && textNode.text().isBlank();
    }

    // 검색 시 HTML 태그나 속성이 일치하지 않도록 화면에 보이는 평문만 추출한다.
    public String extractSearchContent(String sanitizedHtml) {
        if (sanitizedHtml == null) {
            throw invalidContent();
        }
        return Jsoup.parseBodyFragment(sanitizedHtml).text();
    }

    // 브라우저가 Summernote 전경색을 font 태그로 생성하는 경우 안전한 span 스타일로 변환한다.
    private void normalizeLegacyFontColor(Element element) {
        String color = element.attr("color").trim();
        String normalizedColor = color.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        String existingStyle = element.attr("style").trim();

        element.tagName("span");
        element.removeAttr("color");
        element.removeAttr("face");
        element.removeAttr("size");

        if (!isAllowedColor(normalizedColor)) {
            return;
        }

        String colorStyle = "color: " + color;
        element.attr("style", existingStyle.isEmpty()
                ? colorStyle
                : colorStyle + "; " + existingStyle);
    }

    // Summernote 글자 도구가 생성하는 안전한 인라인 서식만 보존한다.
    private void sanitizeInlineStyle(Element element) {
        List<String> safeDeclarations = new ArrayList<>();
        for (String declaration : element.attr("style").split(";")) {
            String[] parts = declaration.split(":", 2);
            if (parts.length != 2) {
                continue;
            }

            String property = parts[0].trim().toLowerCase(Locale.ROOT);
            String value = parts[1].trim();
            if (isAllowedInlineStyle(property, value)) {
                safeDeclarations.add(property + ": " + value);
            }
        }

        if (safeDeclarations.isEmpty()) {
            element.removeAttr("style");
            return;
        }
        element.attr("style", String.join("; ", safeDeclarations));
    }

    private boolean isAllowedInlineStyle(String property, String value) {
        String normalizedValue = value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        return switch (property) {
            case "font-size" -> isAllowedFontSize(normalizedValue);
            case "color", "background-color" -> isAllowedColor(normalizedValue);
            case "font-weight" -> FONT_WEIGHT_VALUES.contains(normalizedValue);
            case "text-decoration", "text-decoration-line" ->
                    isAllowedTextDecoration(normalizedValue);
            default -> false;
        };
    }

    private boolean isAllowedFontSize(String value) {
        Matcher matcher = FONT_SIZE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return false;
        }

        double fontSize = Double.parseDouble(matcher.group(1));
        return fontSize >= 8 && fontSize <= 72;
    }

    private boolean isAllowedColor(String value) {
        return "transparent".equals(value)
                || HEX_COLOR_PATTERN.matcher(value).matches()
                || RGB_COLOR_PATTERN.matcher(value).matches()
                || RGBA_COLOR_PATTERN.matcher(value).matches();
    }

    private boolean isAllowedTextDecoration(String value) {
        String[] values = value.split(" ");
        if (values.length == 0) {
            return false;
        }
        for (String decoration : values) {
            if (!TEXT_DECORATION_VALUES.contains(decoration)) {
                return false;
            }
        }
        return true;
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
