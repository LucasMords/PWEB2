package br.edu.ifto.aula1.Model.Jdbc.dao;

import br.edu.ifto.aula1.Model.Entity.Funcionario;
import br.edu.ifto.aula1.Model.Jdbc.conexao.MinhaConexao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FuncionarioDao implements FuncionarioDaoInterface {

    Connection con;

    public FuncionarioDao(){
        con = MinhaConexao.conexao();
    }


    @Override
    public List<Funcionario> listarfuncionarios() {
        try {
            String sql = "select * from tb_funcionario";
            PreparedStatement ps = con.prepareStatement(sql);
            //ResultSet, representa o resultado do comando sql
            ResultSet rs = ps.executeQuery();

            List<Funcionario> funcionarios = new ArrayList<>();

            while (rs.next()){
                Funcionario f = new Funcionario();
                f.setId(rs.getLong("id"));
                f.setNome(rs.getString("nome"));
                f.setDepartamento(rs.getString("departamento"));
                f.setSalario(rs.getBigDecimal("salario"));
                funcionarios.add(f);
            }
            return funcionarios;
        }catch (SQLException ex){
            Logger.getLogger(FuncionarioDao.class.getName()).log(Level.SEVERE,null, ex);
        }
        return null;
    }

    @Override
    public boolean remove(Long id) {
        try {
            String sql = "delete from tb_funcionario where id = ?";
            PreparedStatement ps = con.prepareStatement(sql);
            //referênciar o parâmetro do método para a ?
            ps.setLong(1, id);
            if (ps.executeUpdate() == 1)
                return true;

        } catch (SQLException ex) {
            Logger.getLogger(FuncionarioDao.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    @Override
    public boolean save(Funcionario funcionario) {
            try {
                String sql = "insert into tb_funcionario (nome, departamento, salario) values (?, ?, ?)";
                PreparedStatement ps = con.prepareStatement(sql);
                //referênciar o parâmetro do método para a ?
                ps.setString(1, funcionario.getNome());
                ps.setString(2, funcionario.getDepartamento());
                ps.setBigDecimal(3, funcionario.getSalario());

                if(ps.executeUpdate()==1)
                    return true;

            } catch (SQLException ex) {
                Logger.getLogger(FuncionarioDao.class.getName()).log(Level.SEVERE, null, ex);
            }
            return false;
        }

    @Override
    public boolean update(Funcionario funcionario) {
        try {
            String sql = "update tb_funcionario set nome=?, departamento=?, salario=? where id=?";
            PreparedStatement ps = con.prepareStatement(sql);
            //referênciar o parâmetro do método para a ?
            ps.setString(1, funcionario.getNome());
            ps.setString(2, funcionario.getDepartamento());
            ps.setBigDecimal(3, funcionario.getSalario());
            ps.setLong(4, funcionario.getId());

            if (ps.executeUpdate()==1)
                return true;

        } catch (SQLException ex) {
            Logger.getLogger(FuncionarioDao.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    @Override
    public Funcionario buscarFuncionario(Long id) {
        try {
            String sql = "select * from tb_funcionario where id = ?";
            PreparedStatement ps = con.prepareStatement(sql);
            //referênciar o parâmetro do método para a ?
            ps.setLong(1, id);
            //ResultSet, representa o resultado do comando SQL
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Funcionario p = new Funcionario();
                p.setId(rs.getLong("id"));
                p.setNome(rs.getString("nome"));
                p.setDepartamento(rs.getString("departamento"));
                p.setSalario(rs.getBigDecimal("salario"));
                return p;
            }
        } catch (SQLException ex) {
            Logger.getLogger(FuncionarioDao.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
