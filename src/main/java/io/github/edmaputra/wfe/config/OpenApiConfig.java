package io.github.edmaputra.wfe.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Workflow Engine API")
                        .description("API contract for the Workflow Engine service")
                        .version("v1")
                        .contact(new Contact().name("Workflow Team")));
    }
}
