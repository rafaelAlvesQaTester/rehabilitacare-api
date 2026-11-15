# ADR 0002: JWT Bearer para autenticação da API

- **Status:** Aceita
- **Data:** 2026-11-15

## Contexto

A API precisa autenticar cuidadores sem sessão de servidor e identificar o usuário em endpoints protegidos. Cadastro e acesso devem funcionar como API REST.

## Decisão

Emitir JWT Bearer assinado com HS256 após validar o email e a senha. O identificador UUID do cuidador é o `sub`; o token expira em 15 minutos e o decoder valida assinatura, expiração e emissor. A chave simétrica é obrigatória, configurada por `ACCESS_TOKEN_SECRET_BASE64` e deve ter ao menos 32 bytes. Senhas são armazenadas com BCrypt fator 12. O endpoint `me` deriva a identidade exclusivamente do `sub` autenticado e consulta somente contas ativas.

## Consequências

- O cliente envia o token no cabeçalho `Authorization`; endpoints protegidos não dependem de sessão ou cookie.
- A aplicação e qualquer instância que valide tokens precisam compartilhar a chave. Rotação exige planejar chaves simultâneas ou aguardar a expiração dos tokens existentes.
- Não há refresh token nem revogação imediata; tokens de acesso expiram em 15 minutos.
- Respostas para email inexistente, senha incorreta ou conta inativa são iguais. Não há rate limiting nem bloqueio progressivo por conta/IP; essa proteção é necessária antes da exposição pública.
- A chave fictícia do `.env.example` serve apenas para desenvolvimento e nunca deve ser usada em produção.
