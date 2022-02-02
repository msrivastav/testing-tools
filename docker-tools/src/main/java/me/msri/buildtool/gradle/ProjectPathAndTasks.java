package me.msri.buildtool.gradle;

import java.util.Set;

public record ProjectPathAndTasks(String path, Set<String> tasks) {
}
