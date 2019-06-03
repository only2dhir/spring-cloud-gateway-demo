package com.devglan.gatewayservice;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(r -> r.path("/api/v1/first/**")
                        .filters(f -> f.rewritePath("/api/v1/first/(?<remains>.*)", "/${remains}")
                                .addRequestHeader("X-first-Header", "first-service-header")
                                .hystrix(c -> c.setName("hystrix")
                                        .setFallbackUri("forward:/fallback/first")))
                        .uri("lb://FIRST-SERVICE/")
                        .id("first-service"))

                .route(r -> r.path("/api/v1/second/**")
                        .filters(f -> f.rewritePath("/api/v1/second/(?<remains>.*)", "/${remains}")
                                .hystrix(c -> c.setName("hystrix")
                                        .setFallbackUri("forward:/fallback/second")))
                        .uri("lb://SECOND-SERVICE/")
                        .id("second-service"))
                .build();
    }

}
