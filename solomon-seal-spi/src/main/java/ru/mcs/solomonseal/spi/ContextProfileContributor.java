package ru.mcs.solomonseal.spi;

import java.util.Map;

public interface ContextProfileContributor {
    Map<String, ContextProfile> contributeProfiles(AppContext context);
}
