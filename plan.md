# SolomonSeal — Полный план развития

> Практический roadmap на основе code review, анализа архитектуры BDoc Editor и конкурентного анализа AI coding tools.

---

## Обзор

**Текущее состояние:** MVP с рабочим агентским циклом, JavaFX UI, SQLite-индексом, режимами Ask/Plan/Build. Монолитный Gradle-модуль, нет тестов, есть баг сериализации `tool_calls`, нет плагинов, нет sandbox, нет undo.

**Цель:** Прозрачный, контролируемый, расширяемый LLM-оркестратор с встроенной историей изменений, маскированием секретов и плагинной системой.

---

## Фаза 0: Быстрые победы (1–2 недели)

Задачи, которые не требуют рефакторинга архитектуры, но устраняют критические дефекты.

### 0.1 Исправить сериализацию `tool_calls`
- **Проблема:** `buildAssistantMessage` сохраняет tool_calls в поле `tool_calls_raw`, но LLM API ожидает `tool_calls` массивом объектов.
- **Результат:** Агентский цикл на втором шаге теряет историю вызовов и дублирует/ломается.
- **Действие:** При восстановлении assistant-сообщения из истории сериализовать `tool_calls` в корректный OpenAI-формат (`List<ToolCall>` → JSON массив объектов с `id`, `type`, `function`).

### 0.2 Объединить HTTP-клиенты
- **Проблема:** `AgentLoop` и `OpenAiCompatibleClient` дублируют логику HTTP/JSON.
- **Действие:** `AgentLoop` должен использовать `OpenAiCompatibleClient` для всех LLM-вызовов. Streaming поддержать через `CompletableFuture` + callback.

### 0.3 Юнит-тесты на `ToolCallParser`
- **Что тестировать:**
  - Три фолбэка (`<tool_call>`, ```json-блоки, голый JSON).
  - Стриминговый аккумулятор (частичные чанки).
  - Малформированный JSON (graceful degradation).
- **Почему сейчас:** Это чистая логика, не требует FX/UI. Если сломается при рефакторинге — агент перестанет работать.

### 0.4 Удалить или заморозить TUI и HTTP API
- **Решение:** Оставить только JavaFX клиент. TUI (269 байт заглушка) и HTTP API отложить до Фазы 4.
- **Почему:** Три клиента = три продукта. Сейчас нужен один рабочий.

---

## Фаза 1: Архитектурный фундамент (2–3 недели)

Разделение монолита и внедрение плагинной системы по образцу BDoc Editor.

### 1.1 Разбить на Gradle-модули

| Модуль | Содержимое | Зависимости |
|---|---|---|
| `solomon-seal-core` | AgentLoop, SessionManager, ToolRegistry, LLM-клиент, индекс | Jackson, SQLite JDBC, JavaParser |
| `solomon-seal-spi` | Интерфейсы: `ToolProvider`, `ContextProfileContributor`, `ConfirmationHandler`, `AppContext` | Только `core` (без JavaFX) |
| `solomon-seal-ui-spi` | JavaFX-специфичные: `ChatPanelExtension`, `ToolbarActionExtension`, `ThemeExtension`, `DebugPanelContributor` | `spi` + JavaFX |
| `solomon-seal-ui` | JavaFX реализация: ChatView, DebugPanel, MainWindow, TopBar | `ui-spi`, `core` |
| `solomon-seal-app` | Точка входа `LlmRobotApp`, загрузчик плагинов | `ui`, `core` |
| `solomon-seal-plugin-demo` | Демо-плагин (кнопка «Export to Markdown» в чат) | `ui-spi` |

### 1.2 Создать `PluginRuntime`, `PluginContext`, `PluginDescriptor`
- `PluginRuntime` — синглтон с `ModuleLayer`, паттерн из BDoc.
- `PluginDescriptor` — `id`, `namespace` (reverse-domain), `version`, `vendor`, `customData`.
- `PluginContext` — реестры: `toolProviders`, `contextProfiles`, `chatPanels`, `toolbarActions`, `themes`.
- **Валидация:** namespace должен соответствовать `^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$`.

### 1.3 Инвертировать `ToolRegistry`
- **Сейчас:** `ToolRegistry` создаёт инструменты в конструкторе.
- **Нужно:** `ToolRegistry` опрашивает `PluginContext.getToolProviders()` на старте. Встроенные инструменты (`ReadFileTool`, `EditFileTool`, `BashTool`, `WebFetchTool`) регистрируются через внутренний `BuiltInPlugin`.
- **Результат:** Плагин может добавить свой инструмент без правки `core`.

### 1.4 Загрузка `ModuleLayer` из `plugins/`
- Точка входа (`solomon-seal-app.Main`):
  1. Найти `plugins/` рядом с JAR.
  2. `ModuleFinder.of(pluginsDir)` → `Configuration` → `defineModulesWithOneLoader`.
  3. `PluginRuntime.setPluginLayer(layer)`.
  4. Запустить JavaFX.
- **Важно:** `ServiceLoader.load(PluginRuntime.getLayer(), ToolProvider.class)` — иначе плагины не обнаружатся.

### 1.5 Демо-плагин как интеграционный тест
- `solomon-seal-plugin-demo` — отдельный Gradle-модуль.
- Компилируется в JAR, кладётся в `plugins/` при `run`.
- Проверяет: загрузка слоя, `ServiceLoader`, регистрация в `PluginContext`, появление UI-элемента.
- **Без этого:** вы не узнаете, работает ли pipeline, пока не напишете реальный плагин.

---

## Фаза 2: Security & Sandbox (2 недели)

### 2.1 Redaction Pipeline
- **Место:** между `DeterministicContextAssembler` и `ContextBuilder`.
- **Уровни:**
  1. Статические regex (`sk-...`, `AIza...`, hex64).
  2. Динамический — значения из `SecretsManager` по ключам.
  3. Обратная замена не производится (плейсхолдеры остаются в логах).
- **UI:** DebugPanel показывает `<SECRET>` с tooltip «redacted: OPENAI_API_KEY».
- **Тест:** Payload с внедрённым секретом → проверка, что секрет не попал в строку запроса.

### 2.2 Capability-based Execution
- **Заменить `BashTool` на маршрутизатор:**
  ```java
  interface ExecAdapter {
      boolean supports(String command);
      ToolResult execute(String command, Path cwd);
      Optional<String> getUndoCommand(String command); // для checkpointing
  }
  ```
- **Адаптеры по умолчанию:** `GitAdapter`, `MavenAdapter`, `GradleAdapter`.
- **UnsafeBashAdapter:** требует `capabilities.exec.unsafe=true` в конфиге.
- **Файловый sandbox:** `WriteFileTool` и `EditFileTool` резолвят пути относительно `project.root`, блокируют `../`.

### 2.3 Permission Matrix в UI
- Галочки в DebugPanel/Settings:
  - [x] Allow read
  - [x] Allow write (in project dir)
  - [ ] Allow exec (via adapters only)
  - [ ] Allow unsafe exec
  - [ ] Allow network (WebFetchTool)
- При попытке нарушения — диалог с объяснением, почему заблокировано.

---

## Фаза 3: Checkpointing & History (2–3 недели)

### 3.1 Operation Log
- **Хранение:** SQLite таблица `operations` в `.solomonseal/history.db` (project-bound).
- **Схема:**
  ```sql
  CREATE TABLE operations (
      id INTEGER PRIMARY KEY,
      session_id TEXT,
      step_number INTEGER,
      tool_name TEXT,
      file_path TEXT,
      operation_type TEXT, -- WRITE | EDIT | DELETE | EXEC
      old_content TEXT,    -- полный текст для WRITE/DELETE
      diff TEXT,           -- unified diff для EDIT
      undo_command TEXT,   -- для EXEC
      irreversible BOOLEAN,
      timestamp INTEGER
  );
  ```

### 3.2 CheckpointingToolRegistry
- Обёртка над `ToolRegistry`:
  - Перед `execute` — `capturePreState()` (читает old_content/diff).
  - После `execute` — `commit()` в SQLite.
- **Exec:** если адаптер вернул `undoCommand` — сохранить, иначе `irreversible=true`.

### 3.3 Timeline в DebugPanel
- Интерактивный список шагов агента.
- Каждый шаг: иконка, tool_name, краткое summary, кнопки [Diff] [Undo] [Redo].
- Дерево (не стек): при откате на шаг N и новом действии — создаётся новая ветка.

### 3.4 Session Replay
- Возможность воспроизвести все tool_calls из истории без LLM.
- Полезно для отладки: «почему агент сломался на шаге 7?»

---

## Фаза 4: Перестройка UI (2 недели)

### 4.1 Динамическая сборка UI из расширений
- `MainWindow` больше не создаёт кнопки вручную.
- `ToolbarBuilder` опрашивает `PluginContext.getToolbarActions()` + `ServiceLoader.load(ui-spi)`.
- `MenuBuilder` — аналогично для `MenuActionExtension`.
- `DebugPanelManager` — собирает вкладки от `DebugPanelContributor`.

### 4.2 ModeSwitchExtension
- `TopBar` не хардкодит Ask/Plan/Build.
- Режимы регистрируются через `ModeSwitchExtension` (встроенные — через `BuiltInPlugin`).
- Плагин может добавить «Review» или «Test» режим.

### 4.3 Темы через CSS + ThemeExtension
- Перевести `Styles.java` с inline-стилей на CSS-файлы.
- `Styles.java` — только константы имён CSS-классов.
- `ThemeExtension` позволяет плагину подменить CSS.

### 4.4 @-syntax в чате
- Пользователь пишет: `@file:src/Main.java` или `@symbol:MyClass#method`.
- Парсер чата резолвит через `ProjectIndex` и добавляет в контекст.
- Альтернатива ручному выбору `ContextProfile`.

---

## Фаза 5: Конкурентные фичи (по приоритету)

### 5.1 Cost Tracker (неделя)
- Running counter токенов за сессию.
- Учёт модели + прайса (из конфига).
- Отображение в статус-баре: «~$0.42 this session».

### 5.2 Diff-based editing для локальных моделей (неделя)
- Два режима в настройках:
  - `smart` — native tool calling (Claude/GPT).
  - `compat` — unified diff/XML в тексте (Ollama, локальные).
- `ToolCallParser` добавляет diff-парсер как 4-й фолбэк.
- System prompt автоматически подстраивается под режим.

### 5.3 Review Mode (неделя)
- Новый режим (через `ModeSwitchExtension`).
- Агент получает `git diff` и только комментирует, не правит.
- Безопасный режим для code review.

### 5.4 Post-action Hooks (неделя)
- После `WriteFileTool`/`EditFileTool` — автозапуск `mvn test` или линтера, если затронуты тестируемые файлы.
- Настраивается в `.solomonseal/hooks.yaml`.

### 5.5 Sub-agents (2–3 недели, сложно)
- Мастер-агент (Plan) координирует рабочих-агентов.
- Каждый рабочий — своя сессия, свой контекст, свой checkpoint-лог.
- Результат мёржится через `CheckpointManager`.

---

## Что НЕ делать (anti-roadmap)

| Фича | Почему нет |
|---|---|
| Voice mode | Не критично для desktop coding tool, можно через OS |
| Browser automation | Уже есть `WebFetchTool`, полноценный браузер — оверкилл |
| Real-time collaboration | Не целевой сценарий |
| Собственная модель (fine-tune) | Дорого, не даёт преимущества для harness |
| Mobile клиент | JavaFX не портируется, отдельный проект |
| Поддержка всех IDE (VS Code extension) | Не целевой сценарий, SolomonSeal — standalone tool |

---

## Критерии готовности MVP

1. **Архитектура:** модули `core`, `spi`, `ui-spi`, `ui`, `app`, `plugin-demo` собираются.
2. **Плагины:** демо-плагин загружается из `plugins/` и добавляет UI-элемент.
3. **Security:** redaction pipeline маскирует `sk-...` в payload.
4. **Sandbox:** `BashTool` работает только через адаптеры, unsafe exec требует explicit enable.
5. **Undo:** можно откатить отдельный tool_call из DebugPanel.
6. **Тесты:** `ToolCallParser`, `PluginContext`, `CheckpointManager` покрыты юнит-тестами.
7. **Стабильность:** 10 последовательных сессий Plan→Build без падений.

---

## Оценка времени

| Фаза | Срок | Результат |
|---|---|---|
| Фаза 0 | 1–2 недели | Исправлены баги, есть тесты, один клиент |
| Фаза 1 | 2–3 недели | Модульная архитектура, плагины грузятся |
| Фаза 2 | 2 недели | Секреты не утекают, exec контролируется |
| Фаза 3 | 2–3 недели | Operation-level undo, timeline, replay |
| Фаза 4 | 2 недели | Динамический UI, темы, @-syntax |
| Фаза 5 | 4–6 недель | Cost tracker, diff fallback, review mode, hooks |
| **Итого MVP** | **~10 недель** | Полноценный продукт с уникальными фичами |

---

## Уникальные фичи SolomonSeal (чего нет у конкурентов)

1. **Детерминированная сборка контекста** без LLM-оркестратора (`DeterministicContextAssembler`).
2. **SQLite-индекс с FTS + JavaParser** — быстрый символьный поиск по проекту.
3. **Встроенный WebFetchTool** — редкость в desktop tools.
4. **Режимы Ask/Plan/Build** с жёсткими permission-границами (`ToolPermission`).
5. **Operation-level undo** без git — selective revert любого шага агента.
6. **Прозрачность cost + payload** — пользователь видит всё до отправки.
7. **Плагинная система** — расширяемость инструментов, UI и режимов.
