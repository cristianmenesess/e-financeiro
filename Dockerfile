FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
# Os testes rodam fora do Docker (precisam de Docker pro Postgres do Testcontainers)
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Usuário sem privilégios: se a aplicação for comprometida, o invasor não é root no container
RUN groupadd --system efinanceiro && useradd --system --gid efinanceiro --no-create-home efinanceiro
COPY --from=build --chown=efinanceiro:efinanceiro /app/build/libs/*.jar app.jar
USER efinanceiro
EXPOSE 8080
# MaxRAMPercentage: heap proporcional à memória do container (512 MB no free tier do Render),
# deixando espaço pra metaspace/threads em vez de o processo ser morto por falta de memória
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]
