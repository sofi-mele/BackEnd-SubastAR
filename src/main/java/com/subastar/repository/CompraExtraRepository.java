package com.subastar.repository;

import com.subastar.model.CompraExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CompraExtraRepository extends JpaRepository<CompraExtra, Integer> {
    Optional<CompraExtra> findByRegistroId(Integer registroId);

    @Query("SELECT ce FROM CompraExtra ce WHERE ce.estadoPago = 'pendiente' AND ce.fechaCompra <= :fechaLimite")
    List<CompraExtra> findComprasPendientesVencidas(@Param("fechaLimite") LocalDateTime fechaLimite);
}
