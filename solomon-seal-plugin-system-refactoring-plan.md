# SolomonSeal — План рефакторинга UI и плагинной системы

> Цель: сделать клиентский слой (JavaFX UI + будущие TUI/API) расширяемым через плагины по образцу BDoc Editor.

---

## 1. Разбить монолит на Gradle-модули (must have)

Сейчас SolomonSeal — один модуль, одно `module-info.java`, всё в одном `src/main/java`. Для поддержки плагинов нужно разделение.

| Модуль | Что внутри | Аналог в BDoc |
|---|---|---|
| `solomon-seal-core` | SessionManager, OrchestratorService, AgentLoop, инструменты, индекс, LLM-клиент | `bdoc-core` |
| `solomon-seal-spi` | Интерфейсы плагинов: ToolProvider, ChatPanelExtension, ContextAssemblerExtension, ThemeExtension | `bdoc-spi` |
| `solomon-seal-ui` | JavaFX UI: ChatView, DebugPanel, ProjectsPanel и т.д. | `bdoc-ui` |
| `solomon-seal-app` | Точка входа (LlmRobotApp) + загрузчик плагинов (Main) | `bdoc-app` |

**Зачем:** без этого `module-info.java` не даст плагинам видеть SPI-интерфейсы, а `PluginRuntime` с `ModuleLayer` не заработает.

---

## 2. Вынести контракты расширений в отдельный `solomon-seal-spi`

В BDoc SPI живёт отдельно и экспортирует `ToolbarActionExtension`, `PropertiesPanelFactory`, `DtpToolStrategy`, `EditorContext`. Для SolomonSeal нужны свои точки расширения, специфичные для LLM-оркестратора:

- **ToolProvider** — регистрация кастомных инструментов (сейчас `ToolRegistry` хардкодит список в конструкторе).
- **ContextProfileContributor** — добавление новых профилей контекста (помимо MINIMAL/SELECTION/...).
- **ChatPanelExtension** — встраивание дополнительных вкладок/панелей в UI чата (аналог `ToolWindowExtension` в BDoc).
- **DebugPanelContributor** — добавление секций в debug-панель.
- **ConfirmationHandler** — уже есть, но должен быть в SPI, чтобы плагин мог подменить UI-подтверждение на своё (например, TUI вместо JavaFX-диалога).
- **ThemeExtension** — если захотите динамические темы, как в BDoc.

**Важно:** SPI не должен зависеть от JavaFX — только от `core`. UI-специфичные расширения принимают `Parent` или абстрактный контекст, а не `javafx.scene.Node`.

---

## 3. Создать PluginContext — микро-контейнер расширений

В BDoc это синглтон `PluginContext`, который держит реестры `toolRegistry`, `propertiesFactories`, `validatorRegistry`, `lifecycleListeners`.

В SolomonSeal аналог:
- `PluginContext.registerToolProvider(...)`
- `PluginContext.registerContextProfileContributor(...)`
- `PluginContext.registerChatPanelExtension(...)`

**Важно:** `ToolRegistry` сейчас создаёт инструменты в конструкторе. Нужно инвертировать: `ToolRegistry` опрашивает `PluginContext` на старте, а встроенные инструменты регистрируются как «встроенный плагин» через тот же механизм.

---

## 4. PluginRuntime + загрузка ModuleLayer из plugins/

Прямо копировать паттерн из BDoc:

```java
public final class PluginRuntime {
    private static volatile ModuleLayer pluginLayer;
    public static void setPluginLayer(ModuleLayer layer) { pluginLayer = layer; }
    public static ModuleLayer getLayer() {
        return pluginLayer != null ? pluginLayer : ModuleLayer.boot();
    }
}
```

Точка входа в `solomon-seal-app` (`Main.main`) должна:
1. Найти `plugins/` рядом с JAR.
2. Собрать `ModuleFinder`, `Configuration`, `defineModulesWithOneLoader`.
3. Вызвать `PluginRuntime.setPluginLayer(...)`.
4. Запустить JavaFX Application.

**Отличие от BDoc:** у вас есть TUI-заглушка. Решите — будет ли `PluginRuntime` один для обоих клиентов, или TUI выкидывается. Если оставляете оба — plugin layer должен создаваться до форка UI, иначе TUI не увидит плагины.

---

## 5. Исправить ServiceLoader — указывать слой

В BDoc `UIManager` делает ошибку:
```java
ServiceLoader.load(ToolbarActionExtension.class) // ОШИБКА: ищет в boot layer
```

В SolomonSeal сразу делать правильно:
```java
ServiceLoader.load(PluginRuntime.getLayer(), ToolProvider.class)
```

Иначе плагины из `plugins/` не будут обнаружены.

---

## 6. Перестроить UI от «зашитого» к «собираемому из расширений»

Сейчас `MainWindow` и `ChatView` собирают интерфейс вручную. Нужно перейти к паттерну BDoc `UIManager` + `ToolManager` + `PropertiesPanelManager`:

- **UIManager** (или `MainWindow`) опрашивает `PluginContext` и `ServiceLoader` для:
  - Добавления кнопок в тулбар (аналог `ToolbarActionExtension`).
  - Добавления пунктов в меню (аналог `MenuActionExtension`).
  - Добавления боковых панелей (аналог `ToolWindowExtension`).
- **PropertiesPanelManager** — сейчас в SolomonSeal нет свойств выбранного объекта, но есть DebugPanel. Можно сделать `DebugPanelManager`, который собирает вкладки от плагинов.
- **ToolManager** — уже есть в BDoc, в SolomonSeal аналогично: не хардкодить список инструментов, а строить палитру из `PluginContext.getRegisteredTools()`.

**Конкретный пример:** сейчас `TopBar.java` создаёт кнопки режимов Ask/Plan/Build вручную. Если сделать `ModeSwitchExtension`, плагин сможет добавить свой режим (например, «Review» или «Test») без правки `TopBar`.

---

## 7. Определить контекст плагина (AppContext / SealContext)

В BDoc `EditorContext` — это мост между плагином и ядром:
```java
public interface EditorContext {
    DocumentHandle getDocument();
    void setStatusText(String text);
}
```

В SolomonSeal нужен `AppContext` или `SealContext`:
```java
public interface AppContext {
    Session getActiveSession();
    void sendMessage(String text);
    void setStatus(String text);
    ToolRegistry getToolRegistry();
}
```

Плагин получает `AppContext` в `execute()` и не лезет напрямую в JavaFX-классы.

---

## 8. Продумать namespacing и sandbox для плагинов

В BDoc `PluginDescriptor` требует `namespace` (reverse-domain) и валидирует `customData`-ключи.

В SolomonSeal аналогично:
- Каждый плагин обязан иметь namespace (`org.example.myplugin`).
- `PluginDescriptor` с `id`, `namespace`, `version`, `vendor`.
- Если плагин хранит кастомные настройки в `Session` или `AppConfig` — ключи должны начинаться с `namespace.`.

Это защитит от коллизий, когда два плагина решат положить данные в один `customData`.

---

## 9. Вынести темы из хардкода в ThemeExtension

В BDoc `ThemeManager` + CSS-файлы (`base.css`, `obsidian-ink.css`, `paper-matte.css`) + `ThemeExtension` позволяют плагину принести свою тему.

В SolomonSeal `Styles.java` сейчас содержит inline-стили:
```java
public static final String CHAT_BG = "-fx-background-color: #1e1e1e;";
```

Это нерасширяемо. Нужно:
- Перейти на CSS-файлы.
- Сделать `ThemeExtension`, чтобы плагин мог подменить CSS.
- `Styles.java` оставить только как константы имён CSS-классов, не значений.

---

## 10. Решить вопрос с TUI и HTTP API

В README SolomonSeal заявлено три клиента: JavaFX, CLI/TUI, HTTP API. Если внедрять плагины по образцу BDoc (JavaFX-ориентированные `ToolbarActionExtension`, `ToolWindowExtension`), то:
- TUI и HTTP API не смогут использовать большинство расширений.
- Или придётся дублировать SPI (один для core, один для JavaFX).

**Рекомендация:** разделить SPI на два уровня:
- **`solomon-seal-spi`** — чистое core: `ToolProvider`, `ContextProfileContributor`, `ConfirmationHandler`.
- **`solomon-seal-ui-spi`** — JavaFX-специфичное: `ToolbarActionExtension`, `ChatPanelExtension`, `ThemeExtension`.

TUI и HTTP API зависят только от `solomon-seal-spi`.

---

## 11. Создать демо-плагин

В BDoc есть `bdoc-plugin-demo-toolbar` — отдельный Gradle-модуль, который:
- Компилируется в JAR.
- Кладётся в `plugins/` при запуске.
- Проверяет, что весь pipeline работает.

В SolomonSeal нужен аналогичный `solomon-seal-plugin-demo` (например, плагин, который добавляет в чат кнопку «Export to Markdown»). Без него вы не узнаете, что `ServiceLoader` ищет в boot layer, пока не попробуете на реальном плагине.

---

## 12. Тесты для plugin-инфраструктуры

В BDoc нет тестов, но `PluginContext` с `ModuleLayer` — это именно та часть, которую легко сломать рефакторингом.

Минимум:
- Юнит-тест на `PluginContext.registerPlugin` с невалидным namespace.
- Интеграционный тест, который собирает `ModuleLayer` из тестового JAR и проверяет, что `ServiceLoader` его видит.

---

## Итог: порядок действий

1. Разбить на модули (`core`, `spi`, `ui`, `app`).
2. Вынести `ToolRegistry` из конструктора в `PluginContext` + сделать `ToolProvider` интерфейсом.
3. Создать `PluginRuntime`, `PluginDescriptor`, `PluginContext` по образцу BDoc.
4. Переписать точку входа (`Main`) на создание `ModuleLayer` из `plugins/`.
5. Перестроить `MainWindow`/`TopBar`/`ChatView` на динамическую сборку из `PluginContext` + `ServiceLoader`.
6. Создать демо-плагин и проверить, что он грузится из `plugins/`.
7. Только потом — добавлять новые `ExtensionPoint` (темы, debug-панели, режимы).

---

## 13. Redaction Pipeline — маскирование секретов перед отправкой в LLM

У вас уже есть `SecretsManager` + `ConfigManager`. Проблема не в хранении, а в **утечке через контекст**: если в коде или конфиге есть `api_key="sk-..."`, модель видит его при любом `read_file` или `PROJECT_SUMMARY`.

### Уровни фильтрации

| Уровень | Что делает | Пример |
|---|---|---|
| **Статический** | Замена известных паттернов на плейсхолдеры | `sk-abc123` → `<API_KEY>` |
| **Семантический** | Поиск по ключам в `SecretsManager` | любое значение, связанное с ключом `openai_api_key`, заменяется |
| **Обратный** | При получении ответа от LLM плейсхолдеры не раскрываются — они нужны только для human-readable логов |

### Регулярные выражения (достаточно простые)

```
\b(sk-[a-zA-Z0-9]{48})\b           # OpenAI
\b(AIza[0-9A-Za-z_-]{35})\b        # Google
\b([0-9a-f]{64})\b                  # Generic hex secret
\b([A-Za-z0-9+/]{40,}={0,2})\b     # Base64-like (агрессивный)
```

**Важный нюанс:** LLM может увидеть секрет **косвенно** — не сам ключ, а его использование в коде:
```java
var client = new OpenAiClient(System.getenv("OPENAI_KEY"));
```
Тут ключ не виден, но имя переменной подсказывает. Для параноика — можно маскировать и имена переменных-носителей секретов.

### Где врезать фильтр

Лучшее место — между `DeterministicContextAssembler` и `ContextBuilder`: после того как собран payload, но до `TokenEstimator`. Фильтр работает на уровне строк, не на уровне AST.

---

## 14. Sandbox & Capability-based Execution

Ваш `BashTool` сейчас требует `approve/deny`, но сам факт наличия `exec` — это большая поверхность атаки.

### Модель: capability-based security

| Capability | По умолчанию | Как включить |
|---|---|---|
| `read` | ✅ | Всегда |
| `write` | ✅ (в рабочей директории) | Всегда, но sandboxed |
| `exec` | ❌ | Только через явный `--allow-exec` или конфиг профиля |
| `network` | ❌ | Только через `--allow-network` (для `WebFetchTool`) |
| `env` | ❌ | Чтение env vars только из allow-list |

### Практический компромисс: adapter-based execution

Вместо полноценного sandbox-а сделать **adapter-based execution**:

```java
interface ExecAdapter {
    boolean supports(String command);
    ToolResult execute(String command, Path cwd);
}

class GitAdapter implements ExecAdapter { ... }
class MavenAdapter implements ExecAdapter { ... }
class UnsafeBashAdapter implements ExecAdapter { ... } // требует explicit enable
```

`BashTool` не вызывает `Runtime.exec()` напрямую — он маршрутизирует через адаптеры. Если ни один не подошёл — команда отклонена. Это проще, чем seccomp или Docker, и даёт контроль.

**Уровни sandbox для Java-приложения:**
1. **JVM Security Manager** (deprecated, но работает) — запрет `Runtime.exec()`.
2. **ProcessBuilder с явным whitelist** — проверка команды по списку разрешённых бинариев.
3. **Рабочая директория как chroot** — все файловые операции через адаптер, резолвящий пути относительно `project.root`.
4. **Fake filesystem для write** — агент пишет в `MemoryFileSystem` (Jimfs), flush на реальный диск только после explicit approve.

---

## 15. Checkpointing & Operation-level Undo/Redo

Git — внешняя зависимость и commit-level гранулярность. Для отката **каждого tool_call** индивидуально нужна встроенная operation-level история.

### Уровни гранулярности

| Уровень | Когда создаётся | Что откатывается |
|---|---|---|
| **Session checkpoint** | Перед запуском агентского цикла | Всё, что сделала сессия |
| **Step checkpoint** | Перед каждым tool_call | Одна операция агента |
| **Operation log** | Внутри каждого инструмента | Частичный revert (например, одна замена в edit) |

**Ключевое:** пользователь может откатить **третий tool_call из пяти**, не трогая остальные. Git так не умеет.

### Модель хранения: Operation Log + Diff

Хранить не snapshot целиком, а **inverse-операцию**:

```java
record Operation(
    String type,        // "WRITE" | "EDIT" | "DELETE" | "EXEC"
    String file,        // путь относительно project root
    String oldContent,  // для WRITE/DELETE — полное содержимое
    String diff,        // для EDIT — unified diff
    String undoCommand, // для EXEC — как откатить (опционально)
    boolean irreversible
) {}
```

**Почему diff для EDIT, а не полный snapshot:**
- Компактно: правка в 2 строки = 200 байт diff, а не 50 КБ файла.
- Читаемо в DebugPanel: пользователь видит, что именно изменилось.
- Можно применить/откатить из UI одной кнопкой.

**Почему полный oldContent для WRITE/DELETE:**
- Перезаписанный файл нужно восстановить целиком.
- Delete — проще хранить содержимое, чем reverse-философию.

### Дерево чекпоинтов (vs линейный стек)

Если пользователь откатился на шаг 3, потом агент сделал новое действие — история разветвляется:

```
Start
├── Step 1: edit Main.java
├── Step 2: write Test.java
│   └── (user undo to here)
│       └── Step 2b: write NewTest.java  ← новая ветка
└── Step 3: exec mvn test  ← orphaned branch
```

### Где врезать в SolomonSeal

**1. Обёртка инструментов (CheckpointingToolRegistry)**
```java
class CheckpointingToolRegistry extends ToolRegistry {
    public ToolResult execute(String toolName, Map args, ExecutionContext ctx) {
        var checkpoint = capturePreState(toolName, args, ctx);
        var result = super.execute(toolName, args, ctx);
        checkpointManager.commit(checkpoint, result);
        return result;
    }
}
```

**2. AgentLoop — автоматические чекпоинты**
Перед каждым шагом цикла создавать checkpoint. При нажатии "Undo" — откат до конкретного checkpoint.id.

**3. DebugPanel — timeline**
```
[✓] Step 1: read_file("Config.java")        — [Preview]
[✓] Step 2: edit_file("Config.java")        — [Diff] [Undo]
[✗] Step 3: write_file("Main.java")         — [Diff] [Undo] [Redo]
[⟳] Step 4: exec("mvn test")                — [Output] ⚠️ Irreversible
```

### Проблема: exec и side effects

Exec — единственная операция, которую **нельзя откатить идеально**:
- Созданные файлы — можно удалить.
- Изменения в БД — нельзя.
- Сетевые запросы — нельзя.

**Решения:**

| Подход | Как работает |
|---|---|
| **Undo-script** | Exec-адаптер возвращает `undoCommand`. Например, если exec создал `build/`, undo = `rm -rf build/`. |
| **Irreversible marker** | Операция помечается ⚠️, откат блокируется с предупреждением. |
| **Pre-exec snapshot** | Перед exec делается snapshot всей рабочей директории. Откат = восстановить директорию целиком. |
| **Wrapper-only exec** | Разрешать exec только через известные адаптеры (git, mvn, npm), каждый знает свой undo. |

**Рекомендация:** undo-script + irreversible marker. Если адаптер не вернул undo-script — операция помечается и требует explicit approve при откате.

### Персистентность

| Режим | Где хранить | Когда сбрасывать |
|---|---|---|
| **Session-only** | In-memory | При закрытии приложения |
| **Persistent** | SQLite/файл рядом с `.solomonseal/` | Хранить N последних сессий |
| **Project-bound** | `.solomonseal/history/` | Привязано к проекту, можно откатить после перезапуска |

**Рекомендация:** project-bound хранение в SQLite. Тогда:
- Можно откатить вчерашнюю сессию после перезапуска.
- Можно сделать «Session Replay» — воспроизвести все tool_calls без LLM.

---

## 16. Режимы редактирования без Tool Calling

SolomonSeal изначально делался для проверки: сможет ли нейросеть **без tool calling** изменять код, получая команды в тексте.

### Форматы, которые работают без tool calling

| Формат | Пример | Надёжность |
|---|---|---|
| **Unified Diff** | `--- a/src/Main.java\n+++ b/src/Main.java\n@@ -1,5 +1,5 @@` | ⭐⭐⭐ Высокая — структурированный, парсится однозначно |
| **XML-теги** | `<edit file="src/Main.java">\n<replace>old</replace>\n<with>new</with>\n</edit>` | ⭐⭐⭐ Высокая — явная разметка |
| **JSON-блоки** | `{"action": "edit", "file": "...", "old": "...", "new": "..."}` | ⭐⭐ Средняя — требует экранирования |
| **Естественный язык** | «Замени строку 5 на ...» | ⭐ Низкая — требует LLM-парсер |

### Почему diff лучше JSON для редактирования

- Модели видели миллионы GitHub PRs в обучающей выборке.
- Diff содержит контекст (`@@` с окружающими строками), что защищает от смещения номеров.
- `patch` утилита существует 40+ лет и отлажена.

Ваш `ToolCallParser` уже умеет JSON. Можно добавить **diff-парсер** как четвёртый фолбэк.

### Гибридный подход: tool calling + diff fallback

1. Если модель поддерживает function calling → используем `EditFileTool` через нативные `tool_calls`.
2. Если модель не поддерживает (Ollama с маленькой моделью, локальная qwen) → в system prompt добавляем инструкцию «используй unified diff».
3. `ToolCallParser` пытается сначала нативные `tool_calls`, потом JSON, потом diff.

### Практика по моделям

| Модель | Diff без обучения | XML | Рекомендация |
|---|---|---|---|
| GPT-4 / Claude 3.5 | Отлично | Отлично | Tool calling preferred |
| Qwen2.5-Coder | Средне | Хорошо | XML в compat-режиме |
| DeepSeek-Coder | Средне | Хорошо | XML в compat-режиме |
| Локальные 7B | Плохо | Средне | Только простые задачи |

**Рекомендация:** два режима редактирования:
- `smart` = tool calling (для Claude/GPT).
- `compat` = diff/XML в тексте (для локальных моделей).

Пользователь выбирает в настройках, harness подстраивает system prompt.

---

## Итог: три линии защиты / контроля

Эти три темы (секреты, sandbox, checkpointing) — одна философия **контроля**:

| Что | Как контролировать |
|---|---|
| Что уходит в LLM | Redaction Pipeline + Context Profiles |
| Что LLM может сделать | Capability-based adapters + Permission modes |
| Как LLM делает | Tool calling vs diff fallback |
| Что сделано можно откатить | Operation log + timeline + selective undo |

SolomonSeal изначально про «прозрачность». Если добавить:
- **redaction markers** в debug panel (показать, что заменено на `<SECRET>`),
- **capability matrix** в UI (галочки read/write/exec/network),
- **diff preview** перед apply,
- **operation timeline** с selective undo,

то получится инструмент, где «печать» работает в обе стороны: и на LLM (что она может), и на пользователя (что он разрешает).
