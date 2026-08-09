package me.newtypeasuka.projectdublin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import me.newtypeasuka.projectdublin.domain.User;

public final class UserDto {

    private UserDto() {
    }

    public record NicknameRequest(
            @NotBlank(message = "닉네임을 입력해주세요.")
            @Size(
                    min = User.MIN_NICKNAME_LENGTH,
                    max = User.MAX_NICKNAME_LENGTH,
                    message = "닉네임은 3자 이상 12자 이하로 입력해주세요."
            )
            @Pattern(
                    regexp = "^[^\\r\\n\\t]+$",
                    message = "닉네임에는 줄바꿈을 사용할 수 없습니다."
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
