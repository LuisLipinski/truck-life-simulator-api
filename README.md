# Truck Life Simulator API

Backend do Truck Life Simulator para sustentar, em uma única plataforma, carreiras do American Truck Simulator e do Euro Truck Simulator 2.

A fundação técnica da P1 está concluída e o backend evolui atualmente pela P2, responsável por identidade, autenticação, sessão e segurança. O projeto segue como um monólito modular, com PostgreSQL, migrações versionadas, contratos HTTP documentados, testes automatizados, cobertura monitorada e imagem OCI pronta para publicação. As demais regras funcionais continuam sendo migradas do frontend módulo a módulo nas próximas fases.

## Stack

- Java 25 LTS
- Spring Boot 4.1.1
- Maven 3.9.16 via Maven Wrapper 3.3.4
- PostgreSQL 18
- Flyway
- OpenAPI/Swagger UI
- JUnit 6, MockMvc, RestTestClient, ArchUnit e Testcontainers
- JaCoCo
- Docker/OCI e GitHub Actions
- CodeQL e Dependency Review

## Executar localmente

Pré-requisitos: JDK 25 e Docker com Compose. Não é necessário instalar Maven separadamente: o repositório fixa a versão usada por meio do Maven Wrapper.

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

No Windows, use `mvnw.cmd spring-boot:run`.

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
./mvnw verify
```

No Windows, use `mvnw.cmd verify`.

A validação requer Docker porque os testes de integração criam instâncias PostgreSQL descartáveis. A suíte verifica, entre outros pontos:

- contratos HTTP e erros no formato Problem Details;
- autenticação, sessão, recuperação de senha e isolamento de conta;
- persistência real, restrições e migrações Flyway;
- propagação do `X-Correlation-ID`;
- limites arquiteturais e ausência de ciclos entre módulos;
- inicialização completa da aplicação;
- endpoints de health e OpenAPI;
- limites mínimos de cobertura do JaCoCo.

O relatório HTML de cobertura é gerado em `target/site/jacoco/index.html` após o `verify`.

### Política de cobertura

A cobertura é um indicador de risco, não um substituto para testes de comportamento. O gate automatizado impede regressões abaixo de:

- **85% de linhas**;
- **60% de branches**.

A meta de qualidade do projeto é manter:

- **90% ou mais de linhas**;
- **70% ou mais de branches**.

Novos testes devem priorizar regras de negócio, segurança, caminhos alternativos e tratamento de falhas em vez de aumentar percentuais artificialmente.

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

Alguns módulos ainda contêm somente o contrato de pacote porque serão implementados nas fases seguintes. Eles preservam os limites arquiteturais planejados e não devem ser tratados como código gerado ou descartável.

`platform` contém capacidades técnicas expostas pela fundação e `shared` mantém somente primitivas realmente transversais. Consulte [docs/architecture.md](docs/architecture.md) para as regras de evolução.

## Identidade e segurança

A P2 cobre contrato de conta, senha, verificação de e-mail, access/refresh token, rotação, revogação, CORS, CSRF, recuperação de senha e consulta segura da conta autenticada. Consulte [docs/identity-security.md](docs/identity-security.md) antes de alterar o módulo `identity` ou criar migrations relacionadas.

O ciclo de sessão expõe `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` e `POST /api/v1/auth/logout`. O access token é um JWT curto; o refresh token opaco permanece em cookie `HttpOnly`, é persistido somente por hash e rotaciona a cada uso. Clientes web devem obter o token antifalsificação em `GET /api/v1/auth/csrf` antes de chamar refresh ou logout.

## Perfis

| Perfil | Uso | Configuração sensível |
| --- | --- | --- |
| `local` | desenvolvimento com Compose | aceita padrões locais ou variáveis `DB_*` |
| `test` | testes automatizados | conexão injetada pelo Testcontainers |
| `prod` | ambiente publicado | exige banco, segredo/identidade JWT e origens CORS por variáveis de ambiente |

O Hibernate usa `validate`; somente o Flyway altera o esquema.

Use [`.env.example`](.env.example) somente como referência para os nomes das variáveis. O arquivo `.env` real e credenciais do Neon/Resend não devem ser versionados; no Render, cadastre os valores como segredos do serviço.

## Branches e integração

- `master`: versão estável e referência de rollback;
- `development`: ambiente integrado usado para validação funcional;
- `feature/*`, `fix/*` e `hotfix/*`: mudanças isoladas, integradas por pull request em `development` após CI verde.

O workflow `.github/workflows/ci.yml` executa o build pelo Maven Wrapper, roda testes, verifica o gate de cobertura, empacota o JAR, publica o relatório JaCoCo como artefato e valida a construção da imagem OCI em pull requests e pushes de `development` e `master`.

O CodeQL executa análise estática de segurança e o Dependency Review analisa alterações de dependências em pull requests. Para que o Dependency Review opere, o `Dependency graph` do repositório deve permanecer habilitado nas configurações de segurança do GitHub.

## Deploy e rollback

A imagem é independente do provedor e o perfil `prod` recebe toda configuração sensível por variáveis de ambiente. A preparação do ambiente e o procedimento de rollback estão em [docs/deployment.md](docs/deployment.md).
