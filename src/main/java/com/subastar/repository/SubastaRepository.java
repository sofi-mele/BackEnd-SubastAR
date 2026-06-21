package com.subastar.repository;

import com.subastar.model.Subasta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubastaRepository extends JpaRepository<Subasta, Integer> {

    @Query("""
        SELECT s, se FROM Subasta s
        LEFT JOIN SubastaExtra se ON se.subastaId = s.identificador
        WHERE (:categoria IS NULL OR s.categoria = :categoria)
          AND (:moneda IS NULL OR se.moneda = :moneda)
          AND (:busqueda IS NULL OR (se.nombre IS NOT NULL
               AND LOWER(se.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))))
        """)
    List<Object[]> findForListado(
            @Param("categoria") String categoria,
            @Param("moneda") String moneda,
            @Param("busqueda") String busqueda);

    @Query("""
        SELECT s FROM Subasta s
        WHERE (:estado IS NULL OR s.estado = :estado)
          AND (:categoria IS NULL OR s.categoria = :categoria)
        """)
    List<Subasta> findByFiltros(
            @Param("estado") String estado,
            @Param("categoria") String categoria);
}
