# Arquitetura da fundação

## Decisão

O backend começa como um monólito modular. Uma unidade implantável reduz o custo operacional inicial, enquanto limites explícitos por domínio evitam que essa simplicidade se transforme em acoplamento indiscriminado.

Cada módulo será responsável por suas regras, casos de uso, interfaces HTTP e persistência. Um módulo não acessará diretamente repositórios, entidades ou detalhes internos de outro. Quando surgir uma colaboração, ela deverá ocorrer por uma interface pública do módulo proprietário ou por evento interno.

## Módulos de negócio

| Módulo | Responsabilidade inicial |
| --- | --- |
| `identity` | identidade, autenticação e autorização |
| `subscription` | planos, direitos de uso e ciclo da assinatura |
| `career` | carreiras ATS/ETS2 e progressão |
| `trip` | viagens, rotas, entregas e jornada |
| `payroll` | holerites, ganhos, impostos e descontos |
| `finance` | despesas, reservas, ativos e saldos |
| `qualification` | licenças, cursos e qualificações |
| `incident` | incidentes e consequências operacionais |
| `backup` | exportação, importação e recuperação de carreiras |
| `audit` | trilha imutável de eventos de segurança e negócio |

## Suporte técnico

- `platform`: descoberta de capacidades e configurações técnicas da API.
- `shared`: erros, observabilidade e primitivas transversais. Não é um depósito de regras de negócio.

## Regras de dependência

1. O código novo nasce dentro do módulo que possui a regra.
2. Controllers ficam no pacote `api` do módulo proprietário.
3. Entidades e repositórios não atravessam limites de módulo.
4. `shared` não depende de módulos de negócio.
5. Migrações são somente aditivas; uma migração aplicada nunca é editada.
6. Mudanças de contrato HTTP são versionadas sob `/api/v1` e documentadas pelo OpenAPI.
7. O teste ArchUnit impede ciclos nos pacotes de primeiro nível e mantém controllers nos pacotes de API.

## Escopo da P1

A P1 cria os limites e os mecanismos técnicos, mas não replica antecipadamente as entidades do frontend. Cada fase posterior moverá uma fatia vertical completa — contrato, regra, persistência e teste — para evitar duas fontes de verdade permanentes.
