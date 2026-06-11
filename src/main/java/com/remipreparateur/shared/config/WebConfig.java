package com.remipreparateur.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Le CORS est desormais gere par Spring Security
 * (voir {@code SecurityConfig.corsConfigurationSource()}).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
