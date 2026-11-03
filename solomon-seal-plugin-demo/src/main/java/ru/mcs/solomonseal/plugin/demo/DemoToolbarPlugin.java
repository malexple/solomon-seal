package ru.mcs.solomonseal.plugin.demo;

import ru.mcs.solomonseal.spi.*;
import ru.mcs.solomonseal.ui.spi.ToolbarActionExtension;
import ru.mcs.solomonseal.ui.spi.UiPluginContext;

public class DemoToolbarPlugin implements ToolProvider {

    @Override
    public java.util.List<AgentTool> provideTools(AppContext context) {
        return java.util.List.of();
    }

    public static void init() {
        UiPluginContext.getInstance().registerToolbarAction(new ToolbarActionExtension() {
            @Override
            public String label() { return "Demo Action"; }
            @Override
            public String icon() { return "demo-icon"; }
            @Override
            public Runnable action() { return () -> System.out.println("Demo clicked"); }
        });
    }
}
