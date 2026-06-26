package com.subastar.service;

import com.subastar.event.NotificationCreatedDomainEvent;
import com.subastar.model.ChatMensaje;
import com.subastar.model.Cliente;
import com.subastar.repository.ChatMensajeRepository;
import com.subastar.repository.CredencialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class NotificacionService {

    private final ChatMensajeRepository chatMensajeRepository;
    private final CredencialRepository credencialRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void notificarGanadorSubasta(Cliente cliente, String nombreItem,
                                        BigDecimal importePujado, BigDecimal comision, BigDecimal costoEnvio) {
        BigDecimal totalComision = comision != null ? comision : BigDecimal.ZERO;
        BigDecimal envio = costoEnvio != null ? costoEnvio : BigDecimal.ZERO;
        BigDecimal total = importePujado.add(totalComision).add(envio);
        String lineaEnvio = costoEnvio != null
                ? "Costo de envío a tu dirección declarada: $" + formatMonto(costoEnvio) + "\n"
                : "Costo de envío a tu dirección declarada: a confirmar por la empresa.\n";
        String contenido = "¡Ganaste el ítem \"" + nombreItem + "\"!\n"
                + "Importe ofertado: $" + formatMonto(importePujado) + "\n"
                + "Comisión: $" + formatMonto(totalComision) + "\n"
                + lineaEnvio
                + "Total a pagar: $" + formatMonto(total) + "\n"
                + "Podés regularizar tu pago desde 'Mis compras'.\n"
                + "Nota: si retirás el bien personalmente, perdés la cobertura del seguro asociado.";
        enviar(cliente, "compra", contenido);
    }

    public void notificarCostoEnvio(Cliente cliente, String nombreItem,
                                    BigDecimal importePujado, BigDecimal comision, BigDecimal costoEnvio) {
        BigDecimal totalComision = comision != null ? comision : BigDecimal.ZERO;
        BigDecimal total = importePujado.add(totalComision).add(costoEnvio);
        String contenido = "Se confirmó el costo de envío para tu compra \"" + nombreItem + "\".\n"
                + "Importe ofertado: $" + formatMonto(importePujado) + "\n"
                + "Comisión: $" + formatMonto(totalComision) + "\n"
                + "Costo de envío: $" + formatMonto(costoEnvio) + "\n"
                + "Total a pagar: $" + formatMonto(total) + "\n"
                + "Podés regularizar tu pago desde 'Mis compras'.\n"
                + "Nota: si retirás el bien personalmente, perdés la cobertura del seguro asociado.";
        enviar(cliente, "compra", contenido);
    }

    public void notificarPagoRegularizado(Cliente cliente, String nombreItem, BigDecimal total) {
        String contenido = "Tu pago por el ítem \"" + nombreItem + "\" fue registrado correctamente.\n"
                + "Total abonado: $" + formatMonto(total);
        enviar(cliente, "bot", contenido);
    }

    public void notificarPujaRegistrada(Cliente cliente, String nombreItem, BigDecimal monto) {
        String contenido = "Tu puja de $" + formatMonto(monto) + " por el ítem \"" + nombreItem + "\" fue registrada exitosamente.";
        enviar(cliente, "bot", contenido);
    }

    public void notificarMedioPagoAgregado(Cliente cliente, String descripcion) {
        String contenido = "Medio de pago agregado: " + descripcion + ".\nEstá pendiente de verificación por la empresa.";
        enviar(cliente, "bot", contenido);
    }

    public void notificarMedioPagoVerificado(Cliente cliente, String descripcion) {
        String contenido = "Tu medio de pago \"" + descripcion + "\" fue verificado y ya está habilitado para pujas y pagos.";
        enviar(cliente, "bot", contenido);
    }

    public void notificarMedioPagoEliminado(Cliente cliente, String descripcion) {
        String contenido = "Medio de pago eliminado: " + descripcion + ".";
        enviar(cliente, "bot", contenido);
    }

    public void notificarMedioPagoRechazado(Cliente cliente, String descripcion) {
        String contenido = "Tu medio de pago \"" + descripcion + "\" no fue aprobado por la empresa.\n"
                + "Podés agregar otro medio de pago desde tu perfil.";
        enviar(cliente, "bot", contenido);
    }

    public void notificarBienConfirmado(Cliente cliente, String nombreBien) {
        String contenido = "Tu bien \"" + nombreBien + "\" fue recibido y está siendo revisado por nuestro equipo.\n"
                + "Te notificaremos cuando tengamos novedades.";
        enviar(cliente, "bien", contenido);
    }

    public void notificarSolicitudBienCargada(Cliente cliente, String nombreBien) {
        String nombre = (nombreBien == null || nombreBien.isBlank()) ? "tu bien" : "\"" + nombreBien + "\"";
        String contenido = "Recibimos la carga de " + nombre + ".\n"
                + "Tu solicitud quedó registrada y podés continuar desde Mis bienes.";
        enviar(cliente, "bien", contenido);
    }

    public void notificarMulta(Cliente cliente, BigDecimal monto, String motivo) {
        String contenido = "Se te aplicó una multa de $" + formatMonto(monto) + ".\n"
                + "Motivo: " + motivo + "\n"
                + "Debés regularizarla antes de participar en otra subasta.";
        enviar(cliente, "multa", contenido);
    }

    public void notificarDevolucion(Cliente cliente, String nombreBien, BigDecimal costoDevolucion) {
        String contenido = "Rechazaste las condiciones para el bien \"" + nombreBien + "\".\n"
                + "El bien será devuelto a tu dirección con un costo de devolución de $" + formatMonto(costoDevolucion) + ".\n"
                + "Deberás abonar este monto para recibir tu bien.";
        enviar(cliente, "bien", contenido);
    }

    public void notificarBienAceptado(Cliente cliente, String nombreBien, BigDecimal precioBase) {
        String contenido = "Tu bien \"" + nombreBien + "\" fue aceptado para subasta.\n"
                + "Precio base: $" + formatMonto(precioBase) + "\n"
                + "Revisá los detalles en 'Mis bienes'.";
        enviar(cliente, "bien", contenido);
    }

    public void notificarBienAsignadoASubasta(Cliente cliente, String nombreBien, Integer subastaId) {
        String contenido = "Tu bien \"" + nombreBien + "\" fue incluido en la subasta #" + subastaId + ".\n"
                + "Podés ver los detalles desde 'Mis bienes'.";
        enviar(cliente, "bien", contenido);
    }

    public void notificarBienRechazado(Cliente cliente, String nombreBien, String motivo) {
        String contenido = "Tu bien \"" + nombreBien + "\" no fue aceptado para subasta.\n"
                + "Motivo: " + (motivo != null ? motivo : "Sin especificar") + "\n"
                + "Podés contactar con la empresa para más información.";
        enviar(cliente, "bien", contenido);
    }

    public void notificarDireccionEnvio(Cliente cliente, String nombreBien, String direccion) {
        String contenido = "La empresa está interesada en tu bien \"" + nombreBien + "\".\n"
                + "Por favor, envialo a la siguiente dirección para proceder con la inspección:\n"
                + direccion + "\n"
                + "Recordá que si el bien no es aceptado tras la inspección, la devolución es con cargo a tu cuenta.";
        enviar(cliente, "bien", contenido);
    }

    public void notificarInspeccionFisicaRequerida(Cliente cliente, String nombreBien) {
        String contenido = "Estamos interesados en tu bien \"" + nombreBien + "\".\n"
                + "Para continuar, traelo a nuestro depósito: Corrientes 2300, CABA (lun-vie 9-17 hs).\n"
                + "Importante: si tras la inspección presencial no es aceptado, el costo de devolución corre por tu cuenta.";
        enviar(cliente, "bien", contenido);
    }

    private String formatMonto(BigDecimal monto) {
        if (monto == null) return "0";
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.of("es", "AR"));
        fmt.setMaximumFractionDigits(2);
        fmt.setMinimumFractionDigits(0);
        return fmt.format(monto.stripTrailingZeros());
    }

    private void enviar(Cliente cliente, String tipo, String contenido) {
        ChatMensaje msg = new ChatMensaje();
        msg.setCliente(cliente);
        msg.setTipo(tipo);
        msg.setEmisor("sistema");
        msg.setContenido(contenido);
        msg.setLeido(false);
        ChatMensaje savedMsg = chatMensajeRepository.save(msg);

        Integer clienteId = cliente != null ? cliente.getIdentificador() : null;
        credencialRepository.findByPersonaId(clienteId).ifPresentOrElse(
                credencial -> eventPublisher.publishEvent(new NotificationCreatedDomainEvent(
                        credencial.getEmail(),
                        savedMsg.getId(),
                        savedMsg.getTipo(),
                        tituloParaTipo(savedMsg.getTipo()),
                        savedMsg.getContenido(),
                        savedMsg.getTimestampMsg(),
                        savedMsg.isLeido()
                )),
                () -> log.warn("Notification {} saved but no credential was found for clienteId={}",
                        savedMsg.getId(), clienteId)
        );
    }

    private String tituloParaTipo(String tipo) {
        return switch (tipo) {
            case "compra" -> "Compra";
            case "multa" -> "Multa";
            case "bien" -> "Aviso";
            case "poliza" -> "Poliza";
            case "soporte" -> "Soporte";
            default -> "Aviso";
        };
    }
}
