package com.subastar.repository;

import com.subastar.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {
    List<Producto> findByDuenioIdentificador(Integer duenioId);
    List<Producto> findBySeguroNroPoliza(String seguroNroPoliza);

    @Query("SELECT DISTINCT p.seguroNroPoliza FROM Producto p, ProductoDetalle pd WHERE pd.productoId = p.identificador AND pd.clienteId = :clienteId AND p.seguroNroPoliza IS NOT NULL")
    List<String> findPolizaIdsByClienteId(@Param("clienteId") Integer clienteId);
}
