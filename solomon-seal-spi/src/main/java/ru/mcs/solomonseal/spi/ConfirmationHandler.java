package ru.mcs.solomonseal.spi;

import java.util.concurrent.CompletableFuture;

public interface ConfirmationHandler {
    CompletableFuture<Boolean> confirm(String toolName, String description, String details);
}
