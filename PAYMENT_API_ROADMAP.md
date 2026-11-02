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
