package br.edu.ifto.aula1.Model.Jdbc.Repository;

import br.edu.ifto.aula1.Model.Entity.Funcionario;

import java.util.List;

public interface FuncionarioRepositoryInterface {

    public List<Funcionario> listarfuncionarios();
    public void remove(Long id);
    public void save(Funcionario funcionario);
    public void update(Funcionario funcionario);
    public Funcionario buscarFuncionario(Long id);

}
