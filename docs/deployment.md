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

## Publicação

1. Aguarde o workflow `Backend CI` verde para o commit candidato.
2. Construa a imagem usando o SHA do commit como tag imutável.
3. Publique a imagem no registry escolhido.
4. Aponte o ambiente para essa tag e forneça as variáveis de produção.
5. Aguarde `GET /actuator/health/readiness` responder `200` e `UP`.
6. Execute smoke tests em `/api/v1/platform` e `/v3/api-docs`.
7. Registre SHA, tag de imagem e versão do Flyway na entrega.

O provedor e o registry permanecem uma decisão operacional separada; a fundação não prende o projeto a um fornecedor.

## Rollback

1. Interrompa novas promoções e identifique a última tag de imagem saudável.
2. Confirme se as migrações do release problemático são retrocompatíveis com essa imagem.
3. Reaponte o serviço para a tag saudável anterior, sem reconstruí-la.
4. Aguarde o readiness e repita os smoke tests.
5. Preserve logs e o `X-Correlation-ID` das falhas para análise.

Migrações destrutivas exigirão uma estratégia expand/contract em fases futuras. Não se deve executar `flyway clean`, apagar volume ou reverter esquema automaticamente durante rollback de aplicação.

## Critério para o primeiro ambiente

O deploy é aceito apenas quando a API inicia com o perfil `prod`, conecta ao PostgreSQL, conclui o Flyway, responde ao readiness e passa nos dois smoke tests. Até que um provedor seja selecionado e esses passos sejam executados, esta documentação representa a preparação do deploy, não uma publicação concluída.
