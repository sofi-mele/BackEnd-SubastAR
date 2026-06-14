package com.subastar.repository;

import com.subastar.model.ChequeCertificado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChequeCertificadoRepository extends JpaRepository<ChequeCertificado, Integer> {
    Optional<ChequeCertificado> findByMedioPagoId(Integer medioPagoId);
    boolean existsByNumeroCheque(String numeroCheque);
}
