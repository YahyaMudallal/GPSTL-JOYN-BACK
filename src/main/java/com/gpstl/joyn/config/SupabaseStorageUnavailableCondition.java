package com.gpstl.joyn.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

final class SupabaseStorageUnavailableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        if (!"supabase".equalsIgnoreCase(environment.getProperty("joyn.storage.provider", "supabase"))) {
            return false;
        }
        return !hasValue(environment.getProperty("joyn.storage.supabase.endpoint"))
                || !hasValue(environment.getProperty("joyn.storage.supabase.region"))
                || !hasValue(environment.getProperty("joyn.storage.supabase.access-key"))
                || !hasValue(environment.getProperty("joyn.storage.supabase.secret-key"));
    }

    private static boolean hasValue(String value) {
        return StringUtils.hasText(value);
    }
}
