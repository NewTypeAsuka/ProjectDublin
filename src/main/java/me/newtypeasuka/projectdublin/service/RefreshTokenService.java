package me.newtypeasuka.projectdublin.service;

import lombok.RequiredArgsConstructor;
import me.newtypeasuka.projectdublin.domain.RefreshToken;
import me.newtypeasuka.projectdublin.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    public RefreshToken findByRefreshToken(String refreshToken) {
        return refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Unexpected token"));
    }

}