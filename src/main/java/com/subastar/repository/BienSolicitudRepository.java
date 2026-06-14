package com.subastar.repository;

import com.subastar.model.BienSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BienSolicitudRepository extends JpaRepository<BienSolicitud, Integer> {
    Optional<BienSolicitud> findByCodigoSolicitud(String codigo);
    Optional<BienSolicitud> findByProductoId(Integer productoId);
    List<BienSolicitud> findByClienteIdentificador(Integer clienteId);
}
