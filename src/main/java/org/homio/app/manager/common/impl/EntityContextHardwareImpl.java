package org.homio.app.manager.common.impl;

import java.util.ArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContextHardware;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.hquery.HQueryProgressBar;
import org.homio.hquery.hardware.other.MachineHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@RequiredArgsConstructor
public class EntityContextHardwareImpl implements EntityContextHardware {

    @Getter private final EntityContextImpl entityContext;
    private final MachineHardwareRepository repo;

    @Override
    public @NotNull String execute(String command) {
        return repo.execute(command);
    }

    @Override
    public @NotNull String executeNoErrorThrow(@NotNull String command, int maxSecondsTimeout, @Nullable HQueryProgressBar progressBar) {
        return repo.executeNoErrorThrow(command, maxSecondsTimeout, progressBar);
    }

    @Override
    public @NotNull ArrayList<String> executeNoErrorThrowList(@NotNull String command, int maxSecondsTimeout,
        @Nullable HQueryProgressBar progressBar) {
        return repo.executeNoErrorThrowList(command, maxSecondsTimeout, progressBar);
    }

    @Override
    public @NotNull String execute(@NotNull String command, @Nullable HQueryProgressBar progressBar) {
        return repo.execute(command, progressBar);
    }

    @Override
    public @NotNull String execute(@NotNull String command, int maxSecondsTimeout) {
        return repo.execute(command, maxSecondsTimeout);
    }

    @Override
    public @NotNull String execute(@NotNull String command, int maxSecondsTimeout, HQueryProgressBar progressBar) {
        return repo.execute(command, maxSecondsTimeout, progressBar);
    }

    @Override
    public boolean isSoftwareInstalled(@NotNull String soft) {
        return repo.isSoftwareInstalled(soft);
    }

    @Override
    public EntityContextHardware installSoftware(@NotNull String soft, int maxSecondsTimeout) {
        repo.installSoftware(soft, maxSecondsTimeout);
        return this;
    }

    @Override
    public EntityContextHardware installSoftware(@NotNull String soft, int maxSecondsTimeout, HQueryProgressBar progressBar) {
        repo.installSoftware(soft, maxSecondsTimeout, progressBar);
        return this;
    }

    @Override
    public EntityContextHardware enableSystemCtl(@NotNull String soft) {
        repo.enableSystemCtl(soft);
        return this;
    }

    @Override
    public EntityContextHardware startSystemCtl(@NotNull String soft) {
        repo.startSystemCtl(soft);
        return this;
    }

    @Override
    public void stopSystemCtl(@NotNull String soft) {
        repo.stopSystemCtl(soft);
    }

    @Override
    public @NotNull String getHostname() {
        return repo.getHostname();
    }

    @Override
    public int getServiceStatus(@NotNull String serviceName) {
        return repo.getServiceStatus(serviceName);
    }

    @Override
    public void reboot() {
        repo.reboot();
    }
}
