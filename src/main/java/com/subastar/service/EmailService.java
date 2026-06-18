package com.subastar.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;

@RequiredArgsConstructor
@Service
@Slf4j
public class EmailService {

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GMAIL_SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${gmail.api.client-id:}")
    private String clientId;

    @Value("${gmail.api.client-secret:}")
    private String clientSecret;

    @Value("${gmail.api.refresh-token:}")
    private String refreshToken;

    @Value("${gmail.api.from:}")
    private String gmailFrom;

    public void enviarCodigoVerificacion(String email, String codigo) {
        enviar(email, "Codigo de verificacion - Subastar", "Tu codigo de verificacion es: " + codigo);
    }

    public void enviarNotificacionAprobacion(String email, String codigo) {
        enviar(email, "Registro aprobado - Subastar", "Tu registro fue aprobado. Tu codigo es: " + codigo);
    }

    public void enviarTokenRecuperacion(String email, String token) {
        enviar(
                email,
                "Recuperacion de contrasena - Subastar",
                "Recibimos una solicitud para restablecer tu contrasena.\n\n" +
                        "Tu token de recuperacion es: " + token + "\n\n" +
                        "Este token es valido por 1 hora. Si no solicitaste este cambio, ignora este mensaje."
        );
    }

    private void enviar(String to, String subject, String text) {
        validarConfiguracion();
        log.info("Intentando enviar email con Gmail API to={} subject={}", to, subject);

        try {
            String accessToken = obtenerAccessToken();
            String rawMessage = crearMensajeMime(to, subject, text);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplateBuilder.build().postForEntity(
                    GMAIL_SEND_URL,
                    new HttpEntity<>(new GmailMessageRequest(rawMessage), headers),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new MailSendException("Gmail API devolvio un error: " + response.getStatusCode());
            }

            log.info("Email enviado correctamente con Gmail API to={} subject={}", to, subject);
        } catch (RestClientResponseException ex) {
            log.error("Gmail API respondio con error status={} body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new MailSendException("Gmail API devolvio un error HTTP " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            log.error("No se pudo conectar con Gmail API: {}", ex.getMessage(), ex);
            throw new MailSendException("No se pudo conectar con Gmail API", ex);
        } catch (MessagingException | IOException ex) {
            log.error("No se pudo construir el email para Gmail API: {}", ex.getMessage(), ex);
            throw new MailSendException("No se pudo construir el email", ex);
        }
    }

    private String obtenerAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        ResponseEntity<GmailTokenResponse> response = restTemplateBuilder.build().postForEntity(
                GOOGLE_TOKEN_URL,
                new HttpEntity<>(form, headers),
                GmailTokenResponse.class
        );

        GmailTokenResponse tokenResponse = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful()
                || tokenResponse == null
                || !StringUtils.hasText(tokenResponse.accessToken())) {
            throw new MailSendException("Google OAuth no devolvio un access token valido");
        }
        return tokenResponse.accessToken();
    }

    private String crearMensajeMime(String to, String subject, String text) throws MessagingException, IOException {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(gmailFrom));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject, StandardCharsets.UTF_8.name());
        message.setText(text, StandardCharsets.UTF_8.name());
        message.setSentDate(new Date());
        message.saveChanges();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            message.writeTo(output);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
        }
    }

    private void validarConfiguracion() {
        if (!StringUtils.hasText(clientId)) {
            throw new MailSendException("GMAIL_CLIENT_ID no esta configurado");
        }
        if (!StringUtils.hasText(clientSecret)) {
            throw new MailSendException("GMAIL_CLIENT_SECRET no esta configurado");
        }
        if (!StringUtils.hasText(refreshToken)) {
            throw new MailSendException("GMAIL_REFRESH_TOKEN no esta configurado");
        }
        if (!StringUtils.hasText(gmailFrom)) {
            throw new MailSendException("GMAIL_FROM no esta configurado");
        }
    }

    private record GmailMessageRequest(String raw) {
    }

    private record GmailTokenResponse(@JsonProperty("access_token") String accessToken) {
    }
}
