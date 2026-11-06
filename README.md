# Desafio Intelbras: Dispositivos

App Android nativo (Kotlin + Jetpack Compose) para listar, consultar, adicionar, editar, remover e favoritar dispositivos de vídeo e alarme, integrado à API do desafio. É **offline-first**: o banco local (Room) é a fonte única de verdade.

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
# ViewModels, repositório, fila offline, worker, validação e regras de estado
./gradlew testDebugUnitTest :core:domain:jvmTest :core:testing:test

# Room com SQLite real (DAO, fila, migrações) e telas com Compose UI Test (precisa de emulador/dispositivo)
./gradlew connectedDebugAndroidTest

# Tudo de uma vez, como antes de cada commit
./gradlew detekt lintDebug testDebugUnitTest :core:domain:jvmTest :core:testing:test connectedDebugAndroidTest
```

`:core:domain` é Kotlin Multiplatform: `jvmTest` roda os testes no alvo usado pelo Android, e no Mac `:core:domain:allTests` roda também no simulador do iOS. `:core:testing` é Kotlin/JVM, então a tarefa dele é `test`. Os relatórios ficam em `<módulo>/build/reports/`.

Análise estática (também roda no CI):

```bash
./gradlew detekt lintDebug
```

- **detekt** (com as regras do ktlint): estilo, formatação e complexidade. `config/detekt/detekt.yml` guarda só o que difere do padrão, cada ajuste com o motivo (a maioria é para Compose).
- **Android Lint**: erro quebra o build; avisos de "versão mais nova disponível" ficam desligados, porque atualizar dependências é feito à parte.

## Arquitetura

MVVM com camadas separadas e fluxo de dados unidirecional:

```
UI (Compose)  ──eventos──▶  ViewModel  ──▶  DeviceRepository (domínio)
     ▲                         │                    │
     └──── StateFlow<UiState> ◀┘          ┌─────────┴─────────┐
                                     Room (local)          Ktor (remoto)
                                   fonte de verdade     sincroniza o local
```

### Módulos

```
                         :app
          (Application, MainActivity, AppNavHost, grafo do Hilt)
            │                 │                    │
            ▼                 ▼                    │
  :feature:dashboard   :feature:device             │
     (lista)        (formulário, informações)      │
            │                 │                    │
            └──────┬──────────┘                    │
                   ▼                               ▼
               :core:ui                       :core:data
   (tema, componentes, UserMessageBus,  (Room, Ktor, fila offline,   
    textos comuns, tradução de erros)    WorkManager, módulos Hilt)
                   │                               │
                   └──────────────┬────────────────┘
                                  ▼
                            :core:domain
             (modelos, validação, DeviceRepository, NetworkMonitor)
                  Kotlin Multiplatform (jvm + iOS)

  :core:testing → fakes (FakeDeviceRepository, FakeNetworkMonitor...) e MainDispatcherRule,
                  usados só nos testes das features e do :core:data
```

Regras que a estrutura garante (um import que as viole não compila, porque o módulo não está nas dependências):

- **Uma feature não conhece `:core:data` nem outra feature.** As telas falam só com a interface `DeviceRepository` do domínio; quem entrega a implementação é o Hilt, montado no `:app`.
- **A navegação entre features passa pelo `:app`.** Cada feature expõe suas rotas e o trecho do grafo (`dashboardScreen`, `deviceFormScreen`, `navigateToDeviceForm`...). A lista só avisa "editar o item X", e o `AppNavHost` traduz isso numa rota da feature de dispositivo.
- **`:core:domain` não tem Android.** É um módulo Kotlin Multiplatform (alvos jvm, iosArm64 e iosSimulatorArm64): o mesmo código e os mesmos testes valem para Android e iOS.
- **Cada módulo tem seus próprios textos.** Os comuns (cancelar, tentar de novo, erros de rede e de validação) ficam no `:core:ui` e são importados como `CoreUiR`.

A configuração repetida dos módulos fica em **convention plugins** (`build-logic/`, um *included build*). Assim, o `build.gradle.kts` de uma feature tem só o plugin e o namespace:

| Plugin | O que aplica |
|---|---|
| `desafio.android.application` | AGP de app, SDKs, Java 11, regras do Lint e detekt |
| `desafio.android.library` | O mesmo para bibliotecas Android, mais JUnit e o runner de testes instrumentados |
| `desafio.android.compose` | Compose ligado, BOM, preview e ferramentas de debug |
| `desafio.hilt` | Hilt + KSP |
| `desafio.android.feature` | biblioteca + Compose + Hilt, `:core:ui`, `:core:domain`, ViewModel, Navigation, serialização das rotas e `:core:testing` nos testes |
| `desafio.kmp.library` | Kotlin Multiplatform sem Android (jvm, iosArm64, iosSimulatorArm64), `kotlin.test` em `commonTest` e detekt lendo `src/<sourceSet>` |
| `desafio.jvm.library` | Kotlin/JVM puro, para `:core:testing` |
| `desafio.detekt` | detekt com as regras do ktlint e o `config/detekt/detekt.yml` compartilhado |

Os pacotes não mudaram com a modularização (só `R` e `BuildConfig` ganharam o namespace do módulo), por isso a tabela abaixo continua valendo:

| Pacote (módulo) | Responsabilidade |
|---|---|
| `domain` (`:core:domain`) | Modelos puros (`Device` + `DeviceDetails` por tipo, `DeviceInput`, `Outcome`, `AppError`), regras de validação (`DeviceValidator`) e a interface `DeviceRepository`. Não conhece Android. |
| `data/local` (`:core:data`) | Room: entidades, DAO, `DeviceLocalDataSource`. |
| `data/remote` (`:core:data`) | `DeviceRemoteDataSource`: esconde os dois recursos da API, os DTOs e a tradução de erros (`safeApiCall`), devolvendo modelos de domínio. Implementada com Ktor: `DeviceApi` (os endpoints) e `createHttpClient` (URL base, token, JSON, timeout e status de erro como `HttpStatusException`). O engine vem de fora: OkHttp no Android e `MockEngine` nos testes. |
| `data/repository` (`:core:data`) | `OfflineFirstDeviceRepository`: só orquestra as duas fontes (lê do Room, escreve via fonte remota e reflete no Room, com `Mutex`). Não conhece Ktor, DTOs nem Room. |
| `data/sync` (`:core:data`) | Fila offline: `SyncScheduler` (WorkManager) e `SyncPendingOperationsWorker`. |
| `data/network` (`:core:data`) | `ConnectivityNetworkMonitor`: implementa o `NetworkMonitor` do domínio, estado da conexão como `Flow`. |
| `di` (`:core:data`) | Módulos Hilt da camada de dados. |
| `ui/theme`, `ui/components`, `ui/common` (`:core:ui`) | Tema, componentes reutilizáveis, `UserMessageBus` e tradução de `AppError` em texto. |
| `ui/dashboard` (`:feature:dashboard`) | Lista, busca, filtros, menu de ações e aviso de sincronização. |
| `ui/device` (`:feature:device`) | Formulário de cadastro/edição e tela de informações. |
| `ui/navigation` (`:app`) | `AppNavHost`, que liga as rotas das features. |

### Single source of truth

- A UI **só observa o Room** (`Flow`). Nenhuma tela lê direto da API.
- `refresh()` primeiro **envia a fila de pendências** e depois busca **os dois endpoints em paralelo** (`async`), sincronizando cada tipo com `syncType`. Numa **única transação**, ele faz upsert do que veio e apaga o que sumiu do servidor. Se um endpoint falhar, o outro tipo é atualizado mesmo assim e o que falhou mantém o último sync.
- Vídeo e alarme ficam numa **tabela única** com colunas específicas anuláveis. Assim listagem, busca e filtros são uma query só; o mapper garante que cada tipo tenha seus campos.
- **Criar, editar e remover funcionam sem rede** (ver [Funcionamento offline](#funcionamento-offline)). Com rede, criar e editar chamam a API primeiro e gravam a resposta no Room; a lista se atualiza sozinha.

### Funcionamento offline

O app abre e mostra os dados sem rede, e criar, editar e remover funcionam offline. O desenho segue o padrão **Outbox**: a mudança local e a entrada na fila são gravadas na mesma transação, e um worker envia a fila depois. A fila tem sua própria interface (`PendingOperationsLocalDataSource`), separada da que lê e grava dispositivos.

```
Usuário remove ──▶ Room (transação):                 WorkManager (rede validada)
                   devices.sync_state = PENDING_DELETE      │
                   pending_operations += DELETE ────────────▶ SyncPendingOperationsWorker
                   (a view esconde o item na hora)           └─▶ repository.syncPendingOperations()
```

**Remoção otimista com soft delete.** O item some da lista na hora, mas a linha só é apagada quando o servidor confirma. Se o servidor recusar, o item volta, **com o favorito**, e o usuário é avisado. A view `device_with_favorite` filtra `PENDING_DELETE`, então todas as telas e o contador do cabeçalho obedecem sem código extra.

**Criar e editar: online primeiro, fila sem rede.** Com rede, a chamada vai direto à API, e um 409 (serial ou MAC repetido) marca o campo no formulário na hora. Sem rede, ou com erro temporário, o item é gravado no banco e entra na fila; o formulário fecha com "Salvo. Será sincronizado quando houver conexão." O resultado é um `WriteResult` (`Synced` ou `Queued`), e a linha do item mostra "Aguardando envio" até o servidor confirmar.

**Id temporário.** Um item criado offline recebe o id `local-<uuid>` e já aparece no topo da lista. Quando o POST dá certo, `confirmCreate` troca pelo id do servidor numa transação, levando junto o favorito e a posição. A fila não guarda cópia dos dados: envia a versão atual da linha, então criar e depois editar offline vira um POST só. Uma tela de edição aberta com o id antigo continua funcionando, porque o repositório lembra a troca.

**Junção de operações do mesmo dispositivo.** A fila guarda no máximo uma operação por dispositivo (`device_id` é a chave):

| Já na fila | Nova ação | Resultado |
|---|---|---|
| — | remover | `DELETE` na fila, item escondido |
| — | criar / editar sem rede | `CREATE` / `UPDATE` na fila, item visível com "Aguardando envio" |
| `CREATE` (nunca chegou ao servidor) | editar | continua `CREATE`, com os dados novos |
| `CREATE` | remover | apaga a linha e tira da fila: nada a enviar |
| `UPDATE` | editar | continua um `UPDATE` só, na posição original |
| `UPDATE` | remover | vira só `DELETE` (editar algo que vai sumir é inútil) |
| `DELETE` | remover | continua um só, mantendo a posição original na fila |
| `DELETE` | editar | recusado: o item já está escondido |

**Envio.** `WorkManagerSyncScheduler` agenda um trabalho único (`APPEND_OR_REPLACE`) que só roda com **rede validada**, com backoff exponencial. Ele também é agendado na abertura do app, para retomar uma fila que sobrou. A fila é enviada **em ordem** (`created_at`); uma falha temporária interrompe a rodada para nenhuma operação "furar a fila".

**Classificação de erros** (`AppError.failureKind()`), que decide o que fazer com cada resposta:

| Tipo | Erros | O que acontece |
|---|---|---|
| `TRANSIENT` | sem rede, timeout, 5xx, 429 | conta a tentativa, mantém na fila, `Result.retry()` |
| `AUTH` | 401/403 | pausa a fila sem descartar nada (token precisa ser corrigido) |
| `ALREADY_DONE` | 404 | remoção: confirma, o estado desejado já existe (**idempotência**). Edição: o item foi removido em outro aparelho, sai da lista e avisa |
| `REJECTED` | demais 4xx | desfaz a mudança local e avisa: remoção volta o item; criação descarta o item (409 diz que o serial/MAC já existe); edição sai da fila e a versão do servidor volta no próximo refresh |

**Sem "ressurreição" nem sumiço.** `syncType` não sobrescreve nem apaga itens com operação na fila: um refresh antes do envio não traz de volta um item removido offline, não desfaz uma edição offline e não apaga um item criado offline (que o servidor ainda não conhece).

**Concorrência.** Um `Mutex` no repositório serializa o worker, o `refresh` e as escritas na API. Sem ele, um `refresh` iniciado antes de um "adicionar" poderia terminar depois e apagar o item recém-criado.

**Recriar o que foi removido offline.** Antes de criar, o repositório envia a fila. Sem isso, remover um dispositivo offline e recriá-lo com o mesmo serial/MAC daria 409, porque o servidor ainda teria o antigo.

**Feedback ao usuário.**
- Aviso fixo no cabeçalho, a partir de `NetworkMonitor` (`ConnectivityManager`, só conta como online uma rede com `INTERNET` e `VALIDATED`) e da contagem da fila: "Sem conexão", "Sem conexão. N alterações aguardando envio" ou "Enviando N alterações…". É um estado, não um evento, por isso não é Snackbar; o TalkBack anuncia a mudança (`liveRegion`).
- Snackbar específico ao salvar ou remover offline ("Será sincronizado quando houver conexão") e para cada recusa do servidor, com o nome do dispositivo.
- "Aguardando envio" na linha de cada item criado ou editado offline.

**Limitações conhecidas**
- **Edição recusada mostra a versão local até o próximo refresh.** A fila sai e o aviso aparece na hora, mas os dados do servidor só voltam na próxima atualização da lista.
- **Tela de informações aberta com o id temporário** mostra "não encontrado" depois que o item é enviado (a edição não tem esse problema).
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

Os esquemas são exportados em `core/data/schemas` e todas as migrações são `AutoMigration` do Room:

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
| Ktor (engine OkHttp) + kotlinx.serialization | Cliente HTTP multiplataforma, pronto para o iOS (engine Darwin); serialização sem reflexão |
| Coroutines/Flow | Assincronismo (requisito) |
| WorkManager + `hilt-work` | Envio da fila offline com restrição de rede, backoff e sobrevivência a reinícios |
| JUnit + kotlinx-coroutines-test | Testes de ViewModel e repositório com fakes |
| Compose UI Test | Testes das telas "sem estado" com um `UiState` fixo: o que aparece e quais ações são disparadas |
| detekt 2 (+ ktlint) e Android Lint | Análise estática local e no CI. O detekt 2 ainda é alpha, mas é a primeira linha compatível com AGP 9 (Kotlin embutido) e Gradle 9 |

## Decisões técnicas e trade-offs

- **Fakes em vez de mocks**: os testes usam implementações fake das interfaces, que são mais legíveis e não quebram com refatorações internas.
- **Busca e filtros no SQLite**: a filtragem vive numa query só. Trade-off: o `LIKE` do SQLite só ignora caixa em ASCII ("câmera" ≠ "CÂMERA"). Melhoria possível: uma coluna normalizada (sem acento, minúscula) para busca.
- **Validação no app espelhando a API** (`DeviceValidator`): feedback imediato por campo, sem ida ao servidor para erros óbvios. O MAC é normalizado enquanto o usuário digita (aceita `1a:2b:...`).
- **Tipo não muda na edição**: vídeo e alarme são recursos diferentes no servidor; trocar exigiria apagar e recriar.
- **Senhas dos dispositivos ficam no Room em texto puro**, porque a API as devolve assim. Na UI aparecem ocultas com botão para revelar, e o banco fica **fora do backup do Android** (`backup_rules.xml` e `data_extraction_rules.xml`), para as senhas não irem para a nuvem nem para outro aparelho. Trade-off: os favoritos não voltam ao reinstalar. Melhoria: criptografar o banco (SQLCipher) ou guardar só no Keystore.
- **Exclusão com 404 conta como sucesso**: o estado desejado (item não existe) foi alcançado. Isso torna o reenvio da fila idempotente.
- **Cleartext HTTP** liberado só para o host da API (`network_security_config.xml`).
- **Foco verde no campo de busca (desvio do guia)**: no Figma a busca focada só ganha borda cinza, o que deixa o foco quase invisível (pouco contraste, ruim para acessibilidade). Usei o mesmo verde (`#00B26B`) dos campos do formulário para o feedback ser claro e consistente no app inteiro. A lupa também fica verde no foco, seguindo a regra de "ícone ativo" da Iconografia do guia.
- **Modularização por camada e por feature**: `:core:*` separa domínio, dados e UI comum; `:feature:*` separa as telas. Ganhos: build incremental (mexer numa tela não recompila os dados), dependências proibidas viram erro de compilação e cada feature é testada isolada. Custo: mais arquivos de build, que os convention plugins mantêm curtos.

## Pendências e melhorias

- [x] Fila de operações offline para remoção (WorkManager + `pending_operations`)
- [x] Criar e editar offline (id temporário trocado pelo do servidor; última escrita vence)
- [ ] Sugestões para a API: `createdAt` (ordem real) e favoritos por usuário (sincronizar entre aparelhos)
- [x] Testes de UI com Compose UI Test (dashboard e formulário)
- [ ] Teste de ponta a ponta com Hilt (`HiltAndroidRule`) e API falsa (MockWebServer)
- [x] detekt (com ktlint) e Android Lint no CI
- [x] Modularização (`:core:domain`, `:core:data`, `:core:ui`, `:core:testing`, `:feature:dashboard`, `:feature:device`)
- [ ] KMP na camada de domínio/dados (Room KMP + Ktor)
