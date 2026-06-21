package com.zhangjiyan.template.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(AuthConstants.TOKEN_HEADER);
        if (header != null && header.startsWith(AuthConstants.TOKEN_PREFIX)) {
            String token = header.substring(AuthConstants.TOKEN_PREFIX.length());
            try {
                LoginUser loginUser = JwtUtils.parseAccessToken(token);
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                loginUser.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                loginUser.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
