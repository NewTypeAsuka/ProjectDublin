package me.newtypeasuka.projectdublin.repository;

import jakarta.persistence.LockModeType;
import me.newtypeasuka.projectdublin.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT comment FROM Comment comment "
            + "JOIN FETCH comment.commenter "
            + "LEFT JOIN FETCH comment.parent "
            + "WHERE comment.article.id = :articleId "
            + "ORDER BY comment.createdAt ASC, comment.id ASC")
    List<Comment> findAllByArticleIdOldestFirst(@Param("articleId") Long articleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT comment FROM Comment comment "
            + "JOIN FETCH comment.commenter "
            + "LEFT JOIN FETCH comment.parent "
            + "WHERE comment.id = :commentId "
            + "AND comment.article.id = :articleId")
    Optional<Comment> findByIdAndArticleIdForUpdate(
            @Param("commentId") Long commentId,
            @Param("articleId") Long articleId
    );

    boolean existsByParentId(Long parentId);
}
