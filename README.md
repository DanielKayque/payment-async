# Async Payment API

API de pagamentos assíncronos construída para estudar processamento em segundo plano, idempotência, mensageria e consistência entre PostgreSQL e RabbitMQ.

> Projeto educacional. Não movimenta dinheiro real e não recebe dados de cartão.

## Objetivos

- receber uma solicitação de pagamento;
- persistir o pagamento com segurança;
- processar a autorização de forma assíncrona;
- comunicar componentes por RabbitMQ;
- evitar perda de eventos usando Transactional Outbox;
- tratar repetições, falhas e evolução de arquitetura.

## Stack

- Java 25;
- Spring Boot 4.1.2;
- Spring MVC, Spring Data JPA e Bean Validation;
- PostgreSQL 17 e Flyway;
- RabbitMQ 4 com painel de gerenciamento;
- Gradle, Docker Compose e Springdoc OpenAPI.

## Arquitetura atual

```text
Cliente
   │
   ▼
POST /payments
   │
   ▼
PaymentUseCase
   │
   ├── PaymentEntity (PENDING)
   └── OutboxEventEntity (payload JSONB)
          │
          ▼
     OutboxPublisher
          │
          ▼
       RabbitMQ
          │
          ▼
     PaymentConsumer
          │
          ▼
  ProcessPaymentUseCase
          │
          ├── Autorizador externo
          └── APPROVED ou DECLINED
```

O pagamento e o evento da Outbox são salvos na mesma transação PostgreSQL. Um publicador agendado busca eventos pendentes e os envia ao RabbitMQ. O consumidor recebe o evento, busca o pagamento pelo ID e atualiza seu status.

## Fluxo de pagamento

1. O cliente envia um pagamento com `Idempotency-Key`.
2. A API valida o valor e verifica se a chave já foi utilizada.
3. O pagamento é salvo como `PENDING`.
4. Um evento `payment.created` é salvo na tabela `outbox_events`.
5. O `OutboxPublisher` busca eventos ainda não publicados.
6. O payload é publicado no exchange `payment.exchange`.
7. A mensagem chega à fila `payment.processing`.
8. O `PaymentConsumer` recebe um `PaymentCreatedEvent`.
9. O worker consulta o autorizador externo.
10. O pagamento é atualizado para `APPROVED` ou `DECLINED`.

Estados previstos:

```text
PENDING → PROCESSING → APPROVED
                     → DECLINED
```

Falha técnica, como timeout ou indisponibilidade do autorizador, não deve ser tratada como recusa de negócio.

## Transactional Outbox

O padrão Outbox evita o problema de salvar o pagamento e falhar antes de publicar o evento:

```text
Transação PostgreSQL
├── salva PaymentEntity
└── salva OutboxEventEntity
```

A tabela `outbox_events` armazena:

- `aggregate_id`: ID do pagamento relacionado;
- `event_type`: tipo do evento;
- `payload`: dados do evento em `JSONB`;
- `published_at`: momento da publicação;
- `attempts`: número de tentativas.

O `OutboxPublisher` publica eventos com `published_at` nulo e, depois do envio, marca o evento como publicado.

## RabbitMQ

Configuração atual:

```text
Exchange: payment.exchange
Tipo: DirectExchange
Routing key: payment.created
Fila: payment.processing
```

O `RabbitPublisher` publica apenas o payload do evento. A entidade da Outbox permanece como detalhe de persistência e não é utilizada como contrato da mensagem.

## API disponível

### Criar pagamento

```http
POST /payments
Content-Type: application/json
Idempotency-Key: 5fe3c100-71ba-49bc-b38c-bff7c21a9481
```

Corpo atual:

```json
{
  "amountCents": 14990
}
```

Resposta atual:

```json
{
  "amount": 149.90
}
```

O valor é armazenado em centavos no banco para evitar problemas de precisão com valores monetários.

### Idempotência

- a chave é armazenada com um hash da requisição;
- a mesma chave com o mesmo conteúdo não cria outro pagamento;
- a mesma chave com conteúdo diferente gera conflito;
- a constraint única do banco protege contra duplicidade concorrente.

## Como executar

### Pré-requisitos

- Java 25;
- Docker e Docker Compose;
- acesso às portas `5432`, `5672` e `15672`.

### Configurar variáveis de ambiente

Copie `.env-example` para `.env` e ajuste os valores se necessário:

```powershell
Copy-Item .env-example .env
```

Não versione credenciais reais.

### Subir PostgreSQL e RabbitMQ

```powershell
docker compose up -d
docker compose ps
```

Painel do RabbitMQ:

```text
http://localhost:15672
```

Credenciais padrão do ambiente local:

```text
Usuário: guest
Senha: guest
```

### Executar a aplicação

```powershell
.\gradlew.bat bootRun
```

As migrations do Flyway são executadas durante a inicialização.

### Testar a criação

```powershell
curl.exe -X POST http://localhost:8080/payments `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: 5fe3c100-71ba-49bc-b38c-bff7c21a9481" `
  -d '{"amountCents":14990}'
```

Depois da criação, o evento será publicado pelo scheduler da Outbox e processado pelo consumer.

## Organização do código

```text
src/main/java/com/payment/async/
├── features/
│   ├── outbox/
│   │   └── OutboxPublisher.java
│   └── payment/
│       ├── createPayment/
│       └── processPayment/
├── infra/
│   ├── persistance/
│   │   ├── entities/
│   │   ├── out/
│   │   └── repository/
│   └── rabbitmq/
└── security/
```

O controller não publica diretamente no RabbitMQ. O caso de uso salva o pagamento e a Outbox; o publisher cuida do broker; o consumer inicia o processamento assíncrono.

## O que já está implementado

- projeto Spring Boot executável;
- PostgreSQL via Docker Compose;
- migrations com Flyway;
- entidade de pagamento e status;
- validação do valor;
- idempotência básica;
- RabbitMQ com exchange, routing key e fila;
- conversão de mensagens para JSON;
- tabela `outbox_events`;
- persistência do pagamento e evento na mesma transação;
- publisher agendado da Outbox;
- consumer RabbitMQ;
- integração com autorizador externo;
- atualização para `APPROVED` ou `DECLINED`.

## Próximos passos

### Confiabilidade da mensageria

- configurar publisher confirms;
- limitar tentativas de publicação;
- adicionar dead-letter queue;
- registrar o erro da última tentativa;
- evitar concorrência entre múltiplas instâncias do publisher;
- criar política de retenção para eventos publicados.

### API e domínio

- criar `GET /payments/{id}`;
- retornar `202 Accepted` na criação assíncrona;
- retornar `paymentId`, `status` e `statusUrl`;
- proteger transições de estado no domínio;
- separar falhas técnicas de recusas de negócio.

### Idempotência do consumer

- adicionar `messageId` aos eventos;
- criar tabela `processed_messages`;
- impedir processamento duplicado;
- testar redelivery e falhas entre persistência e ACK.

### Qualidade e operação

- adicionar testes unitários e de integração;
- usar Testcontainers para PostgreSQL e RabbitMQ;
- criar teste ponta a ponta `POST → Outbox → RabbitMQ → Worker → GET`;
- adicionar logs estruturados com `paymentId` e `messageId`;
- adicionar health checks e métricas;
- configurar CI.

### Segurança e integrações futuras

- adicionar merchants/clientes;
- escopar idempotência por `client_id`;
- implementar autenticação e isolamento de dados;
- adicionar webhooks assinados com HMAC;
- criar retry e DLQ para notificações externas.

## Decisões e limitações

- o projeto permanece como um monólito modular durante o MVP;
- RabbitMQ é usado para processamento assíncrono, não para substituir o PostgreSQL;
- a entrega esperada é `at-least-once`, não `exactly-once`;
- consumidores devem ser idempotentes;
- o autorizador externo é uma dependência de demonstração;
- dados de cartão não fazem parte do escopo;
- a autenticação ainda não está implementada.

## Objetivo do projeto

Este projeto evolui de uma API síncrona para uma aplicação distribuída com persistência, mensageria, processamento assíncrono e tolerância a falhas. Cada etapa do roadmap introduz um problema real de engenharia e documenta a decisão utilizada para resolvê-lo.
