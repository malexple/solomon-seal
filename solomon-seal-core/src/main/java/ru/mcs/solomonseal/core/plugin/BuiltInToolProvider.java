package ru.mcs.solomonseal.core.plugin;

import ru.mcs.solomonseal.spi.AgentTool;
import ru.mcs.solomonseal.spi.AppContext;
import ru.mcs.solomonseal.spi.ToolProvider;

import java.util.List;

public class BuiltInToolProvider implements ToolProvider {
    @Override
    public List<AgentTool> provideTools(AppContext context) {
        return List.of(
            // Built-in tools will be registered here
        );
    }
}
