# ADR 0004: Registro imutável de cuidados com autoria autenticada

- **Status:** Aceita
- **Data:** 2026-11-15

## Contexto

O diário precisa manter uma sequência cronológica de eventos informados pelo cuidador, sem recomendações clínicas. Os registros podem conter observações pessoais e devem permanecer isolados por paciente e por cuidador.

## Decisão

- Criar registros somente para pacientes pertencentes ao cuidador ativo identificado pelo `sub` do JWT validado. O serviço confirma a propriedade do paciente; as consultas de histórico e de item filtram também por paciente e cuidador.
- Derivar o cuidador responsável do token, nunca do corpo enviado pelo cliente.
- Receber o horário da atividade com offset ISO 8601 e armazenar o instante em `TIMESTAMPTZ`; gerar `recordedAt` no servidor. Aceitar até dois minutos de tolerância futura para pequenas diferenças de relógio.
- Preservar registros: expor criação e leitura, sem atualização ou exclusão nesta etapa. Não oferecer retificação até existir um mecanismo auditável.
- Paginar e ordenar por horário da atividade decrescente, usando UUID como desempate estável.

## Consequências

- Um paciente inexistente ou não autorizado retorna `404`; um registro é consultado somente no contexto do paciente e cuidador autorizados.
- A autoria fica registrada pelo UUID do cuidador, sem expor dados da conta na resposta.
- A API registra classificações administrativas, não orienta tratamentos, medicamentos, doses ou procedimentos.
- A ausência de retificação limita o uso operacional real até que o fluxo auditável seja definido.
