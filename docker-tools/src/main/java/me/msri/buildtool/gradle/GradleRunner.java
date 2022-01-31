package me.msri.buildtool.gradle;

import me.msri.buildtool.BuildToolRunner;

public class GradleRunner implements BuildToolRunner {
    @Override
    public boolean isValidService(String serviceName) {
        return false;
    }
}
