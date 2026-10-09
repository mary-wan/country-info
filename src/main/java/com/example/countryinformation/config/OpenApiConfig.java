package com.example.countryinformation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI countryInformationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Country Information API")
                .version("v1")
                .description("Resolves a country name through the CountryInfoService SOAP API, "
                        + "persists the result and exposes CRUD operations over it."));
    }
}
