package me.msri.buildtool.maven;

import java.util.Map;
import java.util.Optional;

import me.msri.buildtool.BuildToolRunner;

public class MavenWrapperRunner implements BuildToolRunner {
    @Override
    public boolean isValidService(String serviceName) {
        return false;
    }

    @Override
    public Optional<Map.Entry<String, String>> createSpringBootImage(String projectName) {
        return null;
    }

    @Override
    public Optional<Map.Entry<String, String>> createSpringBootJar(String projectName) {
        return null;
    }
}
