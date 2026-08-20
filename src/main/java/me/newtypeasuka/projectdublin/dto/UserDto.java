package me.newtypeasuka.projectdublin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.newtypeasuka.projectdublin.domain.User;

public final class UserDto {

    private UserDto() {
    }

    public record NicknameRequest(
            @NotBlank(message = "{validation.nickname.required}")
            @Size(
                    min = User.MIN_NICKNAME_LENGTH,
                    max = User.MAX_NICKNAME_LENGTH,
                    message = "{validation.nickname.length}"
            )
            @Pattern(
                    regexp = "^[^\\r\\n\\t]+$",
                    message = "{validation.nickname.linebreak}"
            )
            String nickname
    ) {

        public NicknameRequest {
            nickname = nickname == null ? null : nickname.strip();
        }
    }

    public record ProfileResponse(
            Long userId,
            String email,
            String nickname,
            long articleCount,
            long commentCount
    ) {
    }

    public record NicknameResponse(Long userId, String nickname) {
    }

    public record ErrorResponse(String message) {
    }
}
