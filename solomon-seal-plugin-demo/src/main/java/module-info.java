module ru.mcs.solomonseal.plugin.demo {
    requires ru.mcs.solomonseal.spi;
    requires ru.mcs.solomonseal.ui.spi;

    provides ru.mcs.solomonseal.spi.ToolProvider
        with ru.mcs.solomonseal.plugin.demo.DemoToolbarPlugin;
}
