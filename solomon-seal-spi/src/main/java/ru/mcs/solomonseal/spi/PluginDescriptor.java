package ru.mcs.solomonseal.spi;

import java.util.Map;
import java.util.regex.Pattern;

public record PluginDescriptor(
        String id, String namespace, String version, String vendor,
        String description, Map<String, String> customData) {

    private static final Pattern NAMESPACE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$");

    public PluginDescriptor {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Plugin id must not be blank");
        if (namespace == null || !NAMESPACE_PATTERN.matcher(namespace).matches())
            throw new IllegalArgumentException("Invalid namespace: " + namespace);
        if (version == null || version.isBlank()) version = "0.0.0";
        if (vendor == null) vendor = "";
        if (description == null) description = "";
        if (customData == null) customData = Map.of();
        for (String key : customData.keySet()) {
            if (!key.startsWith(namespace + "."))
                throw new IllegalArgumentException("Key '" + key + "' must start with '" + namespace + ".'");
        }
    }
}
