package me.msri.buildtool.gradle;

import java.util.Set;

public record ProjectPathAndTasks(String projectPath, Set<String> tasks) {
}
