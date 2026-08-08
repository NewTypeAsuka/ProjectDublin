package me.newtypeasuka.projectdublin.config.oauth;

import me.newtypeasuka.projectdublin.domain.User;
import me.newtypeasuka.projectdublin.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2UserCustomServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    OAuth2UserCustomService oAuth2UserCustomService;

    @DisplayName("기존 Google 사용자는 name만 갱신하고 nickname을 유지한다")
    @Test
    void updateOnlyGoogleNameForRegisteredUser() {
        User registeredUser = User.builder()
                .email("member@example.com")
                .name("Old Google Name")
                .nickname("공개닉네임")
                .build();
        when(userRepository.findByEmail(registeredUser.getEmail()))
                .thenReturn(Optional.of(registeredUser));

        oAuth2UserCustomService.updateExistingUser(oAuth2User(
                registeredUser.getEmail(),
                "New Google Name"
        ));

        assertThat(registeredUser.getName()).isEqualTo("New Google Name");
        assertThat(registeredUser.getNickname()).isEqualTo("공개닉네임");
    }

    @DisplayName("신규 Google 사용자는 닉네임을 입력하기 전에 저장하지 않는다")
    @Test
    void doNotSaveNewUserBeforeNicknameSignup() {
        String email = "new-user@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        oAuth2UserCustomService.updateExistingUser(oAuth2User(
                email,
                "New Google User"
        ));

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private DefaultOAuth2User oAuth2User(String email, String name) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", email, "name", name),
                "email"
        );
    }

}
