package br.edu.ifto.aula1.Model.Jdbc.dao;

import br.edu.ifto.aula1.Model.Entity.Funcionario;

import java.util.List;

public interface FuncionarioDaoInterface {

    public List<Funcionario> listarfuncionarios();
    public boolean remove(Long id);
    public boolean save(Funcionario funcionario);
    public boolean update(Funcionario funcionario);
    public Funcionario buscarFuncionario(Long id);

}
