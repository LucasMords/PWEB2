package br.edu.ifto.aula1.Model.Jdbc.Repository;

import br.edu.ifto.aula1.Model.Entity.Funcionario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository //Define uma conexão com o banco
public class FuncionarioRepository implements FuncionarioRepositoryInterface {

    //gerencia entidades no contexto da persistencia
    @PersistenceContext
    private EntityManager em;


    @Override
    public List<Funcionario> listarfuncionarios() {
        Query query = em.createQuery("from Funcionario"); //HQL
        return query.getResultList();
    }

    @Override
    public void remove(Long id) {
        Funcionario f = em.find(Funcionario.class, id);
        em.remove(f);
    }

    @Override
    public void save(Funcionario funcionario) {
        em.persist(funcionario);
    }

    @Override
    public void update(Funcionario funcionario) {
        em.merge(funcionario);
    }

    @Override
    public Funcionario buscarFuncionario(Long id) {
        return em.find(Funcionario.class, id);
    }
}
