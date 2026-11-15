# Observabilidade e SLO

## Logs e correlação

Os logs de console usam JSON no formato Logstash do Spring Boot. O filtro HTTP aceita `X-Correlation-ID` somente com 1 a 64 caracteres de `[A-Za-z0-9._-]`; se estiver ausente ou for inválido, gera um UUID. O mesmo identificador é devolvido no cabeçalho `X-Correlation-ID` e mantido no MDC durante a requisição.

O evento de conclusão registra somente método HTTP e status. O filtro não registra URI, parâmetros, cabeçalhos de autenticação, corpo, token, senha, identificador de cuidador/paciente ou conteúdo clínico. Não habilite logging de payloads ou SQL com parâmetros sem uma avaliação de privacidade. O MDC anterior da thread é restaurado após a requisição.

## Métricas

O Actuator expõe `health` e `prometheus`. A rota `GET /actuator/prometheus` requer JWT válido; os outros endpoints Actuator não são expostos. A aplicação inclui métricas HTTP do Spring/Micrometer e métricas de JVM. Um bucket de 500 ms é configurado para `http.server.requests`.

O endpoint de métricas não tem uma identidade de monitoramento dedicada nesta etapa. Scrapers devem usar rede privada e credencial protegida; não publique a porta nem coloque tokens em logs. A criação de uma identidade de scraping e alertas gerenciados permanece pendente antes de uso público.

## Metas SLO iniciais

As metas abaixo são propostas de operação, ainda não medidas ou garantidas:

| SLI | SLO inicial | Janela e escopo |
|---|---|---|
| Disponibilidade | Pelo menos 99,5% | Janela móvel de 30 dias, rotas `/api/v1`, excluindo respostas 4xx do denominador |
| Latência | Pelo menos 95% das respostas abaixo de 500 ms | Janela móvel de 30 dias, rotas `/api/v1`, excluindo respostas 4xx |

Com `http_server_requests_seconds_count` e `http_server_requests_seconds_bucket` no Prometheus, as razões correspondentes podem ser calculadas assim:

```promql
sum(rate(http_server_requests_seconds_count{uri=~"/api/v1/.*",status=~"2..|3.."}[30d]))
/
sum(rate(http_server_requests_seconds_count{uri=~"/api/v1/.*",status!~"4.."}[30d]))
```

```promql
sum(rate(http_server_requests_seconds_bucket{uri=~"/api/v1/.*",status!~"4..",le="0.5"}[30d]))
/
sum(rate(http_server_requests_seconds_count{uri=~"/api/v1/.*",status!~"4.."}[30d]))
```

As queries pressupõem tráfego suficiente na janela; sem requisições, o SLI é indefinido. Alertas, error budget automatizado, retenção e armazenamento externo de métricas não estão configurados.
