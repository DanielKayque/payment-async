# Projeto de estudo: API de pagamentos assíncronos

Este projeto pratica construção de APIs além do CRUD: receber pagamentos simulados, processá-los em segundo plano e comunicar resultados com segurança diante de repetições e falhas.

> Projeto educacional: não movimenta dinheiro real nem processa dados de cartão.

## Objetivo e escopo

Construir uma API REST que aceite uma solicitação, persista o pagamento como pendente e responda com 202 Accepted. Um worker consome o trabalho de uma fila, simula uma decisão aprovada ou recusada e atualiza o estado. O cliente consulta o resultado depois.

O fluxo é simulado. Não receba número, CVV ou validade de cartão. Deixe a decisão do processador falso determinística nos testes.

## Stack

- Java 21 e Spring Boot: Spring MVC, Bean Validation e Spring Data JPA;
- PostgreSQL e Flyway;
- RabbitMQ como primeiro broker;
- JUnit, Mockito e Spring Boot Test;
- Testcontainers para PostgreSQL e RabbitMQ em fases posteriores;
- Docker Compose, OpenAPI/Swagger e CI.

Comece com um único serviço Spring Boot contendo API, publicador da outbox e worker. Não adicione Redis, Kafka, Kubernetes ou microsserviços no MVP.

## Fluxo principal

1. Cliente chama POST /api/v1/payments com o cabeçalho Idempotency-Key.
2. A API valida e, numa transação PostgreSQL, grava Payment como PENDING e PaymentRequested na outbox.
3. A API responde 202 com o id e a URL de consulta.
4. Um publicador lê eventos pendentes da outbox e os envia ao RabbitMQ.
5. O worker consome, simula o processamento e atualiza para APPROVED ou DECLINED.
6. Cliente consulta GET /api/v1/payments/{id}.

O pagamento e o evento precisam ser gravados na mesma transação. A outbox evita perder o evento se a aplicação cair entre gravar no banco e publicar no broker. O publicador pode enviar duplicatas; o consumidor deve ser idempotente. Não prometa exactly-once: a meta é entrega at-least-once e efeitos idempotentes.

## Contrato HTTP inicial

### Criar pagamento

POST /api/v1/payments

Cabeçalho obrigatório:

    Idempotency-Key: 5fe3c100-71ba-49bc-b38c-bff7c21a9481
    Content-Type: application/json

Corpo:

    {
      "merchantReference": "pedido-2026-00042",
      "amount": 149.90,
      "currency": "BRL",
      "description": "Pedido de demonstração"
    }

Resposta 202:

    {
      "paymentId": "a4d01071-1311-4e39-9cd2-4664fe9a0a84",
      "status": "PENDING",
      "amount": 149.90,
      "currency": "BRL",
      "createdAt": "2026-11-01T12:00:00Z",
      "statusUrl": "/api/v1/payments/a4d01071-1311-4e39-9cd2-4664fe9a0a84"
    }

Regras de idempotência:

1. A chave é única no escopo do cliente/merchant autenticado.
2. Mesma chave e mesmo conteúdo retornam o mesmo pagamento sem criar outro evento.
3. Mesma chave e conteúdo diferente retornam 409 Conflict.
4. Uma constraint única no banco protege também contra requisições concorrentes; checar apenas no Java não basta.

Para o MVP sem autenticação, documente se a chave é global ou local. Na fase de clientes, escopá-la por client_id.

### Consultar pagamento

GET /api/v1/payments/{paymentId} retorna 200 com o estado. Retorna 404 se o id não existir ou não pertencer ao cliente autenticado. Nunca exponha stack traces ou detalhes internos do worker.

Endpoints futuros: histórico paginado por cliente e cadastro de destinos de webhook. Não crie retry manual antes de definir regra de negócio e permissões.

## Estados

    PENDING -> PROCESSING -> APPROVED
                           -> DECLINED

- PENDING: aceito e aguardando processamento.
- PROCESSING: worker iniciou.
- APPROVED e DECLINED: estados terminais.
- Falha técnica não é recusa de negócio. RabbitMQ indisponível ou erro temporário não deve marcar DECLINED.

Defina as transições no domínio e atualize o estado condicionalmente para impedir que uma mensagem duplicada faça APPROVED voltar para PROCESSING.

## Modelo de dados

Armazene dinheiro em unidade mínima inteira, como centavos em BIGINT, nunca float ou double. Valide no máximo duas casas decimais e converta sem arredondamento. Persista a moeda explicitamente.

### payments

- id UUID;
- client_id UUID, quando houver autenticação;
- idempotency_key e request_hash;
- merchant_reference, amount_minor, currency e description;
- status, created_at, updated_at e processed_at;
- constraint única em (client_id, idempotency_key), ou global no MVP simplificado;
- constraint amount_minor > 0.

Calcule request_hash a partir de representação canônica dos campos relevantes para detectar chave reutilizada com outro payload.

### Return types

- Chave não encontrada: cria o pagamento e salva a chave junto com o hash.
- Chave encontrada e hash igual: retorna o pagamento existente, ou a resposta definida para aquela operação.
- Chave encontrada e hash diferente: retorna 409 Conflict.

### outbox_events

- id UUID, aggregate_id com id do pagamento e event_type;
- payload JSONB somente com dados necessários;
- created_at, published_at inicialmente nulo e attempts;
- índice para localizar eventos não publicados.

### processed_messages, numa fase posterior

- message_id UUID como chave primária;
- processed_at.

O worker grava message_id e atualiza o pagamento na mesma transação. Uma nova entrega encontra o id já processado e não repete o efeito.

## Organização de código

    src/main/java/.../
    ├── payments/
    │   ├── api/          # Controller e DTOs
    │   ├── application/  # Casos de uso
    │   ├── domain/       # Payment, status e transições
    │   └── persistence/  # Entidades JPA e repositórios
    ├── messaging/
    │   ├── outbox/       # Eventos pendentes e publicador
    │   └── payment/      # Contrato do evento e consumidor
    └── shared/
        ├── errors/
        └── config/

Não faça o controller falar com RabbitMQ. O caso de uso grava pagamento e outbox; o publicador cuida do broker. O consumidor não chama o controller.

