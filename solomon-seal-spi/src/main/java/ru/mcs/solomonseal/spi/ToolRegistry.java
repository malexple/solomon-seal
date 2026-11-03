package ru.mcs.solomonseal.spi;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    Optional<AgentTool> get(String name);
    Collection<AgentTool> all();
    List<AgentTool> forMode(SessionMode mode);
}
