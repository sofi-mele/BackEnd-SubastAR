package com.subastar.service;

import com.subastar.model.SubastaExtra;
import com.subastar.repository.SubastaExtraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoteTimerScheduler {

    private final SubastaExtraRepository subastaExtraRepository;
    private final CierreLoteService cierreLoteService;

    @Scheduled(fixedDelayString = "${app.scheduler.lote-timer-delay:5000}")
    public void verificarTimersVencidos() {
        List<SubastaExtra> activos = subastaExtraRepository.findActiveTimers();
        for (SubastaExtra extra : activos) {
            try {
                LocalDateTime ahora = LocalDateTime.now();
                boolean vencido;
                if (extra.getFechaUltimaPuja() != null) {
                    vencido = ahora.isAfter(extra.getFechaUltimaPuja().plusSeconds(20));
                } else {
                    vencido = ahora.isAfter(extra.getFechaInicioLote().plusSeconds(60));
                }
                if (vencido) {
                    log.info("Timer vencido para subasta={}, cerrando lote", extra.getSubastaId());
                    cierreLoteService.cerrarLote(extra.getSubastaId());
                }
            } catch (Exception e) {
                log.error("Error al cerrar lote de subasta={}: {}", extra.getSubastaId(), e.getMessage());
            }
        }
    }
}
