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
./gradlew testDebugUnitTest          # ViewModels, repositório, regras de estado
./gradlew connectedDebugAndroidTest  # integração do Room (precisa de emulador/dispositivo)
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
| `di` | Módulos Hilt. |
| `ui` | Telas (`dashboard`, `device/form`, `device/details`), componentes reutilizáveis, navegação e tema. |

### Single source of truth

- A UI **só observa o Room** (`Flow`). Nenhuma tela lê direto da API.
- `refresh()` busca **os dois endpoints em paralelo** (`async`) e sincroniza cada tipo com `syncType`, que numa **única transação** faz upsert do que veio e apaga o que sumiu do servidor. Se um endpoint falhar, o outro tipo é atualizado mesmo assim e o que falhou mantém o último sync.
- Vídeo e alarme ficam numa **tabela única** com colunas específicas anuláveis. Assim listagem, busca e filtros são uma query só; o mapper garante que cada tipo tenha seus campos.
- Criar/editar/remover: chama a API e, com sucesso, grava o resultado no Room; a lista se atualiza sozinha.
- Sem conexão, a falha é mostrada num Snackbar e a lista continua mostrando o último sync.

### Como os favoritos são preservados

Favoritos ficam numa **tabela própria** (`favorites`), separada de `devices`:

1. A sincronização só escreve em `devices`; nunca toca em `favorites`.
2. A sincronização usa **`@Upsert`** (INSERT ou UPDATE), e não `OnConflictStrategy.REPLACE`. O `REPLACE` faz DELETE + INSERT, e esse DELETE dispararia o `ON DELETE CASCADE` e apagaria o favorito.
3. O `CASCADE` existe para o caso legítimo: se o dispositivo foi removido (no app ou no servidor), o favorito órfão vai junto.
4. A leitura faz `LEFT JOIN` e devolve `isFavorite`. Como a query observa as duas tabelas, marcar ou desmarcar atualiza a UI na hora.

Isso está coberto por `DeviceDaoTest` (SQLite real em memória) e `OfflineFirstDeviceRepositoryTest`.

### Concorrência

O repositório usa um `Mutex` nas operações que escrevem no banco a partir da API. Sem ele, um `refresh` iniciado antes de um "adicionar" poderia terminar depois e apagar o item recém-criado, porque a lista do servidor ainda não o tinha.

### Tratamento de erros

`safeApiCall` traduz exceções em `AppError` (`Network`, `Timeout`, `Unauthorized` para 401/403, `NotFound`, `Conflict` para 409, `InvalidData` para 400/422, `Server`, `Unknown`). No formulário, um 409 também marca o campo culpado (serial ou MAC) com "Já cadastrado". `CancellationException` é relançada para não quebrar o cancelamento estruturado. Os Snackbars passam por um `UserMessageBus` (singleton) coletado na raiz da navegação, para que a mensagem "Dispositivo adicionado" apareça no dashboard depois que o formulário fecha.

## Bibliotecas

| Lib | Por quê |
|---|---|
| Jetpack Compose + Material 3 | UI declarativa (requisito) |
| Navigation Compose (rotas type-safe) | Navegação com argumentos tipados |
| Hilt | Injeção de dependência padrão no Android, integrada a ViewModel/Navigation |
| Room | Persistência com `Flow` reativo, transações e verificação de SQL em tempo de compilação |
| Retrofit + OkHttp + kotlinx.serialization | Cliente HTTP; serialização sem reflexão |
| Coroutines/Flow | Assincronismo (requisito) |
| JUnit + kotlinx-coroutines-test | Testes de ViewModel e repositório com fakes |

## Decisões técnicas e trade-offs

- **Fakes em vez de mocks**: os testes usam implementações fake das interfaces, que são mais legíveis e não quebram com refatorações internas.
- **Busca e filtros no SQLite**: a filtragem vive numa query só. Trade-off: `LIKE` e `COLLATE NOCASE` só ignoram caixa em ASCII ("câmera" ≠ "CÂMERA"). Melhoria possível: uma coluna normalizada (sem acento, minúscula) para busca e ordenação.
- **Validação no app espelhando a API** (`DeviceValidator`): feedback imediato por campo, sem ida ao servidor para erros óbvios. O MAC é normalizado enquanto o usuário digita (aceita `1a:2b:...`).
- **Tipo não muda na edição**: vídeo e alarme são recursos diferentes no servidor; trocar exigiria apagar e recriar.
- **Senhas dos dispositivos ficam no Room em texto puro**, porque a API as devolve assim e a tela de informações as mostra. Na UI aparecem ocultas com botão para revelar. Melhoria: criptografar o banco (SQLCipher) ou guardar só no Keystore.
- **Exclusão com 404 conta como sucesso**: o estado desejado (item não existe) foi alcançado.
- **Cleartext HTTP** liberado só para o host da API (`network_security_config.xml`).
- **Foco verde no campo de busca (desvio do guia)**: no Figma a busca focada só ganha borda cinza, o que deixa o foco quase invisível (pouco contraste, ruim para acessibilidade). Usei o mesmo verde (`#00B26B`) dos campos do formulário para o feedback ser claro e consistente no app inteiro. A lupa também fica verde no foco, seguindo a regra de "ícone ativo" da Iconografia do guia.
- **Módulo único por enquanto**: a separação é por pacote, pensada para virar módulos (`:core:data`, `:feature:dashboard`...) sem refatorar.

## Pendências e melhorias

- [ ] Fila de operações offline (criar/editar/remover sem rede e sincronizar depois), por exemplo com WorkManager e uma tabela `pending_operations`
- [ ] Testes de UI com Compose UI Test
- [ ] ktlint/detekt no CI
- [ ] Modularização
- [ ] KMP na camada de domínio/dados (Room KMP + Ktor)
