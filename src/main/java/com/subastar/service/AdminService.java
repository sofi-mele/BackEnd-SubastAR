package com.subastar.service;

import com.subastar.dto.admin.AsignarPolizaRequest;
import com.subastar.dto.admin.AsignarSubastaRequest;
import com.subastar.dto.admin.CrearSubastaRequest;
import com.subastar.exception.BadRequestException;
import com.subastar.exception.ResourceNotFoundException;
import com.subastar.model.*;
import com.subastar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminService {

    private final SubastaRepository subastaRepository;
    private final SubastaExtraRepository subastaExtraRepository;
    private final CatalogoRepository catalogoRepository;
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final SubastadorRepository subastadorRepository;
    private final EmpleadoRepository empleadoRepository;
    private final ProductoDetalleRepository productoDetalleRepository;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final SeguroRepository seguroRepository;
    private final SeguroExtraRepository seguroExtraRepository;
    private final NotificacionService notificacionService;

    public Integer crearSubasta(CrearSubastaRequest req) {
        Subastador subastador = subastadorRepository.findById(req.getRematadorId())
                .orElseThrow(() -> new ResourceNotFoundException("Subastador no encontrado: " + req.getRematadorId()));
        Empleado responsable = empleadoRepository.findById(req.getRematadorId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado para rematador_id: " + req.getRematadorId()));

        Subasta subasta = new Subasta();
        subasta.setFecha(req.getFecha());
        subasta.setHora(req.getHora());
        subasta.setEstado("abierta");
        subasta.setCategoria(req.getCategoriaRequerida());
        subasta.setSubastador(subastador);
        subasta = subastaRepository.save(subasta);

        Catalogo catalogo = new Catalogo();
        catalogo.setDescripcion(req.getNombre());
        catalogo.setSubasta(subasta);
        catalogo.setResponsable(responsable);
        catalogoRepository.save(catalogo);

        SubastaExtra extra = new SubastaExtra();
        extra.setSubastaId(subasta.getIdentificador());
        extra.setNombre(req.getNombre());
        extra.setMoneda(req.getMoneda());
        subastaExtraRepository.save(extra);

        return subasta.getIdentificador();
    }

    public void resetearSubasta(Integer subastaId) {
        Subasta subasta = subastaRepository.findById(subastaId)
                .orElseThrow(() -> new ResourceNotFoundException("Subasta no encontrada"));

        List<Catalogo> catalogos = catalogoRepository.findAllBySubastaIdentificador(subastaId);
        if (catalogos.isEmpty()) {
            throw new BadRequestException("La subasta no tiene catálogo");
        }

        List<ItemCatalogo> items = catalogos.stream()
                .flatMap(c -> itemCatalogoRepository.findByCatalogoIdentificador(c.getIdentificador()).stream())
                .collect(Collectors.toList());

        if (items.isEmpty()) {
            throw new BadRequestException("La subasta no tiene ítems en el catálogo");
        }

        for (ItemCatalogo item : items) {
            item.setSubastado("no");
            itemCatalogoRepository.save(item);
        }

        subasta.setEstado("abierta");
        subastaRepository.save(subasta);

        Integer primerItemId = items.stream()
                .map(ItemCatalogo::getIdentificador)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        SubastaExtra extra = subastaExtraRepository.findBySubastaId(subastaId)
                .orElseThrow(() -> new ResourceNotFoundException("SubastaExtra no encontrada para subasta " + subastaId));
        extra.setItemActualId(primerItemId);
        extra.setFechaInicioLote(null);
        extra.setFechaUltimaPuja(null);
        subastaExtraRepository.save(extra);
    }

    public void asignarSubasta(Integer bienId, AsignarSubastaRequest req) {
        ProductoDetalle det = productoDetalleRepository.findById(bienId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien no encontrado"));

        if (!"aceptado".equals(det.getEstadoSolicitud())) {
            throw new BadRequestException("El bien debe estar en estado 'aceptado' para ser asignado a una subasta");
        }
        if (!Boolean.TRUE.equals(det.getAceptoCondiciones())) {
            throw new BadRequestException("El usuario aún no aceptó las condiciones del bien (precio base y comisiones)");
        }
        if (det.getPrecioBase() == null || det.getComision() == null) {
            throw new BadRequestException("El bien no tiene precio_base o comision definidos");
        }
        if (itemCatalogoRepository.existsActivaByProductoId(det.getProductoId())) {
            throw new BadRequestException("El bien ya se encuentra asignado a una subasta activa");
        }

        subastaRepository.findById(req.getSubastaId())
                .orElseThrow(() -> new ResourceNotFoundException("Subasta no encontrada: " + req.getSubastaId()));

        List<Catalogo> catalogos = catalogoRepository.findAllBySubastaIdentificador(req.getSubastaId());
        if (catalogos.isEmpty()) {
            throw new BadRequestException("La subasta no tiene catálogo");
        }
        Catalogo catalogo = catalogos.get(0);

        Producto producto = productoRepository.findById(det.getProductoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));

        ItemCatalogo item = new ItemCatalogo();
        item.setCatalogo(catalogo);
        item.setProducto(producto);
        item.setPrecioBase(det.getPrecioBase());
        item.setComision(det.getComision());
        item.setSubastado("no");
        itemCatalogoRepository.save(item);

        det.setSubastaAsignada(String.valueOf(req.getSubastaId()));
        productoDetalleRepository.save(det);

        Cliente cliente = clienteRepository.findById(det.getClienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        notificacionService.notificarBienAsignadoASubasta(cliente, det.getNombre(), req.getSubastaId());
    }

    private static final String DIRECCION_ENVIO = "Av. Corrientes 2300, CABA";

    public void indicarDireccionEnvio(Integer bienId) {
        ProductoDetalle det = productoDetalleRepository.findById(bienId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien no encontrado"));
        if (!"pendiente".equals(det.getEstadoSolicitud()) && !"en_revision".equals(det.getEstadoSolicitud())) {
            throw new BadRequestException("Solo se puede indicar la dirección de envío para bienes en estado 'pendiente' o 'en_revision'");
        }
        det.setEstadoSolicitud("en_revision");
        productoDetalleRepository.save(det);
        Cliente cliente = clienteRepository.findById(det.getClienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        notificacionService.notificarDireccionEnvio(cliente, det.getNombre(), DIRECCION_ENVIO);
    }

    public void asignarPoliza(Integer bienId, AsignarPolizaRequest req) {
        ProductoDetalle det = productoDetalleRepository.findById(bienId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien no encontrado"));

        Seguro seguro = new Seguro();
        seguro.setNroPoliza(req.getNumeroPoliza());
        seguro.setCompania(req.getCompaniaSeguro());
        seguro.setImporte(req.getValorAsegurado());
        seguroRepository.save(seguro);

        det.setPolizaId(req.getNumeroPoliza());
        productoDetalleRepository.save(det);

        Producto producto = productoRepository.findById(det.getProductoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado para el bien " + bienId));
        producto.setSeguroNroPoliza(req.getNumeroPoliza());
        productoRepository.save(producto);

        boolean tieneExtras = req.getCobertura() != null || req.getVigenciaDesde() != null
                || req.getVigenciaHasta() != null || req.getContactoTelefono() != null
                || req.getContactoEmail() != null || req.getContactoWeb() != null;
        if (tieneExtras) {
            SeguroExtra extra = seguroExtraRepository.findById(req.getNumeroPoliza())
                    .orElse(new SeguroExtra());
            extra.setPolizaId(req.getNumeroPoliza());
            extra.setCobertura(req.getCobertura());
            extra.setVigenciaDesde(req.getVigenciaDesde());
            extra.setVigenciaHasta(req.getVigenciaHasta());
            extra.setContactoTelefono(req.getContactoTelefono());
            extra.setContactoEmail(req.getContactoEmail());
            extra.setContactoWeb(req.getContactoWeb());
            seguroExtraRepository.save(extra);
        }
    }
}
