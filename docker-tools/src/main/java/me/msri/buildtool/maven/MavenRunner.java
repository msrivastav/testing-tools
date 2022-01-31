package me.msri.buildtool.maven;

import me.msri.buildtool.BuildToolRunner;

public class MavenRunner implements BuildToolRunner {
    @Override
    public boolean isValidService(String serviceName) {
        return false;
    }
}
