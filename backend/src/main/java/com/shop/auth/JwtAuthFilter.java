package com.shop.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AppUserRepository users;

    public JwtAuthFilter(JwtService jwt, AppUserRepository users) { this.jwt = jwt; this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            try {
                Claims c = jwt.parse(h.substring(7));
                // Doc lai tai khoan moi request: khoa tai khoan / doi vai tro co hieu luc ngay, khong doi token het han (8 gio)
                users.findByUsername(c.getSubject()).filter(AppUser::isActive).ifPresent(u -> {
                    var auth = new UsernamePasswordAuthenticationToken(
                            u.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())));
                    auth.setDetails(u.getFullName());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            } catch (Exception ignored) {
                // token sai/het han -> coi nhu chua dang nhap, Security tra 401
            }
        }
        chain.doFilter(req, res);
    }
}
