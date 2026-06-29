package com.subastar.service;

import com.subastar.model.Subasta;
import com.subastar.repository.SubastaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SubastaAperturaScheduler {

    private final SubastaRepository subastaRepository;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void abrirSubastasProgramadas() {
        List<Subasta> programadas = subastaRepository.findByEstado("programada");
        LocalDateTime ahora = LocalDateTime.now();
        for (Subasta s : programadas) {
            if (s.getFecha() != null && s.getHora() != null) {
                LocalDateTime inicio = LocalDateTime.of(s.getFecha(), s.getHora());
                if (!ahora.isBefore(inicio)) {
                    s.setEstado("abierta");
                    subastaRepository.save(s);
                }
            }
        }
    }
}
