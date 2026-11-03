package ru.mcs.solomonseal.spi;

import java.util.List;

public interface ToolProvider {
    List<AgentTool> provideTools(AppContext context);
}
