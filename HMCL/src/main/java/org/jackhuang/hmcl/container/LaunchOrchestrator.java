package org.jackhuang.hmcl.container;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.container.ContainerLaunchException;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.setting.Profile;

import java.io.File;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class LaunchOrchestrator {

    private LaunchOrchestrator() {
    }

    public static void doLaunch(Container container, Profile profile, Account account, String version) throws ContainerLaunchException {
        String containerPath = container.getContainerPath();
        if (containerPath == null) {
            throw new ContainerLaunchException("Container path is null");
        }
        File containerGameDir = new File(containerPath);
        if (!containerGameDir.exists()) {
            throw new ContainerLaunchException("Container path does not exist: " + containerPath);
        }
        LauncherHelper launcherHelper = new LauncherHelper(profile, account, version);
        launcherHelper.setContainerGameDir(containerGameDir);
        LaunchProfile launchProfile = ContainerManager.getInstance().loadLaunchProfile(container);
        if (launchProfile != null) {
            launcherHelper.setLaunchProfile(launchProfile);
        }
        LOG.info("Launching container " + container.getName() + " with version " + version + ", game dir: " + containerGameDir);
        launcherHelper.launch();
    }
}
