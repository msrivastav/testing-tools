package me.msri.buildtool.gradle;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GradleClientProvider {
    private static GradleClientProvider gradleClientProvider;

    private final GradleConnector connector;

    private GradleClientProvider() {
        connector = GradleConnector.newConnector();
    }

    public static GradleClientProvider newInstance() {
        if (gradleClientProvider == null ){
            gradleClientProvider = new GradleClientProvider();
        }
        return gradleClientProvider;
    }

    public ProjectConnection getConnection() {
        return connector.connect();
    }

    public ProjectConnection getConnectionForProject(final String projectBasePath) {
        return connector.forProjectDirectory(new File(projectBasePath)).connect();
    }
}
