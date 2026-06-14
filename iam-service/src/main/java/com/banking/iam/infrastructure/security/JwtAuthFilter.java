package com.banking.iam.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the Bearer token from Authorization header, validates it, and populates
 * the SecurityContext. Downstream code calls SecurityContextHolder.getContext()
 * to get the authenticated principal.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        extractToken(request).ifPresent(token ->
            jwtTokenService.validate(token).ifPresent(claims -> {
                var userId = jwtTokenService.extractUserId(claims);
                var roles  = jwtTokenService.extractRoles(claims);
                List<SimpleGrantedAuthority> authorities = roles == null ? List.of() :
                        roles.stream()
                             .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                             .toList();

                var auth = new UsernamePasswordAuthenticationToken(
                        userId.toString(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            })
        );

        chain.doFilter(request, response);
    }

    private java.util.Optional<String> extractToken(HttpServletRequest request) {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return java.util.Optional.of(header.substring(7));
        }
        return java.util.Optional.empty();
    }
}
