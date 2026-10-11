package com.marketing.agencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class AgenciaApplication {

	public static void main(String[] args) {
		cargarVariablesDesdeEnv();
		SpringApplication.run(AgenciaApplication.class, args);
	}

	private static void cargarVariablesDesdeEnv() {
		Path archivoEnv = Path.of(".env");
		if (!Files.isRegularFile(archivoEnv)) {
			return;
		}

		try {
			for (String linea : Files.readAllLines(archivoEnv)) {
				String contenido = linea.trim();
				if (contenido.isEmpty() || contenido.startsWith("#")) {
					continue;
				}

				int separador = contenido.indexOf('=');
				if (separador < 1) {
					continue;
				}

				String clave = contenido.substring(0, separador).trim();
				String valor = contenido.substring(separador + 1).trim();
				if (valor.length() >= 2 &&
						((valor.startsWith("\"") && valor.endsWith("\"")) ||
						 (valor.startsWith("'") && valor.endsWith("'")))) {
					valor = valor.substring(1, valor.length() - 1);
				}

				if (!clave.isEmpty() && System.getenv(clave) == null && System.getProperty(clave) == null) {
					System.setProperty(clave, valor);
				}
			}
		} catch (IOException excepcion) {
			throw new IllegalStateException("No se pudo leer el archivo .env", excepcion);
		}
	}

}
