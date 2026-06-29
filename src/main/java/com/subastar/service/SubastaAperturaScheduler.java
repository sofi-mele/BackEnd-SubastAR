package com.subastar.service;

import com.subastar.model.Subasta;
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

    // Convierte cualquier subasta "programada" a "abierta" al arrancar la app
    @PostConstruct
    @Transactional
    public void repararSubastasProgramadas() {
        List<Subasta> programadas = subastaRepository.findByEstado("programada");
        for (Subasta s : programadas) {
            s.setEstado("abierta");
            subastaRepository.save(s);
        }
    }
}
