package io.radius.common.config;

import io.radius.common.security.CurrentUserArgumentResolver;
import io.radius.common.security.JwtAuthFilter;
import io.radius.common.security.JwtService;
import io.radius.common.security.RestAuthenticationEntryPoint;
import io.radius.common.web.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Wires the servlet-side pieces every REST service shares. A service that needs
 * something different declares its own {@code SecurityFilterChain} and this one
 * steps aside.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpServletRequest.class, SecurityFilterChain.class})
@EnableConfigurationProperties(SecurityPathProperties.class)
public class RadiusWebAutoConfiguration {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwt) {
        return new JwtAuthFilter(jwt);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public WebMvcConfigurer radiusArgumentResolvers() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
        return new RestAuthenticationEntryPoint();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain radiusFilterChain(HttpSecurity http, JwtAuthFilter jwtFilter,
                                                 SecurityPathProperties paths,
                                                 RestAuthenticationEntryPoint entryPoint) throws Exception {
        String[] publicPaths = paths.getPublicPaths().toArray(String[]::new);
        String[] publicGetPaths = paths.getPublicGetPaths().toArray(String[]::new);
        http
                .csrf(csrf -> csrf.disable())                 // stateless bearer tokens, no cookies
                .cors(cors -> {})                             // gateway owns CORS; this is for direct dev calls
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> {
                    reg.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    if (publicPaths.length > 0) reg.requestMatchers(publicPaths).permitAll();
                    // GET-only, so browsing is open while writing on the same path is not.
                    if (publicGetPaths.length > 0) {
                        reg.requestMatchers(HttpMethod.GET, publicGetPaths).permitAll();
                    }
                    reg.anyRequest().authenticated();
                })
                // 401 for "who are you", 403 for "not yours" — the clients' refresh
                // logic depends on telling those two apart.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(entryPoint))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
