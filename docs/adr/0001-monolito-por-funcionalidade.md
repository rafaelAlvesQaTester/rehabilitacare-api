# ADR 0001: Monólito organizado por funcionalidade

- **Status:** Aceita
- **Data:** 2026-11-15

## Contexto

O ReabilitaCare começa como uma API REST pequena para apoiar cuidadores. O fluxo funcional será ampliado em etapas, e não há necessidade atual de implantação ou escala independente por componente.

## Decisão

Manter uma única aplicação Spring Boot e organizar o código por áreas funcionais (`auth`, `patient` e `care`), com componentes compartilhados em `shared`. Cada funcionalidade separará controller, serviço e repositório conforme for implementada. Respostas públicas usarão DTOs, não entidades JPA.

## Consequências

- A implantação e a execução local permanecem simples.
- Os limites entre funcionalidades ficam visíveis sem introduzir microsserviços ou uma arquitetura hexagonal completa.
- Cadastro e autenticação de cuidadores com persistência relacional são definidos em [ADR 0002](0002-autenticacao-jwt.md).
- A autorização por cuidador deverá ser aplicada antes de expor operações de pacientes e cuidados.
