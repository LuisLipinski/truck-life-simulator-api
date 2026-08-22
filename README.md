# Truck Life Simulator API

Backend do Truck Life Simulator para sustentar, em uma única plataforma, carreiras do American Truck Simulator e do Euro Truck Simulator 2.

Esta entrega inaugura a P1: a fundação técnica do backend. Ela define os limites do monólito modular, o contrato HTTP inicial, PostgreSQL com migrações versionadas, perfis de execução, testes automatizados e uma imagem OCI pronta para publicação. As regras funcionais continuam no frontend até serem migradas módulo a módulo nas próximas fases.

## Stack

- Java 25 LTS
- Spring Boot 4.1.1
- Maven 3.9+
- PostgreSQL 18
- Flyway
- OpenAPI/Swagger UI
- JUnit 6, MockMvc, RestTestClient, ArchUnit e Testcontainers
- Docker/OCI e GitHub Actions

## Executar localmente

Pré-requisitos: JDK 25, Maven 3.9+ e Docker com Compose.

```bash
docker compose up -d postgres
mvn spring-boot:run
```

A aplicação usa o perfil `local` por padrão. Os valores locais seguros para desenvolvimento estão em `application-local.yml` e podem ser sobrescritos:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/truck_life
export DB_USERNAME=truck_life
export DB_PASSWORD=truck_life
```

Após a inicialização:

- API: `http://localhost:8080/api/v1/platform`
- Health: `http://localhost:8080/actuator/health`
- Readiness: `http://localhost:8080/actuator/health/readiness`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Para interromper somente o banco:

```bash
docker compose down
```

O volume é preservado. Use `docker compose down --volumes` apenas quando quiser apagar intencionalmente os dados locais.

No PostgreSQL 18, a imagem oficial armazena os dados persistentes em `/var/lib/postgresql`; o `compose.yml` já usa esse novo destino.

## Validar

```bash
mvn verify
```

A validação requer Docker porque o teste de integração cria uma instância PostgreSQL descartável. A suíte verifica:

- contratos HTTP e erros no formato Problem Details;
- propagação do `X-Correlation-ID`;
- limites arquiteturais e ausência de ciclos entre módulos;
- inicialização completa da aplicação;
- conexão real com PostgreSQL e execução do Flyway;
- endpoints de health e OpenAPI.

Para validar também a imagem:

```bash
docker build -t truck-life-simulator-api:local .
```

## Monólito modular

O código está dividido por capacidade de negócio, não por camada global:

- `identity`
- `subscription`
- `career`
- `trip`
- `payroll`
- `finance`
- `qualification`
- `incident`
- `backup`
- `audit`

`platform` contém capacidades técnicas expostas pela fundação e `shared` mantém somente primitivas realmente transversais. Consulte [docs/architecture.md](docs/architecture.md) para as regras de evolução.

## Perfis

| Perfil | Uso | Configuração sensível |
| --- | --- | --- |
| `local` | desenvolvimento com Compose | aceita padrões locais ou variáveis `DB_*` |
| `test` | testes automatizados | conexão injetada pelo Testcontainers |
| `prod` | ambiente publicado | exige `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` |

O Hibernate usa `validate`; somente o Flyway altera o esquema.

Use [`.env.example`](.env.example) somente como referência para os nomes das variáveis. O arquivo `.env` real e as credenciais do Neon não devem ser versionados; no Render, cadastre os valores como segredos do serviço.

## Branches e integração

- `master`: versão estável/produção.
- `development`: linha integrada de desenvolvimento.
- `feature/*`: mudanças isoladas, integradas por pull request em `development`.

O workflow `.github/workflows/ci.yml` executa testes, empacota o JAR e valida a construção da imagem em pull requests e nos pushes das branches protegidas.

## Deploy e rollback

A imagem é independente do provedor e o perfil `prod` recebe toda configuração por variáveis de ambiente. A preparação do primeiro ambiente e o procedimento de rollback estão em [docs/deployment.md](docs/deployment.md).
