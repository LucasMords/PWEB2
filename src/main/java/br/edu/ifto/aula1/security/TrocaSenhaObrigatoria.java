package br.edu.ifto.aula1.security;

import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Quem entrou com uma senha provisória (redefinida pela administração) só acessa a página
 * de troca de senha até escolher a própria.
 */
@Configuration
public class TrocaSenhaObrigatoria implements WebMvcConfigurer {

    public static final String PAGINA = "/usuario/senha";

    private final UsuarioRepository usuarios;

    public TrocaSenhaObrigatoria(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                if (request.getUserPrincipal() == null
                        || !usuarios.existsByLoginAndTrocarSenhaTrue(request.getUserPrincipal().getName()))
                    return true;
                response.sendRedirect(request.getContextPath() + PAGINA);
                return false;
            }
        }).excludePathPatterns(PAGINA, "/login", "/error", "/privacidade", "/css/**", "/js/**", "/images/**");
    }
}
