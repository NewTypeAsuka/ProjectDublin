package me.newtypeasuka.projectdublin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.dto.UserDto.ErrorResponse;
import me.newtypeasuka.projectdublin.dto.UserDto.NicknameRequest;
import me.newtypeasuka.projectdublin.dto.UserDto.NicknameResponse;
import me.newtypeasuka.projectdublin.dto.UserDto.ProfileResponse;
import me.newtypeasuka.projectdublin.service.UserService;
import me.newtypeasuka.projectdublin.service.UserService.InvalidNicknameException;
import me.newtypeasuka.projectdublin.service.UserService.NicknameAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users/me")
public class UserApiController {

    private final UserService userService;

    // 로그인 사용자의 마이페이지 정보 조회 API
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(Principal principal) {
        return ResponseEntity.ok(userService.getProfile(principal.getName()));
    }

    // 로그인 사용자의 공개 닉네임 변경 API
    @PutMapping("/nickname")
    public ResponseEntity<NicknameResponse> updateNickname(
            @Valid @RequestBody NicknameRequest request,
            Principal principal
    ) {
        return ResponseEntity.ok(
                userService.updateNickname(principal.getName(), request.nickname())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldError() == null
                ? "닉네임을 확인해주세요."
                : exception.getBindingResult().getFieldError().getDefaultMessage();
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(InvalidNicknameException.class)
    public ResponseEntity<ErrorResponse> handleInvalidNickname(
            InvalidNicknameException exception
    ) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(NicknameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateNickname(
            NicknameAlreadyExistsException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage()));
    }
}
