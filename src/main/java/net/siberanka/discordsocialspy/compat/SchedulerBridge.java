package net.siberanka.discordsocialspy.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Uses Folia schedulers when present and Bukkit's scheduler on legacy Paper. */
public final class SchedulerBridge {

    private final Plugin plugin;
    private final Method getGlobalScheduler;
    private final Method globalRun;
    private final Method getEntityScheduler;
    private final Method entityRun;
    private final AtomicBoolean warned = new AtomicBoolean();

    public SchedulerBridge(Plugin plugin) {
        this.plugin = plugin;
        Method detectedGlobalGetter = null;
        Method detectedGlobalRun = null;
        Method detectedEntityGetter = null;
        Method detectedEntityRun = null;
        try {
            detectedGlobalGetter = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            detectedGlobalRun = detectedGlobalGetter.getReturnType().getMethod("run", Plugin.class, Consumer.class);

            detectedEntityGetter = Player.class.getMethod("getScheduler");
            Class<?> entitySchedulerClass = detectedEntityGetter.getReturnType();
            detectedEntityRun = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            detectedGlobalGetter = null;
            detectedGlobalRun = null;
            detectedEntityGetter = null;
            detectedEntityRun = null;
        }
        getGlobalScheduler = detectedGlobalGetter;
        globalRun = detectedGlobalRun;
        getEntityScheduler = detectedEntityGetter;
        entityRun = detectedEntityRun;
    }

    public boolean isFoliaSchedulerAvailable() {
        return globalRun != null && entityRun != null;
    }

    public void runGlobal(Runnable action) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (globalRun != null) {
            try {
                Object scheduler = getGlobalScheduler.invoke(Bukkit.getServer());
                globalRun.invoke(scheduler, plugin, (Consumer<Object>) ignored -> safeRun(action));
                return;
            } catch (IllegalAccessException | InvocationTargetException failure) {
                warnOnce(failure);
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> safeRun(action));
    }

    public void runForPlayer(Player player, Runnable action) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (entityRun != null) {
            try {
                Object scheduler = getEntityScheduler.invoke(player);
                entityRun.invoke(scheduler, plugin, (Consumer<Object>) ignored -> safeRun(action), null);
                return;
            } catch (IllegalAccessException | InvocationTargetException failure) {
                warnOnce(failure);
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                safeRun(action);
            }
        });
    }

    private void safeRun(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("Scheduled task failed safely: " + safeMessage(failure));
        }
    }

    private void warnOnce(Exception failure) {
        if (warned.compareAndSet(false, true)) {
            plugin.getLogger().warning("Modern scheduler invocation failed; using the legacy fallback: "
                    + safeMessage(failure));
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName()
                : message.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
    }
}
