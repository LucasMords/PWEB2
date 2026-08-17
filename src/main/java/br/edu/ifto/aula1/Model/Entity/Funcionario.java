package br.edu.ifto.aula1.Model.Entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "tb_pessoa")
public class Funcionario implements Serializable {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
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

