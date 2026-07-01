package ru.mcs.solomonseal.core.tools;

import ru.mcs.solomonseal.core.session.SessionMode;

import java.util.*;

/**
 * Реестр инструментов.
 * Фильтрует по режиму сессии: ASK → только READ_ONLY, PLAN → READ_ONLY, BUILD → все.
 */
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        // READ_ONLY
        register(new ReadFileTool());
        register(new GrepTool());
        register(new GlobTool());
        register(new ListTreeTool());
        register(new FindSymbolTool());
        register(new SearchCodeTool());
        register(new WebFetchTool());
        // WRITE
        register(new WriteFileTool());
        register(new EditFileTool());
        register(new DeleteFileTool());
        // EXEC
        register(new BashTool());
    }

    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    /** Возвращает инструменты доступные в данном режиме */
    public List<AgentTool> forMode(SessionMode mode) {
        return tools.values().stream()
                .filter(t -> switch (mode) {
                    case ASK   -> t.permission() == ToolPermission.READ_ONLY;
                    case PLAN  -> t.permission() == ToolPermission.READ_ONLY;
                    case BUILD -> true;
                })
                .toList();
    }

    public Optional<AgentTool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }
}