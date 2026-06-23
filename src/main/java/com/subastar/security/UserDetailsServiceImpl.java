package com.subastar.security;

import com.subastar.model.Credencial;
import com.subastar.model.Empleado;
import com.subastar.repository.CredencialRepository;
import com.subastar.repository.EmpleadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final CredencialRepository credencialRepository;
    private final EmpleadoRepository empleadoRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Credencial credencial = credencialRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        String role = empleadoRepository.findById(credencial.getPersonaId())
                .map(Empleado::getCargo)
                .filter("Administrador"::equals)
                .map(c -> "ROLE_ADMIN")
                .orElse("ROLE_CLIENTE");

        return new User(
                credencial.getEmail(),
                credencial.getPasswordHash(),
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
