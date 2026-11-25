package br.edu.ifto.aula1.security;

import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;
    private final Clock clock;

    public UsuarioDetailsService(UsuarioRepository usuarios, Clock clock) {
        this.usuarios = usuarios;
        this.clock = clock;
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

        // Bloqueio por tentativas: conferido pelo Spring Security antes da senha.
        return new ContaAutenticada(usuario.getLogin(), usuario.getPassword(), usuario.isAtivo(),
                usuario.isBloqueadoEm(LocalDateTime.now(clock)), authorities);
    }

    /** A senha (hash) atual da conta, ou vazio se não é mais aquela com que ela se autenticou. */
    @Transactional(readOnly = true)
    public Optional<String> senhaVigente(Authentication autenticacao) {
        return usuarios.findByLogin(autenticacao.getName()).map(Usuario::getPassword)
                .filter(hash -> autenticacao.getPrincipal() instanceof ContaAutenticada conta && conta.autenticouCom(hash));
    }
}
