# Testes de desempenho

## Metodologia e segurança

Os testes medem somente leituras da API, sem criar usuários ou pacientes durante a carga:

- `GET /api/v1/health`: esperado `200` e corpo com status `UP`.
- `GET /api/v1/patients?page=0&size=20`: esperado `200` com página vazia para o cuidador autenticado.

Um check só passa quando o status e o conteúdo esperado coincidem. Respostas `401`, `404` ou qualquer status diferente de `200` são falhas, não respostas bem-sucedidas. Os scripts não imprimem tokens nem corpos da API. O token vem de `K6_ACCESS_TOKEN` e não está incluído nos arquivos versionáveis.

A execução usou uma instância Docker Compose exclusiva para esta medição: API publicada somente em `127.0.0.1:18081`, PostgreSQL 17 na rede privada do Compose e volume próprio. A instância preexistente do projeto e seus dados não foram usados nem alterados. O banco de teste continha um cuidador fictício e nenhum paciente. Nenhum dado real foi usado.

## Ambiente medido

| Item | Valor |
|---|---|
| Data e fuso | 15/11/2026, `-03:00` (America/Sao_Paulo) |
| Commit da aplicação | `34841f1` |
| Estado do código | O código da API sob teste correspondia ao commit; scripts e relatório ainda não haviam sido commitados na data da medição |
| k6 | v2.4.0, Windows/amd64 |
| Docker | Engine 29.3.1, containers Linux |
| Aplicação | ReabilitaCare, Java 21, Spring Boot 4.1.2 |
| Maven | Wrapper 3.10.0, JDK 21.0.1 |
| Banco de teste | PostgreSQL 17 |
| Alvo | `http://127.0.0.1:18081` |
| Tipo de ambiente | Local e isolado; não é produção e não é GitHub Actions |

O health check respondeu `200` antes da carga e o PostgreSQL de teste estava saudável. A instância existente na porta `8080` não foi alvo dos cenários.

## Cenários e thresholds

Os dois scripts executam uma iteração de leitura e aguardam um segundo antes de repetir.

| Cenário | Usuários virtuais | Duração configurada | Requisições observadas |
|---|---:|---:|---:|
| Smoke (`tests/performance/smoke.js`) | 2 | 30 s | 120 |
| SLO local (`tests/performance/slo-local.js`) | 20 | 5 min | 11.920 |

Thresholds exigidos em ambos:

- checks com sucesso acima de 99%;
- taxa de falha HTTP abaixo de 1%;
- p95 de duração HTTP abaixo de 500 ms.

## Resultados reais

Os horários abaixo são locais (`-03:00`). As três verificações de threshold passaram nos dois processos k6 (exit code `0`).

| Cenário | Início | Checks | Falhas HTTP | p50 | p90 | p95 | Resultado |
|---|---|---:|---:|---:|---:|---:|---|
| Smoke | 18:59:11 | 120/120 (100%) | 0/120 (0%) | 8,00 ms | 10,96 ms | 12,77 ms | Aprovado |
| SLO local | 18:59:42 | 11.920/11.920 (100%) | 0/11.920 (0%) | 3,07 ms | 5,83 ms | 8,01 ms | Aprovado |

Os cenários observaram, respectivamente, 3,93757 e 39,653875 requisições HTTP por segundo. O p95 permaneceu abaixo de 500 ms em ambos. `dropped_iterations` não foi emitido pelo resumo k6 desta execução; portanto, não foi medido e não se declara zero.

Esses números descrevem apenas as execuções locais, a máquina e o ambiente isolado acima. Não representam capacidade em produção, múltiplas instâncias, rede externa ou carga de usuários real.

## Como reproduzir no PowerShell

Inicie uma instância de desenvolvimento isolada com PostgreSQL 17, crie apenas uma conta fictícia e confirme que sua lista de pacientes está vazia. Não aponte os testes para produção ou para um banco com dados que não sejam descartáveis. Obtenha um JWT dessa conta e injete-o na sessão sem escrevê-lo em arquivo, histórico de comandos ou logs:

```powershell
$env:BASE_URL = 'http://127.0.0.1:18081'
# Defina K6_ACCESS_TOKEN na sessão a partir da conta fictícia, sem exibi-lo.
k6 version
k6 run --env "BASE_URL=$env:BASE_URL" --env "K6_ACCESS_TOKEN=$env:K6_ACCESS_TOKEN" tests/performance/smoke.js
k6 run --env "BASE_URL=$env:BASE_URL" --env "K6_ACCESS_TOKEN=$env:K6_ACCESS_TOKEN" tests/performance/slo-local.js
Remove-Item Env:\K6_ACCESS_TOKEN
```

O comando termina com erro se algum threshold falhar. Proteja também os logs do terminal e não compartilhe tokens. A URL padrão nos scripts aponta para loopback, mas configure `BASE_URL` explicitamente para o ambiente local isolado escolhido.

## Cobertura e CI

Validação local após esta etapa: `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` terminou com exit code `0`; os 50 testes passaram (0 falhas, 0 erros, 0 ignorados), Checkstyle não apontou violações e o gate JaCoCo de 70% passou. A cobertura local de linhas foi **96,61% (427/442 linhas)**. Essa medição é de testes automatizados e não de desempenho.

O workflow do GitHub Actions mantém o gate mínimo de linhas em 70%, escreve o percentual medido no resumo do job e publica XML/HTML como artefato por 14 dias.

O workflow foi atualizado localmente nesta etapa, mas não houve push nem execução do GitHub Actions. Portanto, esta documentação não atribui à CI a cobertura ou os resultados de desempenho locais. Não há resultados de produção.

## Limitações

- Cenário limitado a consultas de saúde e listagem vazia autenticada; não mede criação, histórico com volume de registros, concorrência de escrita ou latência de rede.
- Uma única instância local da API e um banco isolado; os resultados não projetam comportamento em produção.
- O token de teste pertence a uma conta fictícia e deve permanecer fora dos scripts e artefatos versionados.
- `dropped_iterations` não foi informado pelo resumo produzido pelo k6; esse dado permanece não medido.
- Durante a parada do container isolado da API após os testes, foi observado exit code 137 (`OOM=false`). A causa não foi determinada.
- SLOs aqui são thresholds de teste local, não uma garantia de disponibilidade ou desempenho de produção.
