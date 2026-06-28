package com.subastar.service;

import com.subastar.model.CompraExtra;
import com.subastar.model.Multa;
import com.subastar.model.RegistroDeSubasta;
import com.subastar.repository.ClienteRepository;
import com.subastar.repository.CompraExtraRepository;
import com.subastar.repository.MultaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MultaScheduler {

    private final CompraExtraRepository compraExtraRepository;
    private final MultaRepository multaRepository;
    private final ClienteRepository clienteRepository;
    private final NotificacionService notificacionService;

    @Transactional
    @Scheduled(fixedDelayString = "${app.scheduler.multas-delay:3600000}")
    public void verificarPagosPendientes() {
        LocalDateTime fechaLimite = LocalDateTime.now().minusHours(72);
        List<CompraExtra> vencidas = compraExtraRepository.findComprasPendientesVencidas(fechaLimite);

        for (CompraExtra compra : vencidas) {
            try {
                if (multaRepository.existsByRegistroId(compra.getRegistroId())) continue;

                RegistroDeSubasta registro = compra.getRegistroDeSubasta();
                if (registro == null) {
                    log.warn("RegistroDeSubasta no encontrado para compra={}", compra.getRegistroId());
                    continue;
                }

                BigDecimal monto = registro.getImporte().multiply(new BigDecimal("0.10"));

                Multa multa = new Multa();
                multa.setCliente(registro.getCliente());
                multa.setRegistroId(compra.getRegistroId());
                multa.setMonto(monto);
                multa.setEstado("pendiente");
                multaRepository.save(multa);

                registro.getCliente().setAdmitido("no");
                clienteRepository.save(registro.getCliente());

                notificacionService.notificarMulta(
                        registro.getCliente(), monto, "Pago no realizado dentro de las 72 horas");

                log.info("Multa creada: cliente={} compra={} monto={}",
                        registro.getCliente().getIdentificador(), compra.getRegistroId(), monto);
            } catch (Exception e) {
                log.error("Error al procesar multa para compra={}: {}", compra.getRegistroId(), e.getMessage());
            }
        }
    }
}
