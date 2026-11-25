# Imagem da aplicacao em duas etapas: a primeira compila com o JDK, a segunda so roda
# com o JRE (imagem menor, sem Maven nem codigo-fonte).
#
#   docker build -t clinica .
#
# A aplicacao precisa do PostgreSQL e das variaveis do .env; para subir tudo:
#   docker compose up --build

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Dependencias primeiro: enquanto o pom.xml nao muda, essa camada vem do cache do Docker.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Os testes rodam no CI (GitHub Actions); aqui so empacota.
RUN ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/aula1-0.0.1-SNAPSHOT.jar extract --layers --launcher --destination target/extraido

FROM eclipse-temurin:25-jre
WORKDIR /app

# A aplicacao nao roda como root.
RUN groupadd --system clinica && useradd --system --gid clinica clinica \
    && mkdir logs && chown clinica:clinica logs

# Camadas do Spring Boot, da que menos muda (bibliotecas) para a que mais muda (classes).
COPY --from=build /app/target/extraido/dependencies/ ./
COPY --from=build /app/target/extraido/spring-boot-loader/ ./
COPY --from=build /app/target/extraido/snapshot-dependencies/ ./
COPY --from=build /app/target/extraido/application/ ./

USER clinica
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
