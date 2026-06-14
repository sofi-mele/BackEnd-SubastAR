package com.subastar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class EmailService {

    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";

    private final JavaMailSender mailSender;
    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${mail.provider:smtp}")
    private String mailProvider;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from:}")
    private String resendFrom;

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
        String provider = mailProvider != null ? mailProvider.trim().toLowerCase() : "smtp";
        log.info("Intentando enviar email con provider={} to={} subject={}", provider, to, subject);

        if ("resend".equals(provider)) {
            enviarConResend(to, subject, text);
            return;
        }

        enviarConSmtp(to, subject, text);
    }

    private void enviarConSmtp(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Email enviado correctamente con provider=smtp to={} subject={}", to, subject);
    }

    private void enviarConResend(String to, String subject, String text) {
        if (!StringUtils.hasText(resendApiKey)) {
            throw new MailSendException("RESEND_API_KEY no esta configurada");
        }
        if (!StringUtils.hasText(resendFrom)) {
            throw new MailSendException("RESEND_FROM no esta configurado");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(resendApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "from", resendFrom,
                "to", List.of(to),
                "subject", subject,
                "text", text
        );

        try {
            ResponseEntity<String> response = restTemplateBuilder.build()
                    .postForEntity(RESEND_EMAILS_URL, new HttpEntity<>(body, headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Resend respondio con error status={} body={}", response.getStatusCode(), response.getBody());
                throw new MailSendException("Resend API devolvio un error: " + response.getStatusCode());
            }

            log.info("Email enviado correctamente con provider=resend to={} subject={}", to, subject);
        } catch (RestClientResponseException ex) {
            log.error("Resend respondio con error status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new MailSendException("Resend API devolvio un error: HTTP " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            log.error("Error al enviar email con Resend: {}", ex.getMessage(), ex);
            throw new MailSendException("No se pudo conectar con Resend API", ex);
        }
    }
}
