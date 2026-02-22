package com.suresell.orders.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ms-order-product API",
                version = "v1",
                description = "API para gestión de órdenes, descuentos, cierres diarios y catálogo local.",
                contact = @Contact(name = "Suresell", email = "soporte@suresell.com"),
                license = @License(name = "Proprietary")
        ),
        servers = {
                @Server(url = "http://localhost:8081", description = "Local")
        }
)
public class OpenApiConfig {
}
