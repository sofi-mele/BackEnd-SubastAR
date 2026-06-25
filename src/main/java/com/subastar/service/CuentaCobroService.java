package com.subastar.service;

import com.subastar.dto.cuentaCobro.CuentaCobroRequest;
import com.subastar.dto.cuentaCobro.CuentaCobroResponse;
import com.subastar.exception.ResourceNotFoundException;
import com.subastar.model.CuentaBancaria;
import com.subastar.repository.CuentaBancariaRepository;
import com.subastar.repository.CredencialRepository;
import com.subastar.repository.MedioPagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CuentaCobroService {

    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final MedioPagoRepository medioPagoRepository;
    private final CredencialRepository credencialRepository;

    public List<CuentaCobroResponse> listar(String email) {
        Integer clienteId = getClienteId(email);
        return medioPagoRepository.findByClienteIdentificadorAndEliminadoFalse(clienteId)
                .stream()
                .filter(mp -> "cuenta_bancaria".equals(mp.getTipo()))
                .map(mp -> cuentaBancariaRepository.findByMedioPagoId(mp.getId()).orElse(null))
                .filter(cb -> cb != null)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public CuentaCobroResponse crear(String email, CuentaCobroRequest req) {
        throw new UnsupportedOperationException("Las cuentas de cobro se gestionan como medios de pago");
    }

    private CuentaCobroResponse toResponse(CuentaBancaria cb) {
        CuentaCobroResponse r = new CuentaCobroResponse();
        r.setId(cb.getMedioPagoId());
        r.setNombreBanco(cb.getNombreBanco());
        r.setCbuIban(cb.getCbuIban());
        r.setPais(cb.getPaisBanco());
        r.setMoneda(null);
        return r;
    }

    private Integer getClienteId(String email) {
        return credencialRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"))
                .getPersonaId();
    }
}
