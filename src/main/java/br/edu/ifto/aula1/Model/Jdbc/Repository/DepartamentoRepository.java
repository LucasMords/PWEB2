package br.edu.ifto.aula1.Model.Jdbc.Repository;

import br.edu.ifto.aula1.Model.Entity.Departamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DepartamentoRepository {

    @PersistenceContext
    private EntityManager em;

    public List<Departamento> listar() {
        return em.createQuery("from Departamento order by nome", Departamento.class)
                .getResultList();
    }

    public Departamento buscarPorId(Long id) {
        return em.find(Departamento.class, id);
    }
}
