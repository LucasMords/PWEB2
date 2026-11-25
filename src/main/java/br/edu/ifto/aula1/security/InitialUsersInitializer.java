package br.edu.ifto.aula1.security;

import br.edu.ifto.aula1.model.entity.Perfil;
import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.PerfilRepository;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria as contas da equipe (admin e atendente) na primeira vez que a aplicação sobe num
 * banco novo; depois disso, não faz nada (as contas já existem e as senhas podem ter sido
 * trocadas). As senhas vêm das propriedades clinica.usuarios-iniciais.*: em desenvolvimento
 * têm um valor padrão, em produção vêm do ambiente. Senha vazia = a conta não é criada.
 *
 * Os perfis vêm da migração V1 do Flyway; aqui só são garantidos caso faltem.
 */
@Component
public class InitialUsersInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialUsersInitializer.class);

    private final PerfilRepository perfis;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final String senhaAdmin;
    private final String senhaAtendente;

    public InitialUsersInitializer(PerfilRepository perfis, UsuarioRepository usuarios,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${clinica.usuarios-iniciais.senha-admin}") String senhaAdmin,
                                   @Value("${clinica.usuarios-iniciais.senha-atendente:}") String senhaAtendente) {
        this.perfis = perfis;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.senhaAdmin = senhaAdmin;
        this.senhaAtendente = senhaAtendente;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Perfil admin = criarPerfil(Perfil.ADMIN);
        Perfil atendente = criarPerfil(Perfil.ATENDENTE);
        // Papéis iniciais das contas criadas nos cadastros de médico e de paciente.
        criarPerfil(Perfil.MEDICO);
        criarPerfil(Perfil.PACIENTE);

        // A administração também atende: marca, cancela e confirma agendamentos.
        criarUsuario("admin", senhaAdmin, admin, atendente);
        criarUsuario("atendente", senhaAtendente, atendente);
    }

    private Perfil criarPerfil(String nome) {
        return perfis.findByNome(nome).orElseGet(() -> perfis.save(new Perfil(nome)));
    }

    private void criarUsuario(String login, String senha, Perfil... papeis) {
        if (senha == null || senha.isBlank() || usuarios.existsByLogin(login)) return;

        Usuario usuario = new Usuario(login, passwordEncoder.encode(senha), true);
        for (Perfil perfil : papeis) usuario.adicionarPerfil(perfil);
        usuarios.save(usuario);
        log.info("Conta inicial '{}' criada.", login);
    }
}
