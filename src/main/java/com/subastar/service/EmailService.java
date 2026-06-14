package com.subastar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void enviarCodigoVerificacion(String email, String codigo) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Codigo de verificacion - Subastar");
        message.setText("Tu codigo de verificacion es: " + codigo);

        try {
            log.info("Intentando enviar codigo de verificacion por SMTP to={} subject={}", email, message.getSubject());
            mailSender.send(message);
            log.info("Codigo de verificacion enviado correctamente por SMTP to={}", email);
        } catch (MailException ex) {
            log.error("Fallo el envio del codigo de verificacion por SMTP to={}: {}", email, ex.getMessage(), ex);
            throw ex;
        }
    }

    public void enviarNotificacionAprobacion(String email, String codigo) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Registro aprobado - Subastar");
        message.setText("Tu registro fue aprobado. Tu codigo es: " + codigo);

        try {
            log.info("Intentando enviar notificacion de aprobacion por SMTP to={} subject={}", email, message.getSubject());
            mailSender.send(message);
            log.info("Notificacion de aprobacion enviada correctamente por SMTP to={}", email);
        } catch (MailException ex) {
            log.error("Fallo el envio de la notificacion de aprobacion por SMTP to={}: {}", email, ex.getMessage(), ex);
            throw ex;
        }
    }

    public void enviarTokenRecuperacion(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Recuperacion de contrasena - Subastar");
        message.setText(
                "Recibimos una solicitud para restablecer tu contrasena.\n\n" +
                        "Tu token de recuperacion es: " + token + "\n\n" +
                        "Este token es valido por 1 hora. Si no solicitaste este cambio, ignora este mensaje."
        );

        try {
            log.info("Intentando enviar token de recuperacion por SMTP to={} subject={}", email, message.getSubject());
            mailSender.send(message);
            log.info("Token de recuperacion enviado correctamente por SMTP to={}", email);
        } catch (MailException ex) {
            log.error("Fallo el envio del token de recuperacion por SMTP to={}: {}", email, ex.getMessage(), ex);
            throw ex;
        }
    }
}
