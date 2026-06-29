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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private final ProductoDetalleRepository productoDetalleRepository;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final SeguroRepository seguroRepository;
    private final SeguroExtraRepository seguroExtraRepository;
    private final CompraExtraRepository compraExtraRepository;
    private final RegistroDeSubastaRepository registroDeSubastaRepository;
    private final CredencialRepository credencialRepository;
    private final NotificacionService notificacionService;
    private final SubastaEnVivoTimerService timerService;

    public Integer crearSubasta(CrearSubastaRequest req) {
        if (req.getNombre() == null || req.getNombre().isBlank())
            throw new BadRequestException("El nombre es obligatorio");
        if (req.getFecha() == null || req.getFecha().isBlank())
            throw new BadRequestException("La fecha es obligatoria (yyyy-MM-dd)");
        if (req.getHora() == null || req.getHora().isBlank())
            throw new BadRequestException("La hora es obligatoria (HH:mm:ss)");
        if (req.getMoneda() == null || req.getMoneda().isBlank())
            throw new BadRequestException("La moneda es obligatoria");
        if (req.getCategoriaRequerida() == null || req.getCategoriaRequerida().isBlank())
            throw new BadRequestException("La categoria_requerida es obligatoria");

        LocalDate fecha;
        LocalTime hora;
        try { fecha = LocalDate.parse(req.getFecha()); }
        catch (Exception e) { throw new BadRequestException("Formato de fecha inválido, usar yyyy-MM-dd"); }
        try { hora = LocalTime.parse(req.getHora()); }
        catch (Exception e) { throw new BadRequestException("Formato de hora inválido, usar HH:mm:ss"); }

        Subasta subasta = new Subasta();
        subasta.setFecha(fecha);
        subasta.setHora(hora);
        subasta.setEstado("abierta");
        subasta.setCategoria(req.getCategoriaRequerida());
        subasta = subastaRepository.save(subasta);

        SubastaExtra extra = new SubastaExtra();
        extra.setSubastaId(subasta.getIdentificador());
        extra.setNombre(req.getNombre());
        extra.setMoneda(req.getMoneda());
        extra.setRematadorNombre(req.getRematadorNombre());
        subastaExtraRepository.save(extra);

        return subasta.getIdentificador();
    }

    public void modificarSubasta(Integer subastaId, com.subastar.dto.admin.ModificarSubastaRequest req) {
        Subasta subasta = subastaRepository.findById(subastaId)
                .orElseThrow(() -> new ResourceNotFoundException("Subasta no encontrada"));
        SubastaExtra extra = subastaExtraRepository.findBySubastaId(subastaId)
                .orElseThrow(() -> new ResourceNotFoundException("Detalle de subasta no encontrado"));

        if (req.getNombre() != null && !req.getNombre().isBlank()) {
            extra.setNombre(req.getNombre());
        }
        if (req.getFecha() != null && !req.getFecha().isBlank()) {
            try { subasta.setFecha(LocalDate.parse(req.getFecha())); }
            catch (Exception e) { throw new BadRequestException("Formato de fecha inválido, usar yyyy-MM-dd"); }
        }
        if (req.getHora() != null && !req.getHora().isBlank()) {
            try { subasta.setHora(LocalTime.parse(req.getHora())); }
            catch (Exception e) { throw new BadRequestException("Formato de hora inválido, usar HH:mm:ss"); }
        }
        if (req.getMoneda() != null && !req.getMoneda().isBlank()) {
            extra.setMoneda(req.getMoneda());
        }
        if (req.getCategoriaRequerida() != null && !req.getCategoriaRequerida().isBlank()) {
            subasta.setCategoria(req.getCategoriaRequerida());
        }
        if (req.getRematadorNombre() != null) {
            extra.setRematadorNombre(req.getRematadorNombre());
        }

        subastaRepository.save(subasta);
        subastaExtraRepository.save(extra);
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

        // Borrar compras generadas por el sistema (empresa o test) para esta subasta
        List<RegistroDeSubasta> registros = registroDeSubastaRepository.findBySubastaIdentificador(subastaId);
        registroDeSubastaRepository.deleteAll(registros);

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

        timerService.resetTimer(subastaId);
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
        Catalogo catalogo;
        if (catalogos.isEmpty()) {
            SubastaExtra extra = subastaExtraRepository.findBySubastaId(req.getSubastaId()).orElse(null);
            Subasta subasta = subastaRepository.findById(req.getSubastaId()).get();
            Catalogo nuevo = new Catalogo();
            nuevo.setDescripcion(extra != null && extra.getNombre() != null ? extra.getNombre() : "Subasta #" + req.getSubastaId());
            nuevo.setSubasta(subasta);
            catalogo = catalogoRepository.save(nuevo);
        } else {
            catalogo = catalogos.get(0);
        }

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

    public void cargarCostoEnvio(Integer compraId, BigDecimal costoEnvio) {
        RegistroDeSubasta registro = registroDeSubastaRepository.findById(compraId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));

        CompraExtra extra = compraExtraRepository.findByRegistroId(compraId)
                .orElseThrow(() -> new ResourceNotFoundException("Detalle de compra no encontrado"));

        extra.setCostoEnvio(costoEnvio);
        compraExtraRepository.save(extra);

        Cliente cliente = registro.getCliente();
        String nombreItem = registro.getProducto().getDescripcionCatalogo() != null
                ? registro.getProducto().getDescripcionCatalogo()
                : "Producto #" + registro.getProducto().getIdentificador();

        notificacionService.notificarCostoEnvio(cliente, nombreItem,
                registro.getImporte(), registro.getComision(), costoEnvio);
    }

    @Transactional
    public void readmitirCliente(String email) {
        Credencial cred = credencialRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));
        Cliente cliente = clienteRepository.findById(cred.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        cliente.setAdmitido("si");
        clienteRepository.save(cliente);
    }
}
