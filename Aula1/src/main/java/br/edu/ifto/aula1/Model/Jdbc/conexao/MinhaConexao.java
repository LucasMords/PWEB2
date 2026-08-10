package br.edu.ifto.aula1.Model.Jdbc.conexao;

import java.sql.Connection;

public class MinhaConexao {

    public static Connection conexao(){
        ConexaoJDBC conexao = new ConexaoH2();
        return conexao.criarConexao();
    }
}
