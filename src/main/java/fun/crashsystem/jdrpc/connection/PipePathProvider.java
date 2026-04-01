package fun.crashsystem.jdrpc.connection;

import java.util.List;

@FunctionalInterface
public interface PipePathProvider {
    List<String> locateAll();
}