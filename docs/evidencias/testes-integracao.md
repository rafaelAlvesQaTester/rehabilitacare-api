# Evidências — Testes automatizados e de integração

**Projeto:** ReabilitaCare API
**Data:** 16/11/2026
**Comando:** `.\mvnw.cmd clean verify`

## Resultados

| Indicador | Resultado |
|---|---|
| Build Maven | BUILD SUCCESS |
| Testes executados | 50 |
| Falhas | 0 |
| Erros | 0 |
| Ignorados | 0 |
| Tempo total | 1 minuto e 51 segundos |
| Checkstyle | 0 violações |
| JaCoCo | Todos os critérios de cobertura atendidos |

## Testes de integração

- JUnit 5 e Spring Boot Test
- Testcontainers 2.0.5
- PostgreSQL 17.12 em containers temporários
- Flyway: 3 migrations aplicadas
- Testes HTTP de autenticação e registros de cuidados
- Banco de testes isolado do banco principal

## Integração contínua

O projeto também possui pipeline automatizado no GitHub Actions.

[Consultar execuções do GitHub Actions](https://github.com/rafaelAlvesQaTester/rehabilitacare-api/actions)

## Observações

Resultados obtidos em ambiente local de desenvolvimento.
Os 50 testes incluem testes unitários e de integração.
O resultado não representa validação em produção.
