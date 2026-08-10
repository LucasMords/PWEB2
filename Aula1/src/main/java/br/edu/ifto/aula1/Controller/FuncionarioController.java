package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Funcionario;
import br.edu.ifto.aula1.Model.Jdbc.dao.FuncionarioDao;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("funcionarios")
public class FuncionarioController {

    FuncionarioDao dao;

    public FuncionarioController(){
        dao = new FuncionarioDao();
    }

    @GetMapping("/form")
    public ModelAndView form(ModelMap model){
        model.addAttribute("funcionario", new Funcionario());
        return new ModelAndView("funcionario/form", model);
    }

    @GetMapping("/list")
    public ModelAndView listar(ModelMap model) {
        model.addAttribute("funcionarios", dao.listarfuncionarios());
        return new ModelAndView("funcionario/list", model);
    }

    @PostMapping("/save")
    public ModelAndView save(Funcionario funcionario){
        dao.save(funcionario);
        return new ModelAndView("redirect:/funcionarios/list");
    }

    @GetMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id){
        dao.remove(id);
        return new ModelAndView("redirect:/funcionarios/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        model.addAttribute("funcionario", dao.buscarFuncionario(id));
        return new ModelAndView("funcionario/form", model);
    }

    @PostMapping("/update")
    public ModelAndView update(Funcionario funcionario) {
        dao.update(funcionario);
        return new ModelAndView("redirect:/funcionarios/list");
    }

}

