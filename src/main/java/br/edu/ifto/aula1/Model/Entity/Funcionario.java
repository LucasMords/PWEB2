package br.edu.ifto.aula1.Model.Entity;

import java.math.BigDecimal;

public class Funcionario {

    private Long id;
    private String nome;
    private String departamento;
    private BigDecimal salario;

    public Funcionario(){}
    public Funcionario(Long id, String nome, String departamento, BigDecimal salario){
        this.id = id;
        this.nome = nome;
        this.departamento = departamento;
        this.salario = salario;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDepartamento() {
        return departamento;
    }

    public void setDepartamento(String departamento) {
        this.departamento = departamento;
    }

    public BigDecimal getSalario() {
        return salario;
    }

    public void setSalario(BigDecimal salario) {
        this.salario = salario;
    }
}

