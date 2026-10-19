package com.gpstl.joyn.joboffer.dto;

import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.models.OfferStatus;
import com.gpstl.joyn.joboffer.models.RemoteMode;
import com.gpstl.joyn.joboffer.models.SalaryPeriod;
import com.gpstl.joyn.joboffer.models.ExperienceLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Presence-aware PATCH: omitted fields stay unchanged; nullable fields may be cleared. */
public record JobPatch(Map<String, Object> columns, List<String> skills) {
    private static final Set<String> TEXT = Set.of("title", "description", "location", "rhythm_type");
    private static final Map<String, Set<String>> ENUMS = Map.of(
        "status", enumNames(OfferStatus.values()),
        "remote_mode", enumNames(RemoteMode.values()),
        "salary_period", enumNames(SalaryPeriod.values()),
        "degree_level_required", Set.of("BAC_2", "BAC_3", "BAC_4", "BAC_5"),
        "experience_level_required", enumNames(ExperienceLevel.values()));
    private static final Set<String> NULLABLE = Set.of("location", "rhythm_type", "salary_min",
        "salary_max", "start_date", "duration_months", "degree_level_required", "experience_level_required");

    public static JobPatch parse(Map<String, Object> body) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> skills = null;
        for (var entry : body.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            try {
                boolean supported = TEXT.contains(field) || ENUMS.containsKey(field)
                    || Set.of("salary_min", "salary_max", "salary_currency", "contract_type_id",
                        "start_date", "duration_months", "skills").contains(field);
                if (!supported) throw new IllegalArgumentException("Unknown or protected field");
                if (value == null) {
                    if (!NULLABLE.contains(field)) throw new IllegalArgumentException("Must not be null");
                    values.put(field, null);
                    continue;
                }
                if (field.equals("skills")) {
                    if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected an array of skill names");
                    Map<String, String> names = new LinkedHashMap<>();
                    for (Object item : list) {
                        String name = text(item).trim();
                        names.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
                    }
                    skills = List.copyOf(names.values());
                } else if (TEXT.contains(field)) {
                    if (!(value instanceof String string)) throw new IllegalArgumentException("Expected a string");
                    if (Set.of("title", "description").contains(field) && string.isBlank())
                        throw new IllegalArgumentException("Must not be blank");
                    values.put(field, string);
                } else if (ENUMS.containsKey(field)) {
                    String string = text(value);
                    if (!ENUMS.get(field).contains(string))
                        throw new IllegalArgumentException("Allowed values: " + new TreeSet<>(ENUMS.get(field)));
                    values.put(field, string);
                } else if (field.startsWith("salary_") && !Set.of("salary_currency", "salary_period").contains(field)) {
                    if (!(value instanceof Number)) throw new IllegalArgumentException("Expected a number");
                    BigDecimal amount = new BigDecimal(value.toString());
                    if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2
                        || amount.compareTo(new BigDecimal("9999999999.99")) > 0)
                        throw new IllegalArgumentException("Expected a nonnegative NUMERIC(12,2) amount");
                    values.put(field, amount);
                } else if (field.equals("salary_currency")) {
                    String currency = text(value);
                    if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Expected a three-letter uppercase currency");
                    values.put(field, currency);
                } else if (field.equals("start_date")) {
                    values.put(field, LocalDate.parse(text(value)));
                } else {
                    if (!(value instanceof Number)) throw new IllegalArgumentException("Expected a positive integer");
                    int integer = new BigDecimal(value.toString()).intValueExact();
                    if (integer <= 0 || integer > Short.MAX_VALUE) throw new IllegalArgumentException("Expected an integer between 1 and 32767");
                    values.put(field, integer);
                }
            } catch (RuntimeException ex) {
                errors.put(field, ex.getMessage() == null ? "Invalid value" : ex.getMessage());
            }
        }
        if (!errors.isEmpty()) throw new JobApiException(400, "Invalid job patch", errors);
        return new JobPatch(Collections.unmodifiableMap(values), skills);
    }

    private static String text(Object value) {
        if (!(value instanceof String string) || string.isBlank())
            throw new IllegalArgumentException("Expected a nonblank string");
        return string;
    }

    private static Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
