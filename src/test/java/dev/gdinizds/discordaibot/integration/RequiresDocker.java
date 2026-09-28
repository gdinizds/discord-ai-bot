package dev.gdinizds.discordaibot.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(RequiresDocker.Condition.class)
public @interface RequiresDocker {

    class Condition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            boolean available;
            try {
                available = DockerClientFactory.instance().isDockerAvailable();
            } catch (Exception | Error e) {
                available = false;
            }
            return available
                    ? ConditionEvaluationResult.enabled("Docker is available")
                    : ConditionEvaluationResult.disabled("Docker daemon is not running or accessible");
        }
    }
}

