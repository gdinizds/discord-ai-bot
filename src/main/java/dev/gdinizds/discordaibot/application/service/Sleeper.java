package dev.gdinizds.discordaibot.application.service;

import java.time.Duration;

@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;

    Sleeper SYSTEM = duration -> Thread.sleep(duration);
}

