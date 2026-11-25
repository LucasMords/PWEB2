package br.edu.ifto.aula1.service;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.PerfilRepository;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Troca e redefinição de senha, com os repositórios e o codificador simulados. */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Long ID = 7L;
    private static final String LOGIN = "conta.teste";
    private static final LocalDateTime AGORA = LocalDateTime.of(2030, 1, 10, 10, 0);

    @Mock private UsuarioRepository usuarios;
    @Mock private PerfilRepository perfis;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditoriaService auditoria;
    @InjectMocks private UsuarioService service;

    private Usuario conta;

    @BeforeEach
    void contaExistente() {
        conta = new Usuario(LOGIN, "hash-antigo", true);
        ReflectionTestUtils.setField(conta, "id", ID);
        lenient().when(usuarios.findById(ID)).thenReturn(Optional.of(conta));
        lenient().when(usuarios.findByLogin(LOGIN)).thenReturn(Optional.of(conta));
        lenient().when(passwordEncoder.encode(anyString())).thenAnswer(c -> "hash:" + c.getArgument(0));
    }

    @Test
    void trocarGravaOHashTiraAMarcaDeProvisoriaEAudita() {
        conta.redefinirSenha("hash-provisorio");

        service.trocarSenha(LOGIN, "Segunda-5678");

        assertThat(conta.getPassword()).isEqualTo("hash:Segunda-5678");
        assertThat(conta.isTrocarSenha()).isFalse();
        verify(auditoria).registrar(AcaoAuditoria.SENHA_ALTERADA, "Usuario", ID, null);
        verify(usuarios, never()).save(any());
    }

    @Test
    void redefinirSorteiaProvisoriaDesbloqueiaEAuditaSemASenha() {
        conta.registrarFalha(AGORA, 1, Duration.ofMinutes(15));
        assertThat(conta.isBloqueadoEm(AGORA)).isTrue();

        UsuarioService.SenhaProvisoria provisoria = service.redefinirSenha(ID, "admin");

        assertThat(provisoria.login()).isEqualTo(LOGIN);
        assertThat(conta.getPassword()).isEqualTo("hash:" + provisoria.senha());
        assertThat(conta.isTrocarSenha()).isTrue();
        assertThat(conta.isBloqueadoEm(AGORA)).isFalse();
        verify(auditoria).registrar(AcaoAuditoria.SENHA_REDEFINIDA, "Usuario", ID, "Conta " + LOGIN);
    }

    @Test
    void provisoriaAtendeAPoliticaSemCaracteresAmbiguosENaoSeRepete() {
        for (int i = 0; i < 200; i++)
            assertThat(service.redefinirSenha(ID, "admin").senha())
                    .matches("(?=.*[A-Za-z])(?=.*\\d)[A-HJKMNP-Za-km-z2-9]{12}");
        assertThat(service.redefinirSenha(ID, "admin").senha())
                .isNotEqualTo(service.redefinirSenha(ID, "admin").senha());
    }

    @Test
    void naoRedefineAPropriaSenhaNemContaInexistente() {
        when(usuarios.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinirSenha(ID, LOGIN)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.redefinirSenha(99L, "admin"))
                .isInstanceOf(RegistroNaoEncontradoException.class);

        assertThat(conta.getPassword()).isEqualTo("hash-antigo");
        verifyNoInteractions(auditoria);
    }

    @Test
    void confereASenhaPeloHash() {
        when(passwordEncoder.matches("certa", "hash-antigo")).thenReturn(true);

        assertThat(service.senhaConfere(LOGIN, "certa")).isTrue();
        assertThat(service.senhaConfere(LOGIN, "errada")).isFalse();
    }
}
