package com.railmind.auth;

import com.railmind.common.exceptions.AppExceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/** Resolves the authenticated principal (set by JwtAuthFilter) to a User entity/id. */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails details)) {
            throw new UnauthorizedException("Authentication required.");
        }
        return userRepository.findByEmail(details.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists."));
    }

    public Long resolveId(Authentication authentication) {
        return resolve(authentication).getId();
    }
}
