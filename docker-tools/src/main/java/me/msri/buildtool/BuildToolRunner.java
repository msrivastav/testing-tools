package me.msri.buildtool;

import java.util.Map;
import java.util.Optional;

public interface BuildToolRunner {

    boolean isValidService(String serviceName);

    // Image name, Image tag
    Optional<Map.Entry<String, String>> createSpringBootImage(String projectName);

    // Jar path, Jar name
    Optional<Map.Entry<String, String>> createSpringBootJar(String projectName);
}
