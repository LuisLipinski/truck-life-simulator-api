package com.luislipinski.trucklife.identity.config;

import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.shared.error.ApiSecurityProblemWriter;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration(proxyBeanMethods = false)
public class IdentityWebConfiguration {

    @Bean
    FilterRegistrationBean<RefreshCookieOriginFilter> sessionRequestSecurityFilter(
            IdentityWebProperties webProperties,
            CsrfTokenService csrfTokenService,
            ApiSecurityProblemWriter problemWriter
    ) {
        FilterRegistrationBean<RefreshCookieOriginFilter> registration =
                new FilterRegistrationBean<>(new RefreshCookieOriginFilter(
                        webProperties,
                        csrfTokenService,
                        problemWriter
                ));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CorsFilter> identityCorsFilter(
            IdentityWebProperties properties
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.AUTHORIZATION,
                CsrfTokenService.HEADER_NAME,
                "X-Correlation-ID"
        ));
        configuration.setExposedHeaders(List.of("X-Correlation-ID", HttpHeaders.RETRY_AFTER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(
                new CorsFilter(source)
        );
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AccessTokenAuthenticationFilter> accessTokenAuthenticationFilter(
            JwtAccessTokenIssuer accessTokenIssuer,
            UserRepository userRepository,
            ApiSecurityProblemWriter problemWriter
    ) {
        FilterRegistrationBean<AccessTokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new AccessTokenAuthenticationFilter(
                        accessTokenIssuer,
                        userRepository,
                        problemWriter
                ));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registration;
    }
}
