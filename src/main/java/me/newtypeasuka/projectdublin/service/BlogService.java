package me.newtypeasuka.projectdublin.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.AddArticleRequest;
import me.newtypeasuka.projectdublin.dto.UpdateArticleRequest;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor // lombok의 RequiredArgsConstructor 어노테이션으로 final이 붙거나 @NotNull 어노테이션이 붙은 생성자 자동 생성
@Service // 스프링의 Service 어노테이션으로 빈 등록
public class BlogService {

    public static final int DEFAULT_FEED_SIZE = 10;
    public static final int MAX_SEARCH_KEYWORD_LENGTH = 15;
    private static final int MAX_FEED_SIZE = 50;
    private static final String CURSOR_SEPARATOR = "|";

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;
    private final ArticleContentService articleContentService;
    private final ArticleImageService articleImageService;

    // 블로그 글 작성
    @Transactional
    public Article save(AddArticleRequest request, String userName) {
        User author = findUserByEmail(userName);
        String sanitizedTitle = sanitizeTitle(request.getTitle());
        String sanitizedContent = articleContentService.sanitize(request.getContent());
        String searchContent = articleContentService.extractSearchContent(sanitizedContent);
        Article article = blogRepository.save(
                request.toEntity(author, sanitizedTitle, sanitizedContent, searchContent)
        );
        articleImageService.synchronize(article, author);
        return article;
    }

    // 블로그 글 모두 조회
    public List<Article> findAll() {
        return blogRepository.findAllPinnedFirst();
    }

    // 최초 게시글 목록은 고정 글 전체와 일반 글 10개만 조회
    @Transactional
    public ArticleFeed findInitialFeed() {
        List<Article> pinnedArticles =
                blogRepository.findAllByPinnedTrueOrderByCreatedAtDescIdDesc();
        List<Article> normalCandidates =
                blogRepository.findAllByPinnedFalseOrderByCreatedAtDescIdDesc(
                        PageRequest.of(0, DEFAULT_FEED_SIZE + 1)
                );

        return createArticleFeed(pinnedArticles, normalCandidates, DEFAULT_FEED_SIZE);
    }

    // 검색어가 있으면 제목·본문 부분 일치 결과를 고정 글 우선으로 10개 조회
    public ArticleFeed findInitialFeed(String keyword) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return findInitialFeed();
        }

        List<Article> candidates = blogRepository.findSearchMatches(
                createSearchPattern(normalizedKeyword),
                PageRequest.of(0, DEFAULT_FEED_SIZE + 1)
        );
        return createSearchArticleFeed(candidates, DEFAULT_FEED_SIZE);
    }

    // 마지막으로 조회한 일반 글 이후의 게시글을 커서 기준으로 조회
    public ArticleFeed findArticleFeed(String cursor, int size) {
        int validatedSize = validateFeedSize(size);
        ArticleCursor articleCursor = decodeCursor(cursor);
        List<Article> normalCandidates = blogRepository.findUnpinnedAfterCursor(
                articleCursor.createdAt(),
                articleCursor.id(),
                PageRequest.of(0, validatedSize + 1)
        );

        return createArticleFeed(List.of(), normalCandidates, validatedSize);
    }

    // 검색 결과의 마지막 글 이후를 검색 전용 커서로 이어서 조회
    public ArticleFeed findArticleFeed(String cursor, int size, String keyword) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return findArticleFeed(cursor, size);
        }

        int validatedSize = validateFeedSize(size);
        ArticleSearchCursor articleCursor = decodeSearchCursor(cursor);
        List<Article> candidates = blogRepository.findSearchMatchesAfterCursor(
                createSearchPattern(normalizedKeyword),
                articleCursor.pinned(),
                articleCursor.createdAt(),
                articleCursor.id(),
                PageRequest.of(0, validatedSize + 1)
        );
        return createSearchArticleFeed(candidates, validatedSize);
    }

    // 검색어의 앞뒤 공백을 제거하고 백엔드에서도 15자 제한을 검증
    public String normalizeSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }

        String normalizedKeyword = keyword.strip();
        int length = normalizedKeyword.codePointCount(0, normalizedKeyword.length());
        if (length > MAX_SEARCH_KEYWORD_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검색어는 15자 이하로 입력해주세요"
            );
        }
        return normalizedKeyword;
    }

    // 블로그 글 단건 조회
    public Article findById(Long id) {
        return blogRepository.findById(id)
                .orElseThrow(() -> articleNotFound(id));
    }

    // 게시글 상세 조회 시 조회수를 1 증가시킨 후 최신 게시글을 반환
    @Transactional
    public Article findByIdAndIncreaseViewCount(Long id) {
        if (blogRepository.increaseViewCount(id) == 0) {
            throw articleNotFound(id);
        }

        return findById(id);
    }

    // 블로그 글 삭제
    @Transactional
    public void delete(long id, String email) {
        Article article = findById(id);

        authorizeArticleManager(article, findUserByEmail(email));
        articleImageService.removeAllForArticle(article.getId());
        blogRepository.delete(article);
    }

    // 블로그 글 수정
    @Transactional // 트랜잭션 처리를 위해 @Transactional 어노테이션 사용
    public Article update(long id, UpdateArticleRequest request, String email) {
        Article article = findById(id);
        User currentUser = findUserByEmail(email);

        authorizeArticleManager(article, currentUser);
        String sanitizedTitle = sanitizeTitle(request.getTitle());
        String sanitizedContent = articleContentService.sanitize(request.getContent());
        String searchContent = articleContentService.extractSearchContent(sanitizedContent);
        article.update(sanitizedTitle, sanitizedContent, searchContent);
        articleImageService.synchronize(article, currentUser);

        return article; // @Transactional 어노테이션을 사용하면, 엔티티를 조회한 후 변경된 값을 디비에 반환하지 않아도 JPA가 자동으로 1차 캐시를 통해 변경을 감지하고 이를 DB에 반영함
    }

    // 작성자 또는 관리자인 경우에만 게시글 수정 화면을 반환
    public Article findByIdForManagement(Long id, String email) {
        Article article = findById(id);
        authorizeArticleManager(article, findUserByEmail(email));
        return article;
    }

    @Transactional
    public Article updatePinned(long id, boolean pinned, String email) {
        User currentUser = findUserByEmail(email);
        if (!currentUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin role required");
        }

        Article article = findById(id);
        article.updatePinned(pinned);
        return article;
    }

    public boolean isAdmin(String email) {
        return findUserByEmail(email).isAdmin();
    }

    public boolean canManageArticle(Article article, String email) {
        return isArticleManager(article, findUserByEmail(email));
    }

    // 게시글 작성자 또는 관리자인지 백엔드에서 확인
    private void authorizeArticleManager(Article article, User currentUser) {
        if (!isArticleManager(article, currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private boolean isArticleManager(Article article, User currentUser) {
        return currentUser.isAdmin()
                || article.getAuthor().getId().equals(currentUser.getId());
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + email));
    }

    private ResponseStatusException articleNotFound(Long id) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "게시글을 찾을 수 없습니다: " + id
        );
    }

    private ArticleFeed createArticleFeed(List<Article> pinnedArticles,
                                          List<Article> normalCandidates,
                                          int size) {
        boolean hasNext = normalCandidates.size() > size;
        List<Article> normalArticles = List.copyOf(
                normalCandidates.subList(0, Math.min(size, normalCandidates.size()))
        );
        List<Article> articles = new ArrayList<>(
                pinnedArticles.size() + normalArticles.size()
        );
        articles.addAll(pinnedArticles);
        articles.addAll(normalArticles);

        String nextCursor = hasNext && !normalArticles.isEmpty()
                ? encodeCursor(normalArticles.get(normalArticles.size() - 1))
                : null;
        return new ArticleFeed(List.copyOf(articles), nextCursor, hasNext);
    }

    private ArticleFeed createSearchArticleFeed(List<Article> candidates, int size) {
        boolean hasNext = candidates.size() > size;
        List<Article> articles = List.copyOf(
                candidates.subList(0, Math.min(size, candidates.size()))
        );
        String nextCursor = hasNext && !articles.isEmpty()
                ? encodeSearchCursor(articles.get(articles.size() - 1))
                : null;
        return new ArticleFeed(articles, nextCursor, hasNext);
    }

    // LIKE의 와일드카드는 이스케이프하고 로마자는 소문자로 통일
    private String createSearchPattern(String keyword) {
        String escapedKeyword = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escapedKeyword + "%";
    }

    private int validateFeedSize(int size) {
        if (size < 1 || size > MAX_FEED_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "게시글은 한 번에 1개 이상 50개 이하로 조회할 수 있습니다"
            );
        }
        return size;
    }

    private String encodeCursor(Article article) {
        String value = article.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + CURSOR_SEPARATOR
                + article.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeSearchCursor(Article article) {
        String value = (article.isPinned() ? "1" : "0")
                + CURSOR_SEPARATOR
                + article.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + CURSOR_SEPARATOR
                + article.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private ArticleCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw invalidCursor();
        }

        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8
            );
            int separatorIndex = decoded.lastIndexOf(CURSOR_SEPARATOR);
            if (separatorIndex <= 0 || separatorIndex == decoded.length() - 1) {
                throw invalidCursor();
            }

            LocalDateTime createdAt = LocalDateTime.parse(
                    decoded.substring(0, separatorIndex),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );
            long id = Long.parseLong(decoded.substring(separatorIndex + 1));
            if (id <= 0) {
                throw invalidCursor();
            }
            return new ArticleCursor(createdAt, id);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalidCursor();
        }
    }

    private ArticleSearchCursor decodeSearchCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw invalidCursor();
        }

        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8
            );
            String[] values = decoded.split("\\|", -1);
            if (values.length != 3 || !(values[0].equals("1") || values[0].equals("0"))) {
                throw invalidCursor();
            }

            boolean pinned = values[0].equals("1");
            LocalDateTime createdAt = LocalDateTime.parse(
                    values[1],
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );
            long id = Long.parseLong(values[2]);
            if (id <= 0) {
                throw invalidCursor();
            }
            return new ArticleSearchCursor(pinned, createdAt, id);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw invalidCursor();
        }
    }

    private ResponseStatusException invalidCursor() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "유효하지 않은 게시글 커서입니다"
        );
    }

    // 게시글 제목을 정리하고 최대 40자 제한을 백엔드에서도 검사
    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해주세요");
        }

        String sanitizedTitle = title.strip();
        int length = sanitizedTitle.codePointCount(0, sanitizedTitle.length());
        if (length > Article.MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "제목은 40자 이하로 작성해주세요"
            );
        }
        return sanitizedTitle;
    }

    public record ArticleFeed(
            List<Article> articles,
            String nextCursor,
            boolean hasNext
    ) {
    }

    private record ArticleCursor(LocalDateTime createdAt, long id) {
    }

    private record ArticleSearchCursor(boolean pinned, LocalDateTime createdAt, long id) {
    }

}
