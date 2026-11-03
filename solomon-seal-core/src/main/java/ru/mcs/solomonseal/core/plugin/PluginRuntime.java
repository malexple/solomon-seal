package ru.mcs.solomonseal.core.plugin;

import ru.mcs.solomonseal.spi.PluginDescriptor;
import ru.mcs.solomonseal.spi.ToolProvider;

import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.*;

/**
 * Загрузка плагинов из директории `plugins/` через ModuleLayer + ServiceLoader.
 */
public class PluginRuntime {

    public static List<PluginDescriptor> loadPlugins(Path pluginsDir) {
        var descriptors = new ArrayList<PluginDescriptor>();
        if (!pluginsDir.toFile().isDirectory()) return descriptors;

        var jars = pluginsDir.toFile().listFiles((d, n) -> n.endsWith(".jar"));
        if (jars == null || jars.length == 0) return descriptors;

        var paths = Arrays.stream(jars).map(f -> f.toPath().getParent()).distinct().toArray(Path[]::new);
        var finder = ModuleFinder.of(paths);
        var parent = ModuleLayer.boot();
        var cfg = parent.configuration().resolveAndBind(finder, ModuleFinder.of(), Set.of());
        var layer = parent.defineModulesWithOneLoader(cfg, ClassLoader.getSystemClassLoader());

        for (ModuleReference ref : finder.findAll()) {
            try {
                layer.findLoader(ref.descriptor().name())
                     .loadClass(ref.descriptor().name() + ".Plugin")
                     .getMethod("descriptor")
                     .invoke(null);
            } catch (Exception e) {
                // ignore
            }
        }

        var loader = ServiceLoader.load(layer, ToolProvider.class);
        for (var provider : loader) {
            PluginContext.getInstance().registerToolProvider(provider);
        }

        return descriptors;
    }
}
