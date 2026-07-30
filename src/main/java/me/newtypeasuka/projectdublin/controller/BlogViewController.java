package me.newtypeasuka.projectdublin.controller;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.Article;
import me.newtypeasuka.projectdublin.dto.ArticleListViewResponse;
import me.newtypeasuka.projectdublin.dto.ArticleViewResponse;
import me.newtypeasuka.projectdublin.service.ArticleLikeService;
import me.newtypeasuka.projectdublin.service.BlogService;
import me.newtypeasuka.projectdublin.service.CommentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Controller
public class BlogViewController {

    private final BlogService blogService;
    private final ArticleLikeService articleLikeService;
    private final CommentService commentService;

    @GetMapping("/")
    public String root() {
        return "redirect:/articles";
    }

//    @GetMapping("/")
//    @ResponseBody // 뷰(html)를 찾지 않고 문자열 텍스트 자체를 200 OK로 반환하게 만듦
//    public String root() {
//        return "Health Check OK";
//    }

    @GetMapping("/articles")
    public String getArticles(Model model) {
        BlogService.ArticleFeed articleFeed = blogService.findInitialFeed();
        List<Article> articleEntities = articleFeed.articles();
        List<Long> articleIds = articleEntities.stream().map(Article::getId).toList();
        Map<Long, Long> likeCounts = articleLikeService.getLikeCounts(articleIds);
        Map<Long, Long> commentCounts = commentService.getCommentCounts(articleIds);
        List<ArticleListViewResponse> articles = articleEntities.stream()
                .map(article -> new ArticleListViewResponse(
                        article,
                        likeCounts.getOrDefault(article.getId(), 0L),
                        commentCounts.getOrDefault(article.getId(), 0L)
                ))
                .toList();
        model.addAttribute("articles", articles); // 블로그 글 리스트 저장
        model.addAttribute("nextCursor", articleFeed.nextCursor());
        model.addAttribute("hasNextArticles", articleFeed.hasNext());

        return "articleList"; // articleList.html 뷰 이름 반환
    }

    @GetMapping("/articles/{id}")
    public String getArticle(@PathVariable Long id, Model model, Principal principal) {
        Article article = blogService.findByIdAndIncreaseViewCount(id);
        long likeCount = articleLikeService.getLikeCount(id);
        long commentCount = commentService.getCommentCount(id);
        model.addAttribute(
                "article",
                new ArticleViewResponse(article, likeCount, commentCount)
        );
        String email = principal.getName();
        model.addAttribute("isAdmin", blogService.isAdmin(email));
        model.addAttribute(
                "canManageArticle",
                blogService.canManageArticle(article, email)
        );

        return "article"; // article.html 뷰 이름 반환
    }

    @GetMapping("/new-article")
    public String newArticle(@RequestParam(required = false) Long id,
                             Model model,
                             Principal principal) {
        if (id == null) { // id가 없으면 새 글 작성 페이지로 이동
            model.addAttribute("article", new ArticleViewResponse());
        } else {
            Article article = blogService.findByIdForManagement(id, principal.getName());
            model.addAttribute("article", new ArticleViewResponse(article));
        }

        return "newArticle"; // newArticle.html 뷰 이름 반환
    }

}
