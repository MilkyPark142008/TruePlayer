package me.yourname.trueplayer;

import me.yourname.trueplayer.command.PlayerCommand;
import me.yourname.trueplayer.fake.FakePlayerManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TruePlayerPlugin extends JavaPlugin {

    private FakePlayerManager fakePlayerManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.fakePlayerManager = new FakePlayerManager(this);

        PlayerCommand command = new PlayerCommand(this, fakePlayerManager);
        if (getCommand("player") != null) {
            getCommand("player").setExecutor(command);
            getCommand("player").setTabCompleter(command);
        }

        getLogger().info("TruePlayer enabled.");
    }

    @Override
    public void onDisable() {
        if (fakePlayerManager != null) {
            fakePlayerManager.removeAll();
        }
        getLogger().info("TruePlayer disabled.");
    }

    public FakePlayerManager getFakePlayerManager() {
        return fakePlayerManager;
    }
}
