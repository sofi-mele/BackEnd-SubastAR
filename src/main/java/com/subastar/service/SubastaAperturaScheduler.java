package com.subastar.service;

import com.subastar.dto.realtime.AuctionRealtimeEvent;
import com.subastar.dto.realtime.RealtimeEventType;
import com.subastar.model.Subasta;
import com.subastar.realtime.RealtimeEventPublisher;
import com.subastar.repository.SubastaRepository;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SubastaAperturaScheduler {

    private final SubastaRepository subastaRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    // IDs de subastas que ya fueron notificadas como "en vivo" en esta sesión
    private final Set<Integer> notificadas = new HashSet<>();

    @PostConstruct
    @Transactional
    public void repararSubastasProgramadas() {
        List<Subasta> programadas = subastaRepository.findByEstado("programada");
        for (Subasta s : programadas) {
            s.setEstado("abierta");
            subastaRepository.save(s);
        }
    }

    // Corre cada 30 segundos y notifica via WebSocket cuando una subasta pasa a "en vivo"
    @Scheduled(fixedDelay = 30000)
    public void notificarSubastasQueInician() {
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        List<Subasta> abiertas = subastaRepository.findByEstado("abierta");
        for (Subasta s : abiertas) {
            if (s.getFecha() == null || s.getHora() == null) continue;
            LocalDateTime inicio = LocalDateTime.of(s.getFecha(), s.getHora());
            boolean yaInicio = !ahora.isBefore(inicio);
            if (yaInicio && !notificadas.contains(s.getIdentificador())) {
                notificadas.add(s.getIdentificador());
                realtimeEventPublisher.publishAuctionListEvent(AuctionRealtimeEvent.builder()
                        .type(RealtimeEventType.AUCTION_STATE_CHANGED)
                        .subastaId(s.getIdentificador())
                        .build());
            }
        }
    }
}
