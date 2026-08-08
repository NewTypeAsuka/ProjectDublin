package me.newtypeasuka.projectdublin.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public final class ArticleApiDto {

    private ArticleApiDto() {
    }

    public record ImageUploadResponse(String url) {
    }

    public record LikeResponse(boolean liked, long likeCount) {
    }

    public record PinResponse(boolean pinned) {
    }

    public record CommentContentRequest(
            @NotBlank String content
    ) {
    }

    public record CommentResponse(
            Long id,
            Long parentId,
            int depth,
            Long commenterId,
            String commenterName,
            // 댓글 작성자가 관리자인지 화면에 전달
            boolean commenterAdmin,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean deleted,
            boolean editable,
            boolean deletable,
            List<CommentResponse> replies
    ) {
    }
}
