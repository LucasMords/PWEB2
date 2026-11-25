package br.edu.ifto.aula1.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;

/**
 * A conta que se autenticou, com uma impressão (SHA-256) do hash conferido. O Spring Security
 * apaga o hash depois de autenticar; a impressão permite saber se a senha mudou nesse meio
 * tempo, sem guardar o hash na sessão.
 */
public class ContaAutenticada extends User {

    private final String impressao;

    public ContaAutenticada(String login, String hash, boolean ativa, boolean bloqueada,
                            Collection<? extends GrantedAuthority> perfis) {
        super(login, hash, ativa, true, true, !bloqueada, perfis);
        this.impressao = impressao(hash);
    }

    /** O hash informado é o mesmo com que a conta se autenticou? */
    public boolean autenticouCom(String hash) {
        return impressao.equals(impressao(hash));
    }

    private static String impressao(String hash) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(hash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
