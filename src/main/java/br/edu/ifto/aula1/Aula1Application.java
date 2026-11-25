package br.edu.ifto.aula1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
public class Aula1Application {

	/** Fuso da clínica (Tocantins). Pode ser trocado pela variável de ambiente CLINICA_FUSO. */
	static final String FUSO_PADRAO = "America/Araguaina";

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(fuso(System.getenv("CLINICA_FUSO"))));
		SpringApplication.run(Aula1Application.class, args);
	}

	/**
	 * Definido antes de subir o Spring, para valer em tudo: regras de horário, validações
	 * (@Past, @FutureOrPresent), conexão com o banco e logs. Sem isso valeria o fuso da
	 * máquina, que num container costuma ser UTC. Fuso inválido impede a subida.
	 */
	static ZoneId fuso(String configurado) {
		return ZoneId.of(configurado == null || configurado.isBlank() ? FUSO_PADRAO : configurado.trim());
	}

}
