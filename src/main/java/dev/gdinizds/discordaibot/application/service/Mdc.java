package dev.gdinizds.discordaibot.application.service;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;

public final class Mdc {

    private Mdc() {}

    public static <T> Callable<T> propagate(Callable<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (context == null) return task;
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.setContextMap(context);
            try {
                return task.call();
            } finally {
                if (previous == null) MDC.clear();
                else MDC.setContextMap(previous);
            }
        };
    }
}

