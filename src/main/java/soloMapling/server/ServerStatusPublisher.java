package soloMapling.server;

import client.Character;
import net.server.Server;
import net.server.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soloMapling.ArtificialPlayer.BotHelpers;
import soloMapling.ArtificialPlayer.BotMessagingSystem.CharacterStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Publishes non-sensitive live population totals for the local MapleBit website.
 */
public final class ServerStatusPublisher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ServerStatusPublisher.class);
    private static final Path STATUS_PATH = resolveStatusPath();
    private boolean writeFailureLogged;

    @Override
    public void run() {
        int players = 0;
        for (World world : Server.getInstance().getWorlds()) {
            for (Character character : world.getPlayerStorage().getAllCharacters()) {
                if (!BotHelpers.isBot(character)) {
                    players++;
                }
            }
        }

        int bots = CharacterStorage.getAllBots().size();
        String json = String.format(
                "{\"updated_at\":%d,\"players\":%d,\"bots\":%d,\"total_online\":%d}%n",
                Instant.now().getEpochSecond(),
                players,
                bots,
                players + bots
        );

        try {
            Path parent = STATUS_PATH.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temporary = STATUS_PATH.resolveSibling(STATUS_PATH.getFileName() + ".tmp");
            Files.writeString(
                    temporary,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            try {
                Files.move(
                        temporary,
                        STATUS_PATH,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, STATUS_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            writeFailureLogged = false;
        } catch (IOException error) {
            if (!writeFailureLogged) {
                log.warn("Unable to publish server status to {}", STATUS_PATH.toAbsolutePath(), error);
                writeFailureLogged = true;
            }
        }
    }

    private static Path resolveStatusPath() {
        String configuredPath = System.getenv("MAPLE_SERVER_STATUS_FILE");
        if (configuredPath == null || configuredPath.isBlank()) {
            return Path.of("runtime", "server-status.json");
        }
        return Path.of(configuredPath);
    }
}
