package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Departamento;
import br.edu.ifto.aula1.Model.Entity.Funcionario;
import br.edu.ifto.aula1.Model.Jdbc.Repository.DepartamentoRepository;
import br.edu.ifto.aula1.Model.Jdbc.Repository.FuncionarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Transactional
@Controller
@RequestMapping("funcionarios")
public class FuncionarioController {

    @Autowired
    FuncionarioRepository repository;

    @Autowired
    DepartamentoRepository departamentoRepository;

    @GetMapping("/form")
    public ModelAndView form(ModelMap model){
        Funcionario funcionario = new Funcionario();
        funcionario.setDepartamento(new Departamento());
        model.addAttribute("funcionario", funcionario);
        adicionarDepartamentos(model);
        return new ModelAndView("funcionario/form", model);
    }

    @GetMapping("/list")
    public ModelAndView listar(ModelMap model) {
        model.addAttribute("funcionarios", repository.listarfuncionarios());
        return new ModelAndView("funcionario/list", model);
    }

    @PostMapping("/save")
    public ModelAndView save(Funcionario funcionario){
        associarDepartamentoGerenciado(funcionario);
        repository.save(funcionario);
        return new ModelAndView("redirect:/funcionarios/list");
    }

    @GetMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id){
        repository.remove(id);
        return new ModelAndView("redirect:/funcionarios/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        model.addAttribute("funcionario", repository.buscarFuncionario(id));
        adicionarDepartamentos(model);
        return new ModelAndView("funcionario/form", model);
    }

    @PostMapping("/update")
    public ModelAndView update(Funcionario funcionario) {
        associarDepartamentoGerenciado(funcionario);
        repository.update(funcionario);
        return new ModelAndView("redirect:/funcionarios/list");
    }

    private void adicionarDepartamentos(ModelMap model) {
        model.addAttribute("departamentos", departamentoRepository.listar());
    }

    private void associarDepartamentoGerenciado(Funcionario funcionario) {
        Departamento departamento = funcionario.getDepartamento();
        if (departamento == null || departamento.getId() == null) {
            throw new IllegalArgumentException("Um departamento deve ser selecionado.");
        }

        Departamento departamentoGerenciado = departamentoRepository.buscarPorId(departamento.getId());
        if (departamentoGerenciado == null) {
            throw new IllegalArgumentException("Departamento inválido.");
        }
        funcionario.setDepartamento(departamentoGerenciado);
    }

}
