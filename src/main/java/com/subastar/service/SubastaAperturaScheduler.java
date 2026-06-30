package com.subastar.service;

import com.subastar.dto.realtime.AuctionRealtimeEvent;
import com.subastar.dto.realtime.RealtimeEventType;
import com.subastar.model.Subasta;
import com.subastar.realtime.RealtimeEventPublisher;
import com.subastar.repository.SubastaRepository;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SubastaAperturaScheduler {

    private final SubastaRepository subastaRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @PostConstruct
    @Transactional
    public void repararSubastasProgramadas() {
        List<Subasta> programadas = subastaRepository.findByEstado("programada");
        for (Subasta s : programadas) {
            s.setEstado("abierta");
            subastaRepository.save(s);
            realtimeEventPublisher.publishAuctionListEvent(AuctionRealtimeEvent.builder()
                    .type(RealtimeEventType.AUCTION_STATE_CHANGED)
                    .subastaId(s.getIdentificador())
                    .build());
        }
    }
}
