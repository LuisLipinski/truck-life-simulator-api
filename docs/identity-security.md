# Contrato de identidade, sessão e segurança

Status: baseline da P2, versão 1, aprovada para orientar migrations, implementação e testes.

## Objetivo e limites

O módulo `identity` é o único proprietário de usuários, credenciais, sessões e tokens de ação. Outros módulos recebem apenas a identidade autenticada e não acessam hashes, refresh tokens ou detalhes internos desse módulo.

Premium não é papel de autorização. Os papéis iniciais são `USER` e `ADMIN`; plano e direitos de uso continuarão sob o módulo `subscription`.

## Conta

- Identificador: UUID.
- E-mail: obrigatório, trimado, normalizado em minúsculas e único após normalização.
- Nome de exibição: opcional no banco, obrigatório no cadastro, entre 2 e 120 caracteres.
- Estados: `PENDING_VERIFICATION`, `ACTIVE`, `LOCKED` e `DISABLED`.
- Papel padrão: `USER`.
- Uma conta nova permanece `PENDING_VERIFICATION` até confirmar o e-mail.
- Não haverá bloqueio permanente baseado apenas em tentativas de login, para evitar negação de serviço contra uma conta. O controle inicial será por rate limit progressivo.

## Senha

- Comprimento mínimo: 12 caracteres.
- Comprimento máximo: 128 caracteres, rejeitado explicitamente; nunca truncado.
- Espaços, colagem e Unicode são permitidos.
- Não serão exigidas regras arbitrárias de composição como uma maiúscula, um número e um símbolo.
- O valor bruto só existe durante a requisição e nunca é persistido ou registrado.
- O hash usa `DelegatingPasswordEncoder` com Argon2id como algoritmo novo.
- Baseline Argon2id: memória mínima de 19 MiB, 2 iterações e paralelismo 1; os parâmetros devem ser calibrados no Render e versionados no hash.
- A aplicação pode atualizar o hash após um login válido quando `upgradeEncoding` indicar parâmetros antigos.

## Sessão

### Access token

- Formato: JWT assinado.
- Duração padrão: 10 minutos.
- Transporte: resposta JSON e header `Authorization: Bearer <token>`.
- Armazenamento frontend: somente memória; nunca `localStorage` ou `sessionStorage`.
- Claims mínimas: `sub` (UUID), `sid` (UUID da sessão), `role`, `email_verified`, `iss`, `aud`, `iat`, `exp` e `jti`.
- Algoritmo inicial: HS256 com segredo aleatório de pelo menos 256 bits, fornecido em Base64 por variável de ambiente.
- O logout revoga o refresh token imediatamente. Um access token já emitido pode permanecer válido por até 10 minutos, limite aceito nesta fase.

### Refresh token

- Formato: valor opaco com 256 bits aleatórios, codificado em Base64 URL-safe.
- Duração padrão: 30 dias.
- O banco armazena apenas SHA-256 do valor aleatório.
- Cada refresh rotaciona o token, invalida o anterior e preserva a relação da família.
- Reutilizar um token já rotacionado revoga toda a família da sessão.
- Alteração ou redefinição de senha revoga todas as sessões do usuário.
- O token bruto nunca aparece em logs, métricas, URLs ou resposta de erro.

### Cookie e compatibilidade do ambiente

O refresh token será enviado por cookie `HttpOnly`, `Secure`, `SameSite=None`, sem atributo `Domain` e com caminho `/api/v1/auth`. O frontend usa `credentials: "include"`.

GitHub Pages e Render pertencem a sites diferentes. Mesmo com CORS correto e `SameSite=None`, políticas de cookies de terceiros podem bloquear a sessão em alguns navegadores. Antes de concluir a P2, o fluxo deve ser validado em Chrome/Android, Firefox e Safari. Se não for confiável, a solução obrigatória é publicar frontend e API em subdomínios do mesmo domínio registrável; não será adotado refresh token em `localStorage` como atalho.

## CSRF e CORS

- Origens permitidas vêm de `AUTH_ALLOWED_ORIGINS`; curingas são proibidos quando credenciais estão habilitadas.
- Requests com cookie aceitam somente `Origin` presente na allowlist.
- `POST /refresh` e `POST /logout` exigem token CSRF no header `X-CSRF-TOKEN`.
- `GET /api/v1/auth/csrf` cria/renova o token CSRF e o devolve no corpo para a origem permitida.
- Métodos, headers e credenciais CORS são declarados de forma explícita.
- Toda comunicação externa exige HTTPS; localhost é a única exceção de desenvolvimento.

## Endpoints e comportamento

| Endpoint | Sucesso | Regra principal |
| --- | --- | --- |
| `GET /api/v1/auth/csrf` | `200` | devolve token CSRF para origem permitida |
| `POST /api/v1/auth/register` | `202` | resposta neutra; cria conta pendente quando elegível |
| `POST /api/v1/auth/verify-email` | `204` | consome token de uso único |
| `POST /api/v1/auth/resend-verification` | `202` | resposta neutra e rate limit |
| `POST /api/v1/auth/login` | `200` | devolve access token e instala refresh cookie |
| `POST /api/v1/auth/refresh` | `200` | rotaciona refresh e devolve novo access token |
| `POST /api/v1/auth/logout` | `204` | idempotente; revoga sessão e limpa cookie |
| `POST /api/v1/auth/forgot-password` | `202` | resposta neutra independentemente da conta |
| `POST /api/v1/auth/reset-password` | `204` | troca senha e revoga todas as sessões |
| `GET /api/v1/me` | `200` | devolve apenas a conta autenticada |

Login com e-mail ou senha incorretos sempre devolve o mesmo `401`. Se a senha estiver correta e a conta ainda não estiver verificada, a API devolve `403` com o código estável `EMAIL_NOT_VERIFIED`.

Erros seguem Problem Details e incluem `type`, `title`, `status`, `detail`, `instance`, `code` e `correlationId`. O `detail` nunca revela se um e-mail está cadastrado nos fluxos públicos de cadastro e recuperação.

## Tokens de ação e e-mail

- Verificação de e-mail: validade padrão de 24 horas.
- Redefinição de senha: validade padrão de 30 minutos.
- Tokens: 256 bits aleatórios, uso único, armazenados somente por SHA-256.
- Um novo token invalida tokens ativos anteriores da mesma finalidade.
- Links usam fragmento ou parâmetro somente no frontend; o frontend envia o token no corpo do POST e evita que ele apareça em logs do backend.
- Produção nunca devolve o token bruto no JSON.
- O envio passa por uma interface do módulo `identity`. O adaptador real será escolhido antes da validação externa; testes usam fake e ambiente local pode usar capturador controlado sem registrar o token em log comum.

## Persistência mínima

### `users`

- `id UUID PRIMARY KEY`
- `email VARCHAR(320) NOT NULL`
- `normalized_email VARCHAR(320) NOT NULL UNIQUE`
- `password_hash VARCHAR(255) NOT NULL`
- `display_name VARCHAR(120) NOT NULL`
- `status VARCHAR(30) NOT NULL`
- `role VARCHAR(30) NOT NULL DEFAULT 'USER'`
- `email_verified BOOLEAN NOT NULL DEFAULT FALSE`
- `email_verified_at TIMESTAMPTZ`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`
- `last_login_at TIMESTAMPTZ`
- constraint garantindo coerência entre `email_verified` e `email_verified_at`

### `refresh_tokens`

- `id UUID PRIMARY KEY`
- `user_id UUID NOT NULL REFERENCES users(id)`
- `family_id UUID NOT NULL`
- `parent_id UUID REFERENCES refresh_tokens(id)`
- `replaced_by_id UUID REFERENCES refresh_tokens(id)`
- `token_hash CHAR(64) NOT NULL UNIQUE`
- `issued_at TIMESTAMPTZ NOT NULL`
- `expires_at TIMESTAMPTZ NOT NULL`
- `revoked_at TIMESTAMPTZ`
- `reuse_detected_at TIMESTAMPTZ`
- `ip_address VARCHAR(64)`
- `user_agent VARCHAR(500)`

### `user_action_tokens`

- `id UUID PRIMARY KEY`
- `user_id UUID NOT NULL REFERENCES users(id)`
- `purpose VARCHAR(30) NOT NULL`
- `token_hash CHAR(64) NOT NULL UNIQUE`
- `expires_at TIMESTAMPTZ NOT NULL`
- `used_at TIMESTAMPTZ`
- `created_at TIMESTAMPTZ NOT NULL`

Migrations são somente aditivas. Nenhuma migration da P2 remove ou renomeia objetos já publicados.

## Rate limit inicial

Os valores serão configuráveis, com baseline:

- cadastro: 5 tentativas por hora por IP;
- login: 10 tentativas por 15 minutos por IP e por hash do e-mail normalizado;
- refresh: 30 por minuto por sessão;
- reenvio de verificação: 3 por hora por conta/IP;
- recuperação: 3 por hora por conta/IP;
- reset: 5 tentativas por 30 minutos por token/IP.

A resposta é `429` com `Retry-After`. Chaves de rate limit nunca armazenam o e-mail bruto.

## Variáveis de ambiente

| Variável | Obrigatória em produção | Finalidade |
| --- | --- | --- |
| `AUTH_JWT_SECRET_BASE64` | sim | segredo HS256 com pelo menos 256 bits |
| `AUTH_JWT_ISSUER` | sim | emissor aceito |
| `AUTH_JWT_AUDIENCE` | sim | audiência aceita |
| `AUTH_ALLOWED_ORIGINS` | sim | origens exatas separadas por vírgula |
| `AUTH_ACCESS_TOKEN_TTL` | não | padrão `PT10M` |
| `AUTH_REFRESH_TOKEN_TTL` | não | padrão `P30D` |
| `AUTH_EMAIL_VERIFICATION_TTL` | não | padrão `PT24H` |
| `AUTH_PASSWORD_RESET_TTL` | não | padrão `PT30M` |
| `APP_FRONTEND_BASE_URL` | sim | base dos links enviados por e-mail |

Variáveis específicas do provedor de e-mail serão documentadas somente depois da escolha do adaptador.

## Testes obrigatórios

- cadastro, normalização e concorrência de e-mail único;
- hash e upgrade de senha;
- login válido, inválido, conta pendente, bloqueada e desabilitada;
- assinatura, issuer, audience e expiração do JWT;
- refresh válido, expirado, revogado, rotacionado e reutilizado;
- logout idempotente;
- verificação e reset com token válido, expirado, usado e substituído;
- revogação global após redefinição de senha;
- CORS permitido/negado, CSRF ausente/inválido e cookie seguro;
- rate limit e `Retry-After`;
- isolamento: usuário A nunca acessa dados do usuário B;
- migrations em PostgreSQL 18 com Testcontainers;
- ausência de senha, token ou código nos logs capturados.

## Referências

- [Spring Security — Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [OWASP — Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [RFC 9700 — OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700.html)
- [MDN — Cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Cookies)
- [MDN — CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS)
