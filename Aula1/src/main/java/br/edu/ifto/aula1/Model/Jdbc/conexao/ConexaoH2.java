package br.edu.ifto.aula1.Model.Jdbc.conexao;

import br.edu.ifto.aula1.Model.Jdbc.conexao.ConexaoJDBC;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConexaoH2 implements ConexaoJDBC {


    public static void main(String[] args) {

        System.out.println(new ConexaoH2().criarConexao());
    }

    @Override
    public Connection criarConexao() {
        try {
            //carregar o driver de conexão
            Class.forName("org.h2.Driver");
            //parâmetros
            String url = "jdbc:h2:mem:dbname";
            String usuario = "user";
            String senha = "password";
            //retorna a conexão com o banco de dados
            return DriverManager.getConnection(url, usuario, senha);

        } catch (ClassNotFoundException | SQLException ex) {
            Logger.getLogger(ConexaoH2.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
