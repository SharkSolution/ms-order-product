package com.suresell.orders.infrastructure.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class WebConfig implements WebMvcConfigurer {
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(
            new String[] {
              "http://localhost:4200", "http://suresell-pos.s3-website-us-east-1.amazonaws.com"
            })
        .allowedMethods(new String[] {"GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"})
        .allowedHeaders(new String[] {"*"})
        .allowCredentials(true);
  }
}
