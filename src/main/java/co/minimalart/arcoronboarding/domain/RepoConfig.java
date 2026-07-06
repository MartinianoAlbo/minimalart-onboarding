package co.minimalart.arcoronboarding.domain;

/** A git repository plus the path (relative to the WP root) it is cloned into. */
public record RepoConfig(String url, String branch, String relativePath) {}
