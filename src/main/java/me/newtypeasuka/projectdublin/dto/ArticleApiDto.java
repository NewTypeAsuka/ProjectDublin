package me.newtypeasuka.projectdublin.dto;

public final class ArticleApiDto {

    private ArticleApiDto() {
    }

    public record ImageUploadResponse(String url) {
    }

    public record LikeResponse(boolean liked, long likeCount) {
    }

    public record PinResponse(boolean pinned) {
    }
}
