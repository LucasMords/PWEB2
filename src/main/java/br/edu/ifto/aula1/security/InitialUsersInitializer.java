package br.edu.ifto.aula1.security;

import br.edu.ifto.aula1.model.entity.Perfil;
import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.PerfilRepository;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastra os usuários da equipe (admin e atendente) ao subir a aplicação.
 * O banco H2 é recriado a cada execução, por isso o cadastro roda sempre.
 * Os perfis são gravados sem o prefixo ROLE_ e as senhas como hash BCrypt.
 *
 * O import.sql roda antes e já insere os perfis e as contas dos pacientes e médicos
 * de exemplo; aqui só é criado o que ainda não existir.
 */
@Component
public class InitialUsersInitializer implements ApplicationRunner {

    private final PerfilRepository perfis;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public InitialUsersInitializer(PerfilRepository perfis, UsuarioRepository usuarios,
                                   PasswordEncoder passwordEncoder) {
        this.perfis = perfis;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Perfil admin = criarPerfil(Perfil.ADMIN);
        Perfil atendente = criarPerfil(Perfil.ATENDENTE);
        // Papéis iniciais das contas criadas nos cadastros de médico e de paciente.
        // Já vêm do import.sql; ficam garantidos aqui caso ele mude.
        criarPerfil(Perfil.MEDICO);
        criarPerfil(Perfil.PACIENTE);

        // A administração também atende: marca, cancela e confirma agendamentos.
        criarUsuario("admin", "Admin123", admin, atendente);
        criarUsuario("atendente", "Atendente123", atendente);
    }

    private Perfil criarPerfil(String nome) {
        return perfis.findByNome(nome).orElseGet(() -> perfis.save(new Perfil(nome)));
    }

    private void criarUsuario(String login, String senha, Perfil... papeis) {
        if (usuarios.existsByLogin(login)) return;

        Usuario usuario = new Usuario(login, passwordEncoder.encode(senha), true);
        for (Perfil perfil : papeis) usuario.adicionarPerfil(perfil);
        usuarios.save(usuario);
    }
}
