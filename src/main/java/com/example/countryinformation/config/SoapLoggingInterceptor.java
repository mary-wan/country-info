package com.example.countryinformation.config;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SoapLoggingInterceptor implements ClientInterceptor {

    private static final int MAX_LOGGED_CHARS = 2000;

    @Override
    public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
        if (log.isDebugEnabled()) {
            log.debug("SOAP request: {}", render(messageContext.getRequest()));
        }
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
        if (log.isDebugEnabled()) {
            log.debug("SOAP response: {}", render(messageContext.getResponse()));
        }
        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
        log.warn("SOAP fault: {}", render(messageContext.getResponse()));
        return true;
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
        if (ex != null) {
            log.warn("SOAP call failed: {}", ex.getMessage());
        }
    }

    private String render(WebServiceMessage message) {
        if (message == null) {
            return "<empty>";
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            message.writeTo(out);
            String payload = out.toString(StandardCharsets.UTF_8);
            return payload.length() <= MAX_LOGGED_CHARS
                    ? payload
                    : payload.substring(0, MAX_LOGGED_CHARS) + "...[truncated]";
        } catch (Exception e) {
            return "<unrenderable: " + e.getMessage() + ">";
        }
    }
}
