package de.Z7534.ztone.stoneattack.modules.playerlimit;

import de.Z7534.ztone.stoneattack.Stoneattack;
import de.Z7534.ztone.stoneattack.util.TimeUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rein informatives, taeglich rotierendes Logging der Join-
 * Reihenfolge. Laeuft komplett async, damit der Main-Thread nie
 * auf Datei-I/O wartet.
 */
public class JoinOrderLogger {

    private final Stoneattack plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Stoneattack-JoinOrderLogger");
        t.setDaemon(true);
        return t;
    });
    private final File logDir;

    public JoinOrderLogger(Stoneattack plugin) {
        this.plugin = plugin;
        this.logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }

    public void logJoin(UUID uuid, String name, int position) {
        executor.submit(() -> write(String.format("[%s] JOIN name=%s uuid=%s position=%d",
                Instant.now(), name, uuid, position)));
    }

    public void logLeave(UUID uuid, String name) {
        executor.submit(() -> write(String.format("[%s] LEAVE name=%s uuid=%s",
                Instant.now(), name, uuid)));
    }

    private void write(String line) {
        File file = new File(logDir, TimeUtil.todayLogFileName("playerlimit"));
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte Join-Order-Log nicht schreiben: " + e.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
