package me.newtypeasuka.projectdublin.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.domain.ArticleLike;
import me.newtypeasuka.projectdublin.domain.ArticleLikeId;
import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.dto.ArticleLikeResponse;
import me.newtypeasuka.projectdublin.repository.ArticleLikeRepository;
import me.newtypeasuka.projectdublin.repository.BlogRepository;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class ArticleLikeService {

    private final ArticleLikeRepository articleLikeRepository;
    private final BlogRepository blogRepository;
    private final UserRepository userRepository;

    @Transactional
    public ArticleLikeResponse getStatus(long articleId, String email) {
        User user = findUser(email);
        findArticle(articleId);

        return createResponse(user.getId(), articleId);
    }

    public long getLikeCount(long articleId) {
        return articleLikeRepository.countByIdArticleId(articleId);
    }

    @Transactional
    public ArticleLikeResponse like(long articleId, String email) {
        User user = findUser(email);
        Article article = findArticle(articleId);
        ArticleLikeId id = new ArticleLikeId(user.getId(), articleId);

        if (!articleLikeRepository.existsById(id)) {
            articleLikeRepository.save(new ArticleLike(user, article));
        }

        return createResponse(user.getId(), articleId);
    }

    @Transactional
    public ArticleLikeResponse unlike(long articleId, String email) {
        User user = findUser(email);
        findArticle(articleId);
        ArticleLikeId id = new ArticleLikeId(user.getId(), articleId);

        if (articleLikeRepository.existsById(id)) {
            articleLikeRepository.deleteById(id);
        }

        return new ArticleLikeResponse(false, articleLikeRepository.countByIdArticleId(articleId));
    }

    private ArticleLikeResponse createResponse(Long userId, Long articleId) {
        ArticleLikeId id = new ArticleLikeId(userId, articleId);
        boolean liked = articleLikeRepository.existsById(id);
        long likeCount = articleLikeRepository.countByIdArticleId(articleId);
        return new ArticleLikeResponse(liked, likeCount);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Article findArticle(long articleId) {
        return blogRepository.findById(articleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
