# Deploy inicial e rollback

## Contrato do ambiente

A aplicação é distribuída como imagem OCI e deve ser executada com o perfil `prod`. O ambiente precisa fornecer:

| Variável | Obrigatória | Finalidade |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE=prod` | sim | ativa as proteções de produção |
| `DB_URL` | sim | URL JDBC do PostgreSQL |
| `DB_USERNAME` | sim | usuário do banco |
| `DB_PASSWORD` | sim | segredo do banco |
| `PORT` | não | porta HTTP; padrão `8080` |
| `DB_MAX_POOL_SIZE` | não | máximo de conexões; padrão `10` |
| `DB_MIN_IDLE` | não | conexões ociosas mínimas; padrão `2` |

O PostgreSQL deve estar acessível antes do start. O Flyway aplica migrações aditivas automaticamente e o Hibernate somente valida o resultado.

## Primeiro ambiente: Render + Neon

O primeiro ambiente de desenvolvimento usa:

- Render Web Service com runtime Docker e branch `development`;
- Neon com PostgreSQL 18;
- região AWS US East 2 (Ohio) nos dois serviços;
- health check em `/actuator/health/readiness`.

No Neon, use a conexão direta, sem o sufixo `-pooler` no host. A aplicação executa o Flyway durante a inicialização e o Neon recomenda conexão direta para ferramentas de migração de esquema. A conexão deve usar TLS.

Cadastre no Render:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<host-direto-do-neon>/<banco>?sslmode=require
DB_USERNAME=<role-do-neon>
DB_PASSWORD=<senha-do-neon>
```

Não cadastre `PORT`: o Render fornece esse valor e a aplicação o utiliza automaticamente. Credenciais reais pertencem somente aos segredos do Render e nunca devem ser adicionadas ao repositório.

## Publicação

1. Aguarde o workflow `Backend CI` verde para o commit candidato.
2. Confirme que o Render está acompanhando a branch `development` com runtime Docker.
3. Forneça as variáveis de produção como segredos do serviço.
4. Aguarde o Render construir o `Dockerfile` e iniciar a revisão correspondente ao SHA do commit.
5. Aguarde `GET /actuator/health/readiness` responder `200` e `UP`.
6. Execute smoke tests em `/api/v1/platform` e `/v3/api-docs`.
7. Registre SHA, identificador do deploy e versão do Flyway na entrega.

A imagem permanece independente do provedor. Render e Neon são o primeiro ambiente escolhido, mas a aplicação continua portável para qualquer plataforma compatível com contêiner e PostgreSQL.

## Rollback

1. Interrompa novas promoções e identifique a última tag de imagem saudável.
2. Confirme se as migrações do release problemático são retrocompatíveis com essa imagem.
3. Reaponte o serviço para a tag saudável anterior, sem reconstruí-la.
4. Aguarde o readiness e repita os smoke tests.
5. Preserve logs e o `X-Correlation-ID` das falhas para análise.

Migrações destrutivas exigirão uma estratégia expand/contract em fases futuras. Não se deve executar `flyway clean`, apagar volume ou reverter esquema automaticamente durante rollback de aplicação.

## Critério para o primeiro ambiente

O deploy é aceito apenas quando a API inicia com o perfil `prod`, conecta ao PostgreSQL 18 no Neon, conclui o Flyway, responde ao readiness e passa nos dois smoke tests. Até que o serviço e o banco sejam criados e esses passos sejam executados, esta documentação representa a preparação do deploy, não uma publicação concluída.
