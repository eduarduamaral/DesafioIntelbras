# Desafio Intelbras: Dispositivos

App Android nativo (Kotlin + Jetpack Compose) para listar, consultar, adicionar, editar, remover e favoritar dispositivos de vídeo e alarme, integrado à API do desafio. É **offline-first**: o banco local (Room) é a fonte única de verdade.

> 🚧 Em desenvolvimento.

## API

Contrato em `http://squadapps.ddns-intelbras.com.br:37025/api` (OpenAPI em `/api-json`). Autenticação com `Authorization: Bearer <token>`.

| Recurso | Campos (todos obrigatórios) | Regras |
|---|---|---|
| `/video-devices` | `name`, `serial`, `username`, `password` | 409 se o serial já existe |
| `/alarm-centrals` | `name`, `macAddress`, `password` | MAC com 12 hex, sem separadores; senha com 4 ou 6 dígitos; 409 se o MAC já existe |

Cada recurso tem `GET` (lista em `{ count, data }`), `POST`, `PATCH /{id}` e `DELETE /{id}`. Token inválido responde **403**.

## Como rodar

1. Android Studio atualizado, JDK 21.
2. No `local.properties` (na raiz do projeto, **não versionado**), adicione:

   ```properties
   API_TOKEN=seu_token_aqui
   # opcional, já tem valor padrão:
   # API_BASE_URL=http://squadapps.ddns-intelbras.com.br:37025/
   ```

   Em vez do arquivo, também dá para usar as variáveis de ambiente `API_TOKEN` e `API_BASE_URL`, que é o que o CI usa.
3. Rode a configuração `app`.

Testes:

```bash
./gradlew testDebugUnitTest          # ViewModels, repositório, fila offline, worker, regras de estado
./gradlew connectedDebugAndroidTest  # Room com SQLite real: DAO, fila e migrações (precisa de emulador/dispositivo)
```

## Arquitetura

MVVM com camadas separadas e fluxo de dados unidirecional:

```
UI (Compose)  ──eventos──▶  ViewModel  ──▶  DeviceRepository (domínio)
     ▲                         │                    │
     └──── StateFlow<UiState> ◀┘          ┌─────────┴─────────┐
                                     Room (local)        Retrofit (remoto)
                                   fonte de verdade     sincroniza o local
```

| Pacote | Responsabilidade |
|---|---|
| `domain` | Modelos puros (`Device` + `DeviceDetails` por tipo, `DeviceInput`, `Outcome`, `AppError`), regras de validação (`DeviceValidator`) e a interface `DeviceRepository`. Não conhece Android. |
| `data/local` | Room: entidades, DAO, `DeviceLocalDataSource`. |
| `data/remote` | `DeviceRemoteDataSource`: esconde os dois recursos da API, os DTOs e a tradução de erros (`safeApiCall`), devolvendo modelos de domínio. Implementada com Retrofit (`DeviceApi`, `AuthInterceptor`). |
| `data/repository` | `OfflineFirstDeviceRepository`: só orquestra as duas fontes (lê do Room, escreve via fonte remota e reflete no Room, com `Mutex`). Não conhece Retrofit, DTOs nem Room. |
| `data/sync` | Fila offline: `SyncScheduler` (WorkManager) e `SyncPendingOperationsWorker`. |
| `data/network` | `NetworkMonitor`: estado da conexão como `Flow`. |
| `di` | Módulos Hilt. |
| `ui` | Telas (`dashboard`, `device/form`, `device/details`), componentes reutilizáveis, navegação e tema. |

### Single source of truth

- A UI **só observa o Room** (`Flow`). Nenhuma tela lê direto da API.
- `refresh()` primeiro **envia a fila de pendências** e depois busca **os dois endpoints em paralelo** (`async`), sincronizando cada tipo com `syncType`. Numa **única transação**, ele faz upsert do que veio e apaga o que sumiu do servidor. Se um endpoint falhar, o outro tipo é atualizado mesmo assim e o que falhou mantém o último sync.
- Vídeo e alarme ficam numa **tabela única** com colunas específicas anuláveis. Assim listagem, busca e filtros são uma query só; o mapper garante que cada tipo tenha seus campos.
- **Remover** é otimista e funciona sem rede (ver [Funcionamento offline](#funcionamento-offline)). **Criar e editar** chamam a API e, com sucesso, gravam a resposta no Room; a lista se atualiza sozinha.

### Funcionamento offline

O app abre e mostra os dados sem rede, e a remoção funciona offline. O desenho segue o padrão **Outbox**: a mudança local e a entrada na fila são gravadas na mesma transação, e um worker envia a fila depois.

```
Usuário remove ──▶ Room (transação):                 WorkManager (rede validada)
                   devices.sync_state = PENDING_DELETE      │
                   pending_operations += DELETE ────────────▶ SyncPendingOperationsWorker
                   (a view esconde o item na hora)           └─▶ repository.syncPendingOperations()
```

**Remoção otimista com soft delete.** O item some da lista na hora, mas a linha só é apagada quando o servidor confirma. Se o servidor recusar, o item volta, **com o favorito**, e o usuário é avisado. A view `device_with_favorite` filtra `PENDING_DELETE`, então todas as telas e o contador do cabeçalho obedecem sem código extra.

**Junção de operações do mesmo dispositivo.** A fila guarda no máximo uma operação por dispositivo (`device_id` é a chave):

| Já na fila | Nova ação | Resultado |
|---|---|---|
| — | remover | `DELETE` na fila, item escondido |
| `CREATE` (nunca chegou ao servidor) | remover | apaga a linha e tira da fila: nada a enviar |
| `UPDATE` | remover | vira só `DELETE` (editar algo que vai sumir é inútil) |
| `DELETE` | remover | continua um só, mantendo a posição original na fila |

As linhas `CREATE`/`UPDATE` já existem no modelo e nas regras (testadas no DAO) para quando criar e editar offline forem implementados.

**Envio.** `WorkManagerSyncScheduler` agenda um trabalho único (`APPEND_OR_REPLACE`) que só roda com **rede validada**, com backoff exponencial. Ele também é agendado na abertura do app, para retomar uma fila que sobrou. A fila é enviada **em ordem** (`created_at`); uma falha temporária interrompe a rodada para nenhuma operação "furar a fila".

**Classificação de erros** (`AppError.failureKind()`), que decide o que fazer com cada resposta:

| Tipo | Erros | O que acontece |
|---|---|---|
| `TRANSIENT` | sem rede, timeout, 5xx, 429 | conta a tentativa, mantém na fila, `Result.retry()` |
| `AUTH` | 401/403 | pausa a fila sem descartar nada (token precisa ser corrigido) |
| `ALREADY_DONE` | 404 | confirma: o estado desejado já existe (**idempotência**) |
| `REJECTED` | demais 4xx | desfaz a mudança local e avisa o usuário |

**Sem "ressurreição".** `syncType` não sobrescreve itens com operação na fila: um refresh que rode antes do envio não traz de volta o item removido offline.

**Concorrência.** Um `Mutex` no repositório serializa o worker, o `refresh` e as escritas na API. Sem ele, um `refresh` iniciado antes de um "adicionar" poderia terminar depois e apagar o item recém-criado.

**Recriar o que foi removido offline.** Antes de criar, o repositório envia a fila. Sem isso, remover um dispositivo offline e recriá-lo com o mesmo serial/MAC daria 409, porque o servidor ainda teria o antigo.

**Feedback ao usuário.**
- Aviso fixo no cabeçalho, a partir de `NetworkMonitor` (`ConnectivityManager`, só conta como online uma rede com `INTERNET` e `VALIDATED`) e da contagem da fila: "Sem conexão", "Sem conexão. N alterações aguardando envio" ou "Enviando N alterações…". É um estado, não um evento, por isso não é Snackbar; o TalkBack anuncia a mudança (`liveRegion`).
- Snackbar específico ao remover offline ("Será sincronizado quando houver conexão") e quando o servidor recusa uma remoção.
- Criar/editar sem rede: "Nada foi salvo" e o formulário continua aberto, com os dados preenchidos.

**Limitações conhecidas**
- **Criar e editar ainda exigem rede.** O caminho está preparado (`PENDING_CREATE`/`PENDING_UPDATE`), mas criar offline exige um id temporário trocado pelo do servidor depois, e editar offline exige resolver conflitos.
- **Última escrita vence.** A API não tem versão nem `updatedAt`, então duas edições do mesmo item em aparelhos diferentes não são detectadas: vale a última que chegar.

### Ordem da lista

A lista mostra os **adicionados mais recentemente primeiro**. A API não devolve data de criação (e o `id` é UUID aleatório), então o app guarda uma coluna local `devices.sort_order`:

- Criado pelo app: `MAX + 1`, vai para o topo.
- Novo vindo da API: recebe números seguidos, na ordem da resposta (o último da resposta fica no topo).
- Já conhecido: mantém o número. Editar ou sincronizar não reordena.

É um **contador, não um horário**: não empata quando vários itens chegam no mesmo sync e não depende do relógio do aparelho. A atribuição fica dentro de transações do DAO (os `@Upsert` crus são `protected`), então worker e tela não pegam o mesmo número.

Limitações: na primeira sincronização a ordem real só é preservada se a API listar do mais antigo para o mais novo, e câmeras e alarmes (endpoints diferentes) não podem ser intercalados. Dali em diante a ordem é exata. A solução definitiva seria a API devolver `createdAt`.

### Como os favoritos são preservados

Favoritos ficam numa **tabela própria** (`favorites`), separada de `devices`:

1. A sincronização só escreve em `devices`; nunca toca em `favorites`.
2. A sincronização usa **`@Upsert`** (INSERT ou UPDATE), e não `OnConflictStrategy.REPLACE`. O `REPLACE` faz DELETE + INSERT, e esse DELETE dispararia o `ON DELETE CASCADE` e apagaria o favorito.
3. O `CASCADE` existe para o caso legítimo: se o dispositivo foi removido (no app ou no servidor), o favorito órfão vai junto.
4. A leitura vem da **view `device_with_favorite`** (`@DatabaseView`), que faz o `LEFT JOIN` e devolve `is_favorite`. A regra "é favorito" fica definida num lugar só, e as queries do DAO só acrescentam o `WHERE`. Como a view lê as duas tabelas, marcar ou desmarcar atualiza a UI na hora.

Os favoritos são **locais, por aparelho**, como pede o desafio: a API não tem esse conceito. Marcar no celular não aparece no emulador. Para acompanharem o usuário, a API precisaria guardá-los (por exemplo, um endpoint de favoritos por usuário).

Isso está coberto por `DeviceDaoTest` (SQLite real em memória) e `OfflineFirstDeviceRepositoryTest`.

### Migrações do banco

Os esquemas são exportados em `app/schemas` e todas as migrações são `AutoMigration` do Room:

| Versão | Mudança |
|---|---|
| 1 | tabelas `devices` e `favorites` |
| 2 | view `device_with_favorite` |
| 3 | coluna `sync_state` (padrão `SYNCED`), tabela `pending_operations`; a view passa a esconder remoções pendentes |
| 4 | coluna `sort_order`; as linhas existentes são numeradas pelo `rowid` (ordem de inserção) num `AutoMigrationSpec`, para não ficarem empatadas |

O `AppDatabaseMigrationTest` cria o banco como era em cada versão, com dados e favoritos, migra e confere o resultado (inclusive de 1 direto até a atual): quem já tem o app instalado não perde nada ao atualizar.

### Tratamento de erros

`safeApiCall` traduz exceções em `AppError` (`Network`, `Timeout`, `Unauthorized` para 401/403, `NotFound`, `Conflict` para 409, `InvalidData` para 400/422, `Server`, `Unknown`). No formulário, um 409 também marca o campo culpado (serial ou MAC) com "Já cadastrado", e a mensagem do servidor num 400 é mostrada ao usuário. `CancellationException` é relançada para não quebrar o cancelamento estruturado. Os Snackbars passam por um `UserMessageBus` (singleton) coletado na raiz da navegação, para que a mensagem "Dispositivo adicionado" apareça no dashboard depois que o formulário fecha.

## Bibliotecas

| Lib | Por quê |
|---|---|
| Jetpack Compose + Material 3 | UI declarativa (requisito) |
| Navigation Compose (rotas type-safe) | Navegação com argumentos tipados |
| Hilt | Injeção de dependência padrão no Android, integrada a ViewModel/Navigation |
| Room | Persistência com `Flow` reativo, transações e verificação de SQL em tempo de compilação |
| Retrofit + OkHttp + kotlinx.serialization | Cliente HTTP; serialização sem reflexão |
| Coroutines/Flow | Assincronismo (requisito) |
| WorkManager + `hilt-work` | Envio da fila offline com restrição de rede, backoff e sobrevivência a reinícios |
| JUnit + kotlinx-coroutines-test | Testes de ViewModel e repositório com fakes |

## Decisões técnicas e trade-offs

- **Fakes em vez de mocks**: os testes usam implementações fake das interfaces, que são mais legíveis e não quebram com refatorações internas.
- **Busca e filtros no SQLite**: a filtragem vive numa query só. Trade-off: o `LIKE` do SQLite só ignora caixa em ASCII ("câmera" ≠ "CÂMERA"). Melhoria possível: uma coluna normalizada (sem acento, minúscula) para busca.
- **Validação no app espelhando a API** (`DeviceValidator`): feedback imediato por campo, sem ida ao servidor para erros óbvios. O MAC é normalizado enquanto o usuário digita (aceita `1a:2b:...`).
- **Tipo não muda na edição**: vídeo e alarme são recursos diferentes no servidor; trocar exigiria apagar e recriar.
- **Senhas dos dispositivos ficam no Room em texto puro**, porque a API as devolve assim e a tela de informações as mostra. Na UI aparecem ocultas com botão para revelar. Melhoria: criptografar o banco (SQLCipher) ou guardar só no Keystore.
- **Exclusão com 404 conta como sucesso**: o estado desejado (item não existe) foi alcançado. Isso torna o reenvio da fila idempotente.
- **Cleartext HTTP** liberado só para o host da API (`network_security_config.xml`).
- **Foco verde no campo de busca (desvio do guia)**: no Figma a busca focada só ganha borda cinza, o que deixa o foco quase invisível (pouco contraste, ruim para acessibilidade). Usei o mesmo verde (`#00B26B`) dos campos do formulário para o feedback ser claro e consistente no app inteiro. A lupa também fica verde no foco, seguindo a regra de "ícone ativo" da Iconografia do guia.
- **Módulo único por enquanto**: a separação é por pacote, pensada para virar módulos (`:core:data`, `:feature:dashboard`...) sem refatorar.

## Pendências e melhorias

- [x] Fila de operações offline para remoção (WorkManager + `pending_operations`)
- [ ] Criar e editar offline (id temporário e resolução de conflitos)
- [ ] Sugestões para a API: `createdAt` (ordem real) e favoritos por usuário (sincronizar entre aparelhos)
- [ ] Testes de UI com Compose UI Test
- [ ] ktlint/detekt no CI
- [ ] Modularização
- [ ] KMP na camada de domínio/dados (Room KMP + Ktor)
