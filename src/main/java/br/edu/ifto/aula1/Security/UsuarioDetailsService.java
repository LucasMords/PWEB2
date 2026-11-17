package br.edu.ifto.aula1.Security;

import br.edu.ifto.aula1.Model.Entity.Usuario;
import br.edu.ifto.aula1.Model.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public UsuarioDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        Usuario usuario = usuarios.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        // Cada perfil vira uma autoridade: ADMIN -> ROLE_ADMIN, USER -> ROLE_USER.
        List<SimpleGrantedAuthority> authorities = usuario.getPerfis().stream()
                .map(perfil -> new SimpleGrantedAuthority("ROLE_" + perfil.getNome()))
                .toList();

        return User.withUsername(usuario.getLogin())
                .password(usuario.getPassword())
                .authorities(authorities)
                .disabled(!usuario.isAtivo())
                .build();
    }
}
