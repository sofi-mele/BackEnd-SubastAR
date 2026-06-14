package com.subastar.repository;

import com.subastar.model.TarjetaCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TarjetaCreditoRepository extends JpaRepository<TarjetaCredito, Integer> {
    Optional<TarjetaCredito> findByMedioPagoId(Integer medioPagoId);
}
