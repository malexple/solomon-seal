package ru.mcs.solomonseal.core.plugin;

import ru.mcs.solomonseal.spi.AgentTool;
import ru.mcs.solomonseal.spi.AppContext;
import ru.mcs.solomonseal.spi.ConfirmationHandler;
import ru.mcs.solomonseal.spi.ContextProfileContributor;
import ru.mcs.solomonseal.spi.PluginDescriptor;
import ru.mcs.solomonseal.spi.ToolProvider;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Центральная точка регистрации плагинов (ядро).
 * UI-расширения регистрируются через отдельный {@link ru.mcs.solomonseal.ui.spi.UiPluginContext}.
 */
public class PluginContext {
    private static final PluginContext INSTANCE = new PluginContext();

    private final List<ToolProvider> toolProviders = new CopyOnWriteArrayList<>();
    private final List<ContextProfileContributor> profileContributors = new CopyOnWriteArrayList<>();
    private final Map<String, PluginDescriptor> descriptors = new LinkedHashMap<>();

    public static PluginContext getInstance() { return INSTANCE; }

    public void registerToolProvider(ToolProvider p) {
        toolProviders.add(p);
    }

    public void registerProfileContributor(ContextProfileContributor c) {
        profileContributors.add(c);
    }

    public void registerPlugin(PluginDescriptor d) {
        descriptors.put(d.id(), d);
    }

    public Collection<PluginDescriptor> getPlugins() {
        return descriptors.values();
    }

    public List<AgentTool> collectTools(AppContext context) {
        var result = new ArrayList<AgentTool>();
        for (var provider : toolProviders) {
            result.addAll(provider.provideTools(context));
        }
        return result;
    }

    public Map<String, Object> collectProfiles(AppContext context) {
        var map = new LinkedHashMap<String, Object>();
        for (var contributor : profileContributors) {
            map.putAll(contributor.contributeProfiles(context));
        }
        return map;
    }
}
