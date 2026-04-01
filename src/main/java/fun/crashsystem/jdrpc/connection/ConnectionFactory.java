package fun.crashsystem.jdrpc.connection;

import java.io.IOException;

@FunctionalInterface
public interface ConnectionFactory {
    Connection create(String path) throws IOException;
}
