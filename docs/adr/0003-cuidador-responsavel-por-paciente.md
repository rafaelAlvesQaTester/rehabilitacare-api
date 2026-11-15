# ADR 0003: Um cuidador responsável por paciente nesta versão

- **Status:** Aceita
- **Data:** 2026-11-15

## Contexto

A primeira versão precisa permitir que um cuidador cadastre e acompanhe pacientes sem expor os dados a outras contas. Compartilhamento entre cuidadores ou equipes não faz parte do escopo atual.

## Decisão

Cada paciente terá um único cuidador responsável, ligado por chave estrangeira ao cadastro de cuidadores. A identidade usada em criação, listagem, consulta e atualização será derivada do `sub` do JWT validado. As consultas de leitura e atualização filtram simultaneamente pelo UUID do paciente e pelo UUID do cuidador autenticado. O identificador do cuidador não é editável por requisição.

## Consequências

- Pacientes de contas diferentes permanecem isolados; um registro inexistente e um registro de outro cuidador retornam a mesma resposta `404`.
- A listagem é paginada, com tamanho padrão de 20 e máximo de 50.
- A modelagem não oferece compartilhamento, múltiplos responsáveis ou acesso multidisciplinar. Uma futura mudança exigirá modelagem e autorização próprias, sem enfraquecer o isolamento existente.
