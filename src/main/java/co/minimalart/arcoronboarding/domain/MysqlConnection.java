package co.minimalart.arcoronboarding.domain;

public record MysqlConnection(String host, int port, String user, String password, String database) {}
