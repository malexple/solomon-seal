package ru.mcs.solomonseal.ui.spi;

import ru.mcs.solomonseal.spi.AppContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Контекст расширений UI (JavaFX-специфичный).
 * Живёт внутри UI-слоя, поэтому core его не видит — избегаем циклической зависимости.
 */
public class UiPluginContext {
    private static UiPluginContext INSTANCE;

    private final List<ToolbarActionExtension> toolbarActions = new CopyOnWriteArrayList<>();
    private final List<ChatPanelExtension> chatPanels = new CopyOnWriteArrayList<>();
    private final List<DebugPanelContributor> debugPanels = new CopyOnWriteArrayList<>();
    private final List<ThemeExtension> themes = new CopyOnWriteArrayList<>();
    private final List<ModeSwitchExtension> modeSwitchers = new CopyOnWriteArrayList<>();

    public static synchronized UiPluginContext getInstance() {
        if (INSTANCE == null) INSTANCE = new UiPluginContext();
        return INSTANCE;
    }

    public void registerToolbarAction(ToolbarActionExtension a) { toolbarActions.add(a); }
    public void registerChatPanel(ChatPanelExtension p) { chatPanels.add(p); }
    public void registerDebugPanel(DebugPanelContributor d) { debugPanels.add(d); }
    public void registerTheme(ThemeExtension t) { themes.add(t); }
    public void registerModeSwitcher(ModeSwitchExtension m) { modeSwitchers.add(m); }

    public List<ToolbarActionExtension> toolbarActions() { return List.copyOf(toolbarActions); }
    public List<ChatPanelExtension> chatPanels() { return List.copyOf(chatPanels); }
    public List<DebugPanelContributor> debugPanels() { return List.copyOf(debugPanels); }
    public List<ThemeExtension> themes() { return List.copyOf(themes); }
    public List<ModeSwitchExtension> modeSwitchers() { return List.copyOf(modeSwitchers); }
}
