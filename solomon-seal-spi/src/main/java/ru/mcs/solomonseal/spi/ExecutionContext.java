package ru.mcs.solomonseal.spi;

import java.nio.file.Path;

public interface ExecutionContext {
    String projectRoot();
    Session session();
    ConfirmationHandler confirmationHandler();
    Path resolvePath(String path);
}
