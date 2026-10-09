package com.example.countryinformation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

@Configuration
@EnableConfigurationProperties(SoapProperties.class)
public class SoapClientConfig {

    private static final String GENERATED_CONTEXT_PATH = "com.example.countryinformation.generated";

    @Bean
    public Jaxb2Marshaller countryInfoMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(GENERATED_CONTEXT_PATH);
        return marshaller;
    }

    @Bean
    public WebServiceMessageSender countryInfoMessageSender(SoapProperties properties) {
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(properties.connectTimeout());
        sender.setReadTimeout(properties.readTimeout());
        return sender;
    }

    @Bean
    public ClientInterceptor soapLoggingInterceptor() {
        return new SoapLoggingInterceptor();
    }

    @Bean
    public WebServiceTemplate countryInfoWebServiceTemplate(SoapProperties properties,
            Jaxb2Marshaller countryInfoMarshaller,
            WebServiceMessageSender countryInfoMessageSender,
            ClientInterceptor soapLoggingInterceptor) {
        WebServiceTemplate template = new WebServiceTemplate();
        template.setDefaultUri(properties.endpoint());
        template.setMarshaller(countryInfoMarshaller);
        template.setUnmarshaller(countryInfoMarshaller);
        template.setMessageSender(countryInfoMessageSender);
        template.setInterceptors(new ClientInterceptor[] { soapLoggingInterceptor });
        return template;
    }
}
