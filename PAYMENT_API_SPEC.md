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

## Roadmap

### Fase 0 — Projeto executável

- Criar repositório separado da Banking API;
- iniciar Spring Boot, Java 21 e Gradle;
- adicionar Docker Compose com PostgreSQL;
- configurar ambiente e .env.example sem segredos reais;
- escrever README com objetivo, setup e aviso de simulação.

**Pronto quando:** alguém clona, sobe dependências e inicia a aplicação seguindo o README.

### Fase 1 — API síncrona mínima

- Migration inicial de payments;
- Payment e PaymentStatus com validações;
- POST cria PENDING e GET consulta por id;
- validar valor positivo, precisão, moeda e tamanhos;
- padronizar erros 400, 404 e 409;
- documentar com OpenAPI.

**Estude:** DTO versus entidade, validação HTTP versus domínio, migrations e status HTTP.

**Pronto quando:** testes cobrem criação, consulta e validação.

### Fase 2 — Idempotência

- Exigir Idempotency-Key;
- calcular hash estável da requisição;
- adicionar constraint única no PostgreSQL;
- repetição de chave e payload igual retorna o mesmo pagamento;
- payload diferente retorna 409;
- tratar concorrência usando a constraint.

**Estude:** índices únicos, concorrência, retry do cliente e hash canônico.

**Pronto quando:** chamadas concorrentes com a mesma chave criam apenas um pagamento.

### Fase 3 — RabbitMQ e worker

- Adicionar RabbitMQ ao Compose;
- definir exchange, routing key, fila e evento versionável PaymentRequested;
- criar consumidor que transita por PROCESSING e depois APPROVED ou DECLINED;
- confirmar ack somente após persistência;
- configurar retries limitados e dead-letter queue;
- manter simulador determinístico nos testes.

**Estude:** entrega at-least-once, ack, redelivery, retry e DLQ.

**Pronto quando:** POST responde antes do processamento e GET mostra o resultado depois.

### Fase 4 — Transactional Outbox

- Criar outbox_events;
- gravar pagamento e evento na mesma transação;
- publicar eventos ainda não publicados;
- marcar published_at após confirmação do broker;
- tratar múltiplas instâncias com estratégia de lock/claim no PostgreSQL;
- planejar retenção dos eventos publicados.

**Estude:** dual write e consistência eventual.

**Pronto quando:** criar pagamento com RabbitMQ fora do ar não perde o evento; depois da recuperação ele é enviado.

### Fase 5 — Consumidor idempotente e resiliência

- Incluir messageId único no evento;
- gravar messageId e resultado na mesma transação;
- ignorar duplicatas e impedir transições inválidas;
- separar falha técnica de recusa de negócio;
- testar redelivery, timeout e queda entre commit e ack.

**Pronto quando:** a mesma mensagem chega várias vezes sem duplicar efeito ou regredir estado.

### Fase 6 — Autenticação e isolamento

- Adicionar merchants/clientes e autenticação ou API keys;
- guardar hashes de segredos, nunca segredos em claro;
- escopar pagamentos, consultas e chaves por client_id;
- impedir consulta cruzada entre clientes;
- não registrar tokens ou segredos em logs.

**Pronto quando:** testes provam isolamento e rejeição de credenciais inválidas.

### Fase 7 — Webhooks

- Cadastrar destino HTTPS e eventos;
- assinar payload com HMAC SHA-256 e timestamp;
- prever replay protection e rotação de segredo;
- entregar em background com timeout, retry limitado e DLQ;
- persistir tentativas e oferecer consulta e reenvio autenticado.

**Pronto quando:** destino indisponível não bloqueia o pagamento e retries não duplicam efeitos no consumidor.

### Fase 8 — Portfólio

- Testar com Testcontainers para PostgreSQL e RabbitMQ;
- criar teste ponta a ponta POST → fila → worker → GET;
- adicionar logs estruturados com paymentId e messageId;
- incluir health checks e métricas básicas;
- documentar diagrama, decisões e limitações no README;
- configurar CI para build e testes;
- preparar demonstração pública com dados fictícios e limites de uso.

## Estratégia de testes

- Unitários: valor inválido, precisão, moeda, transições e hash idempotente.
- PostgreSQL: criação, consulta, repetição/conflito de chave, corrida concorrente e atomicidade payment/outbox.
- RabbitMQ: evento publicado, ack após commit, duplicata ignorada, retry e DLQ.

Use RabbitMQ real via Testcontainers para testar ack, redelivery e DLQ; mocks servem para regras unitárias.

## Primeira sessão de código

1. Criar repositório separado chamado async-payment-api.
2. Gerar Spring Boot com Java 21, Gradle, Web, Validation, Data JPA, PostgreSQL e Flyway.
3. Configurar PostgreSQL no Compose e variáveis em .env.example.
4. Criar V1__create_payments.sql com os campos básicos e constraint de valor positivo.
5. Implementar PaymentStatus e regras de transição.
6. Implementar DTOs, POST síncrono que retorna PENDING e GET por id.
7. Escrever testes de domínio e integração com banco.
8. Documentar execução e exemplos no README.

Não inclua RabbitMQ no primeiro commit. Primeiro deixe claros contrato, domínio, validação e persistência; depois introduza mensageria em uma etapa visível no histórico.

## Critérios para concluir o MVP

- POST retorna 202 e cria pagamento PENDING;
- repetição idempotente não duplica pagamentos;
- outbox preserva eventos durante indisponibilidade do broker;
- worker tolera mensagens duplicadas;
- GET mostra resultado apenas ao cliente autorizado;
- testes cobrem persistência, concorrência e falhas principais;
- README explica setup, API, estados, arquitetura e limitações;
- CI passa e a demonstração usa dados fictícios.
