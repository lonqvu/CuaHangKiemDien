package com.shop.config;

import com.shop.auth.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtAuthFilter jwtFilter) throws Exception {
        http.csrf(c -> c.disable())
            .cors(c -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(a -> a
                // /error va luot dispatch loi khong kiem tra lai quyen: neu khong, 403/500 bi doi thanh 401 (mat dang nhap
                // o luot dispatch loi) va giao dien tuong het phien -> dang xuat nguoi dung
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/login", "/actuator/health", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll() // anh nen man dang nhap (chua dang nhap)
                .requestMatchers("/api/auth/change-password").permitAll() // nhu app cu: doi tu man dang nhap
                .requestMatchers("/api/auth/2fa/**").authenticated() // tu cai xac thuc 2 lop cho tai khoan dang dung
                .requestMatchers("/api/auth/logout-others").authenticated() // ca nhan vien: dang xuat moi may khac cua minh
                // Nhan vien: chi ban hang (xem san pham, tim/chon/tao khach, lam + in hoa don).
                // So no, sao ke, phieu thu, danh sach hoa don, huy hoa don: chi quan tri.
                .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/customers", "/api/customers/{id:\\d+}").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/customers").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/orders").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/cancel").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/orders/*/cancel-preview").hasRole("ADMIN")
                .requestMatchers("/api/orders/*/revise", "/api/orders/*/revise-preview", "/api/orders/*/revisions").hasRole("ADMIN") // sua hoa don da chot
                .requestMatchers("/api/orders/**").authenticated()
                // Con lai (sua san pham, cong no, bao cao, tai khoan...) la ADMIN
                .anyRequest().hasRole("ADMIN"))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsSource(@Value("${app.cors-origins}") String origins) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(origins.split(",")));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/api/**", c);
        return s;
    }
}
