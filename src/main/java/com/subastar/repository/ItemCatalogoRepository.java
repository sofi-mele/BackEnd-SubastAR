package com.subastar.repository;

import com.subastar.model.ItemCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemCatalogoRepository extends JpaRepository<ItemCatalogo, Integer> {
    List<ItemCatalogo> findByCatalogoIdentificador(Integer catalogoId);

    @Query("SELECT i FROM ItemCatalogo i WHERE i.catalogo.subasta.identificador = :subastaId AND (i.subastado IS NULL OR i.subastado <> 'si') AND i.identificador > :currentItemId ORDER BY i.identificador ASC")
    List<ItemCatalogo> findNextAvailableItems(@Param("subastaId") Integer subastaId, @Param("currentItemId") Integer currentItemId);

    @Query("SELECT COUNT(i) > 0 FROM ItemCatalogo i " +
           "WHERE i.producto.identificador = :productoId AND i.catalogo.subasta.estado <> 'cerrada'")
    boolean existsActivaByProductoId(@Param("productoId") Integer productoId);
}
