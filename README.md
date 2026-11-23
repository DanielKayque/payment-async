# Async Payment API

API de pagamentos assíncronos desenvolvida com Java e Spring Boot, com foco em processamento em segundo plano, consistência de dados, idempotência e confiabilidade na comunicação entre serviços.

O projeto é educacional e tem como objetivo explorar problemas reais de engenharia de software em sistemas que utilizam mensageria. Não movimenta dinheiro real e não recebe dados de cartão.

## Objetivos

- Receber e persistir solicitações de pagamento.
- Processar pagamentos de forma assíncrona.
- Garantir consistência entre PostgreSQL e RabbitMQ.
- Evitar perda de eventos utilizando o padrão Transactional Outbox.
- Implementar mecanismos de retry e Dead Letter Queue (DLQ).
- Garantir idempotência no processamento de mensagens.
- Desenvolver testes unitários e de integração para cenários de falha.

## Stack tecnológica

- Java 25
- Spring Boot 4.1.2
- Spring MVC
- Spring Data JPA
- Bean Validation
- PostgreSQL 17
- Flyway
- RabbitMQ 4
- Gradle
- Docker Compose
- Testcontainers
- Springdoc OpenAPI
- JUnit 5 e Mockito

## Arquitetura

O projeto utiliza uma arquitetura de monólito modular, separando responsabilidades por funcionalidades e infraestrutura.

```text
Cliente
   |
   v
POST /payments
   |
   v
PaymentUseCase
   |
   +---- PaymentEntity (PENDING)
   |
   +---- OutboxEventEntity
              |
              v
       OutboxPublisher
              |
              v
         RabbitMQ
              |
              v
       PaymentConsumer
              |
              v
    ProcessPaymentUseCase
              |
              v
      Autorizador externo
              |
              v
       APPROVED / DECLINED
```

O pagamento e seu evento de Outbox são persistidos na mesma transação PostgreSQL. Um publicador agendado consulta os eventos pendentes e os publica no RabbitMQ.

O consumidor recebe o evento e inicia o processamento assíncrono do pagamento.

## Fluxo de pagamento

1. O cliente envia uma solicitação de pagamento com uma chave de idempotência.
2. A API valida os dados recebidos.
3. A aplicação verifica se a chave de idempotência já foi utilizada.
4. O pagamento é persistido com status `PENDING`.
5. Um evento `payment.created` é registrado na tabela `outbox_events`, na mesma transação.
6. O `OutboxPublisher` identifica eventos ainda não publicados.
7. O evento é publicado no exchange do RabbitMQ.
8. A mensagem é encaminhada para a fila de processamento.
9. O `PaymentConsumer` recebe o evento.
10. O caso de uso de processamento executa a autorização.
11. O pagamento tem seu status atualizado para `APPROVED` ou `DECLINED`.

### Estados do pagamento

```text
PENDING
   |
   v
PROCESSING
   |
   +----> APPROVED
   |
   +----> DECLINED
```

Falhas técnicas, como indisponibilidade do autorizador ou timeout, devem ser diferenciadas de recusas de negócio.

## Transactional Outbox

O padrão Transactional Outbox é utilizado para evitar inconsistências entre a persistência de um pagamento e a publicação de seu evento.

Sem esse padrão, a aplicação poderia salvar um pagamento no PostgreSQL e falhar antes de publicar a mensagem no RabbitMQ.

Com a Outbox, ambas as operações de persistência acontecem na mesma transação:

```text
Transação PostgreSQL
   |
   +---- Salva PaymentEntity
   |
   +---- Salva OutboxEventEntity
```

A tabela `outbox_events` armazena informações como:

- `aggregate_id`: identificador do pagamento relacionado.
- `event_type`: tipo do evento.
- `payload`: conteúdo do evento em JSONB.
- `published_at`: momento em que o evento foi publicado.
- `attempts`: quantidade de tentativas de publicação.

O `OutboxPublisher` consulta os eventos pendentes, realiza a publicação e atualiza seu estado após o envio.

Essa abordagem permite que eventos não publicados permaneçam registrados no banco e possam ser processados posteriormente.

## RabbitMQ

O RabbitMQ é responsável pela comunicação assíncrona entre a API e o processamento dos pagamentos.

### Topologia

| Componente | Nome |
|---|---|
| Exchange | `payment.exchange` |
| Tipo | DirectExchange |
| Routing Key | `payment.created` |
| Queue | `payment.processing` |

O publisher envia o payload do evento, mantendo a entidade da Outbox como um detalhe interno de persistência.

### ACK e NACK

O consumidor utiliza confirmações de mensagens para informar ao RabbitMQ o resultado do processamento.

- **ACK:** confirma que a mensagem foi processada com sucesso.
- **NACK:** informa que o processamento não foi concluído corretamente.
- **Requeue:** determina se uma mensagem rejeitada deve retornar à fila para nova entrega.

A confirmação da mensagem deve acontecer somente depois que o processamento e a persistência do resultado forem concluídos com sucesso.

### Retry e Dead Letter Queue

Falhas temporárias podem exigir novas tentativas de processamento.

O mecanismo de retry permite que mensagens com falha sejam processadas novamente, respeitando uma política de tentativas e intervalos configurados.

Quando o limite de tentativas é atingido, a mensagem pode ser encaminhada para uma Dead Letter Queue (DLQ).

A DLQ permite isolar mensagens que não conseguiram ser processadas, evitando ciclos infinitos de reprocessamento e permitindo investigação posterior.

### Confiabilidade da mensageria

O projeto explora os seguintes mecanismos:

- Confirmação de mensagens por ACK/NACK.
- Política de retry para falhas de processamento.
- Limitação de tentativas.
- Encaminhamento de mensagens para DLQ.
- Idempotência para evitar efeitos duplicados.
- Transactional Outbox para reduzir o risco de perda de eventos.

A entrega de mensagens segue o modelo *at-least-once*. Portanto, consumidores devem considerar a possibilidade de receber a mesma mensagem mais de uma vez.

## API disponível

### Criar pagamento

`POST /payments`

Headers:

```http
Content-Type: application/json
Idempotency-Key: 5fe3c100-71ba-49bc-b38c-bff7c21a9481
```

Body:

```json
{
  "amountCents": 14990
}
```

Resposta:

```json
{
  "amount": 149.90
}
```

O valor monetário é armazenado em centavos para evitar problemas de precisão associados a operações com ponto flutuante.

## Idempotência

A API utiliza uma chave de idempotência para evitar a criação de pagamentos duplicados em decorrência de requisições repetidas.

Comportamento esperado:

- A mesma chave com o mesmo conteúdo não deve criar outro pagamento.
- A mesma chave com conteúdo diferente deve gerar conflito.
- Uma constraint única no banco protege contra duplicidade concorrente.

A idempotência é especialmente importante em sistemas distribuídos, nos quais falhas de comunicação podem levar clientes a repetir solicitações.

## Como executar

### Pré-requisitos

- Java 25.
- Docker e Docker Compose.
- Portas 5432, 5672 e 15672 disponíveis.

### Configurar variáveis de ambiente

Copie o arquivo de exemplo:

```powershell
Copy-Item .env-example .env
```

Ajuste os valores conforme necessário.

Não versione credenciais reais.

### Subir os serviços

```bash
docker compose up -d
```

Verifique os containers:

```bash
docker compose ps
```

O ambiente disponibiliza:

- PostgreSQL.
- RabbitMQ.
- Painel de gerenciamento do RabbitMQ.

Painel:

`http://localhost:15672`

Credenciais padrão do ambiente local:

```text
Usuário: guest
Senha: guest
```

### Executar a aplicação

Windows:

```powershell
.\gradlew.bat bootRun
```

Linux/macOS:

```bash
./gradlew bootRun
```

As migrations do Flyway são executadas durante a inicialização da aplicação.

### Criar um pagamento

Windows:

```powershell
curl.exe -X POST http://localhost:8080/payments `
  -H "Content-Type: application/json" `
  -H "Idempotency-Key: 5fe3c100-71ba-49bc-b38c-bff7c21a9481" `
  -d '{"amountCents":14990}'
```

Após a criação, o evento será identificado pelo scheduler da Outbox, publicado no RabbitMQ e encaminhado ao consumidor.

## Testes

O projeto utiliza JUnit 5 e Mockito para testes unitários e de integração.

Testcontainers permite executar testes com dependências reais em containers, reduzindo a dependência de serviços previamente instalados no ambiente local.

Os cenários de teste devem contemplar:

- Validação da criação de pagamentos.
- Idempotência.
- Persistência e consistência dos dados.
- Publicação de eventos da Outbox.
- Consumo de mensagens pelo RabbitMQ.
- ACK/NACK.
- Retry e limite de tentativas.
- Encaminhamento para DLQ.
- Comportamento diante de falhas de processamento.

Para executar os testes:

Windows:

```powershell
.\gradlew.bat clean test
```

Linux/macOS:

```bash
./gradlew clean test
```

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

A organização separa os casos de uso das integrações com banco de dados e RabbitMQ.

O controller não realiza publicação diretamente no broker. O caso de uso de criação persiste o pagamento e a Outbox, enquanto o publisher e o consumer são responsáveis pela comunicação assíncrona.

## Próximos passos

### Confiabilidade e mensageria

- Implementar ou aprimorar publisher confirms.
- Garantir controle de concorrência entre instâncias do OutboxPublisher.
- Registrar o erro da última tentativa de publicação.
- Implementar política de retenção para eventos publicados.
- Aprimorar observabilidade de retry e DLQ.

### API e domínio

- Criar `GET /payments/{id}`.
- Retornar `202 Accepted` na criação assíncrona.
- Retornar `paymentId`, `status` e `statusUrl`.
- Proteger transições de estado no domínio.
- Separar falhas técnicas de recusas de negócio.

### Idempotência do consumidor

- Adicionar identificador único às mensagens.
- Implementar controle persistente de mensagens processadas.
- Garantir proteção contra efeitos duplicados.
- Testar redelivery e falhas entre persistência e ACK.

### Qualidade e operação

- Expandir a cobertura de testes.
- Criar testes ponta a ponta: POST → Outbox → RabbitMQ → Worker → GET.
- Adicionar logs estruturados com `paymentId` e `messageId`.
- Adicionar health checks e métricas.
- Configurar pipeline de CI.

### Segurança e integrações futuras

- Implementar autenticação e autorização.
- Adicionar merchants/clientes.
- Escopar idempotência por cliente.
- Implementar webhooks assinados com HMAC.
- Criar processamento assíncrono para notificações externas.

## Decisões arquiteturais

- O projeto permanece como um monólito modular.
- PostgreSQL é responsável pela persistência e consistência dos dados.
- RabbitMQ é utilizado para processamento assíncrono.
- Transactional Outbox reduz o risco de inconsistência entre banco e broker.
- A entrega de mensagens segue o modelo *at-least-once*.
- Consumidores devem ser idempotentes.
- Falhas técnicas não devem ser confundidas com recusas de negócio.
- O autorizador externo é uma dependência de demonstração.
- Dados de cartão não fazem parte do escopo.

## Objetivo do projeto

Este projeto evolui uma API de pagamentos para uma aplicação capaz de lidar com processamento assíncrono, persistência transacional, mensageria e tolerância a falhas.

Mais do que implementar funcionalidades, o objetivo é compreender os desafios envolvidos na construção de sistemas distribuídos: consistência, entrega de mensagens, idempotência, recuperação de falhas e confiabilidade operacional.
