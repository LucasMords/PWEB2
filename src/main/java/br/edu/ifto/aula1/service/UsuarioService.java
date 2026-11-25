package br.edu.ifto.aula1.service;

import br.edu.ifto.aula1.model.entity.Perfil;
import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.PerfilRepository;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import br.edu.ifto.aula1.validation.Credenciais;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PerfilRepository perfis;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarios, PerfilRepository perfis, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean loginEmUso(String login) {
        return usuarios.existsByLogin(login);
    }

    /**
     * Cria a conta de acesso já com o papel inicial. A senha é gravada como hash BCrypt.
     * Os perfis são criados pelo InitialUsersInitializer ao subir a aplicação.
     */
    @Transactional
    public Usuario criar(Credenciais credenciais, String nomePerfil) {
        Perfil perfil = perfis.findByNome(nomePerfil)
                .orElseThrow(() -> new IllegalStateException("Perfil não cadastrado: " + nomePerfil));

        Usuario usuario = new Usuario(credenciais.getLogin(), passwordEncoder.encode(credenciais.getSenha()), true);
        usuario.adicionarPerfil(perfil);
        return usuarios.save(usuario);
    }
}
