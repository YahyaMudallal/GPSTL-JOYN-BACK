package com.gpstl.joyn.candidate.dto;

import com.gpstl.joyn.candidate.exception.CandidateProfileException;
import com.gpstl.joyn.candidate.model.Degree;
import com.gpstl.joyn.candidate.model.PostbacYear;
import com.gpstl.joyn.joboffer.model.RemoteMode;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Corps validé de PATCH /api/v1/candidates/profile, sans aucun accès à la base.
 *
 * Règles :
 *  - champ absent : conservé ; null explicite : effacé si la colonne l'autorise ;
 *  - objets imbriqués (links, alternance, preferences, visibility) : fusion champ par champ ;
 *  - listes (skills, experiences, educations, preferences.contract_types) : remplacement complet,
 *    null signifie « ne pas modifier » et [] vide la section ;
 *  - champs système ou inconnus : 400 ; toutes les erreurs sont renvoyées ensemble.
 *
 * @param values champs simples validés, clés au format JSON (« phone », « preferences.min_salary »...)
 */
public record CandidateProfilePatch(
        Map<String, Object> values,
        List<String> skills,
        List<String> contractTypes,
        List<ExperienceInput> experiences,
        List<EducationInput> educations) {

    public record ExperienceInput(String title, String organization, String description,
            LocalDate startDate, LocalDate endDate, boolean currentlyWorking) {}

    public record EducationInput(String schoolName, String schoolAddress, String fieldOfStudy,
            Degree degree, PostbacYear postbacYear, Short startYear, Short endYear, boolean currentlyStudying) {}

    // Modifiés par d'autres routes (compte, dépôt du CV) ou gérés par le serveur.
    private static final Set<String> PROTECTED = Set.of("id", "email", "role", "status", "password",
            "password_hash", "user_id", "cv_url", "resume_url", "created_at", "updated_at");
    // Même règle que la contrainte CHECK de la colonne phone (E.164).
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{6,14}$");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Pattern CONTRACT_CODE = Pattern.compile("[A-Z_]{1,50}");
    private static final int HEADLINE_MAX = 255;   // VARCHAR(255) en base
    private static final int SKILL_NAME_MAX = 100;
    private static final int LIST_MAX = 100;
    private static final long SALARY_MAX = 9_999_999_999L;   // partie entière de NUMERIC(12, 2)

    public static CandidateProfilePatch parse(Map<String, Object> body) {
        if (body == null) {
            throw new CandidateProfileException(400, "Invalid profile patch",
                    Map.of("body", "Expected a JSON object"));
        }
        Parser parser = new Parser();
        parser.parseRoot(body);
        // Regrouper les erreurs pour permettre au client de corriger plusieurs champs à la fois.
        if (!parser.errors.isEmpty()) {
            throw new CandidateProfileException(400, "Invalid profile patch", parser.errors);
        }
        return new CandidateProfilePatch(Collections.unmodifiableMap(parser.values),
                parser.skills, parser.contractTypes, parser.experiences, parser.educations);
    }

    /** Chemins JSON envoyés, dans l'ordre : champs simples puis sections remplacées (pour le journal admin). */
    public List<String> changedFields() {
        List<String> fields = new ArrayList<>(values.keySet());
        if (contractTypes != null) fields.add("preferences.contract_types");
        if (skills != null) fields.add("skills");
        if (experiences != null) fields.add("experiences");
        if (educations != null) fields.add("educations");
        return List.copyOf(fields);
    }

    /** Vrai si le cache de recommandations doit être invalidé (compétences ou préférences modifiées). */
    public boolean changesMatchingCriteria() {
        return skills != null || contractTypes != null
                || values.keySet().stream().anyMatch(key -> key.startsWith("preferences."));
    }

    private static final class Parser {
        final Map<String, String> errors = new LinkedHashMap<>();
        final Map<String, Object> values = new LinkedHashMap<>();
        List<String> skills;
        List<String> contractTypes;
        List<ExperienceInput> experiences;
        List<EducationInput> educations;

        void parseRoot(Map<String, Object> body) {
            for (var entry : body.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();
                try {
                    switch (field) {
                        case "first_name", "last_name" -> values.put(field, requiredText(value));
                        case "headline" -> values.put(field, optionalText(value, HEADLINE_MAX));
                        case "bio", "location", "address" -> values.put(field, optionalText(value, 0));
                        case "phone" -> values.put(field, phone(value));
                        case "photo_url" -> values.put(field, url(value));
                        case "years_of_experience" -> values.put(field, smallInt(value, 0));
                        case "links" -> section(field, value, Map.of(
                                "github", Parser::url,
                                "linkedin", Parser::url,
                                "portfolio", Parser::url));
                        case "alternance" -> section(field, value, Map.of(
                                "rhythm_type", v -> optionalText(v, 0),
                                "available_from", Parser::date,
                                "contract_duration_months", v -> smallInt(v, 1)));
                        case "preferences" -> section(field, value, Map.of(
                                "contract_types", v -> contractTypes = codes("preferences.contract_types", v),
                                "remote_policy", v -> enumValue(v, RemoteMode.class),
                                "min_salary", Parser::salary,
                                "currency", Parser::currency));
                        case "visibility" -> section(field, value, Map.of(
                                "visible_to_recruiters", Parser::bool,
                                "visible_to_others", Parser::bool,
                                "show_last_name", Parser::bool,
                                "show_contact_links", Parser::bool));
                        case "skills" -> skills = skillNames(field, value);
                        case "experiences" -> experiences = items(field, value, this::experience);
                        case "educations" -> educations = items(field, value, this::education);
                        default -> throw new IllegalArgumentException(PROTECTED.contains(field)
                                ? "Protected field: it cannot be modified by this route"
                                : "Unknown field");
                    }
                } catch (IllegalArgumentException ex) {
                    errors.put(field, ex.getMessage());
                }
            }
            // La liste des contrats est rangée à part : elle remplace la table de jointure.
            values.remove("preferences.contract_types");
        }

        /** Objet imbriqué : seuls les sous-champs envoyés sont modifiés. */
        private void section(String name, Object raw, Map<String, Function<Object, Object>> fields) {
            if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected an object");
            for (var entry : map.entrySet()) {
                String key = name + "." + entry.getKey();
                Function<Object, Object> parser = fields.get(String.valueOf(entry.getKey()));
                try {
                    if (parser == null) throw new IllegalArgumentException("Unknown field");
                    values.put(key, parser.apply(entry.getValue()));
                } catch (IllegalArgumentException ex) {
                    errors.put(key, ex.getMessage());
                }
            }
        }

        /** Les erreurs d'un élément portent son chemin complet, par ex. « skills[2] ». */
        private List<String> skillNames(String path, Object raw) {
            List<?> list = list(raw);
            Map<String, String> unique = new LinkedHashMap<>();
            for (int i = 0; i < list.size(); i++) {
                try {
                    String name = requiredText(list.get(i));
                    if (name.length() > SKILL_NAME_MAX)
                        throw new IllegalArgumentException("Must not exceed " + SKILL_NAME_MAX + " characters");
                    // Doublons ignorés sans tenir compte de la casse, comme l'index unique LOWER(name).
                    unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
                } catch (IllegalArgumentException ex) {
                    errors.put(path + "[" + i + "]", ex.getMessage());
                }
            }
            return List.copyOf(unique.values());
        }

        /** Le format est vérifié ici ; l'existence du code dans contract_types l'est par le service. */
        private List<String> codes(String path, Object raw) {
            List<?> list = list(raw);
            Set<String> unique = new LinkedHashSet<>();
            for (int i = 0; i < list.size(); i++) {
                try {
                    String code = requiredText(list.get(i)).toUpperCase(Locale.ROOT);
                    if (!CONTRACT_CODE.matcher(code).matches())
                        throw new IllegalArgumentException("Expected a contract type code such as CDI");
                    unique.add(code);
                } catch (IllegalArgumentException ex) {
                    errors.put(path + "[" + i + "]", ex.getMessage());
                }
            }
            return List.copyOf(unique);
        }

        private <T> List<T> items(String path, Object raw, ItemParser<T> parser) {
            List<?> list = list(raw);
            List<T> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                String itemPath = path + "[" + i + "]";
                if (list.get(i) instanceof Map<?, ?> map) {
                    result.add(parser.parse(new Item(itemPath, map)));
                } else {
                    errors.put(itemPath, "Expected an object");
                }
            }
            return result;
        }

        private ExperienceInput experience(Item item) {
            item.allow("title", "organization", "description", "start_date", "end_date", "currently_working");
            String title = item.read("title", Parser::requiredText);
            String organization = item.read("organization", v -> optionalText(v, 0));
            String description = item.read("description", v -> optionalText(v, 0));
            LocalDate start = item.read("start_date", Parser::date);
            LocalDate end = item.read("end_date", Parser::date);
            boolean current = item.readOr("currently_working", Parser::bool, false);
            if (start != null && end != null && start.isAfter(end))
                item.error("end_date", "Must not be before start_date");
            if (current && end != null)
                item.error("end_date", "Must be empty when currently_working is true");
            return new ExperienceInput(title, organization, description, start, end, current);
        }

        private EducationInput education(Item item) {
            item.allow("school_name", "school_address", "field_of_study", "degree", "postbac_year",
                    "start_year", "end_year", "currently_studying");
            String school = item.read("school_name", Parser::requiredText);
            String address = item.read("school_address", v -> optionalText(v, 0));
            String field = item.read("field_of_study", Parser::requiredText);
            Degree degree = item.read("degree", v -> requiredEnum(v, Degree.class));
            PostbacYear postbac = item.read("postbac_year", v -> enumValue(v, PostbacYear.class));
            Short start = item.read("start_year", Parser::year);
            Short end = item.read("end_year", Parser::year);
            boolean current = item.readOr("currently_studying", Parser::bool, false);
            if (start != null && end != null && start > end)
                item.error("end_year", "Must not be before start_year");
            if (current && end != null)
                item.error("end_year", "Must be empty when currently_studying is true");
            return new EducationInput(school, address, field, degree, postbac, start, end, current);
        }

        @FunctionalInterface
        private interface ItemParser<T> {
            T parse(Item item);
        }

        /** Lecture d'un élément de liste ; les erreurs sont enregistrées avec le chemin de l'élément. */
        private final class Item {
            private final String path;
            private final Map<?, ?> map;

            Item(String path, Map<?, ?> map) {
                this.path = path;
                this.map = map;
            }

            /** « id » est toléré et ignoré : le client peut renvoyer les éléments reçus par GET. */
            void allow(String... keys) {
                Set<String> allowed = new HashSet<>(Arrays.asList(keys));
                allowed.add("id");
                for (Object key : map.keySet()) {
                    if (!allowed.contains(String.valueOf(key))) error(String.valueOf(key), "Unknown field");
                }
            }

            <T> T read(String key, Function<Object, T> parser) {
                try {
                    return parser.apply(map.get(key));
                } catch (IllegalArgumentException ex) {
                    error(key, ex.getMessage());
                    return null;
                }
            }

            <T> T readOr(String key, Function<Object, T> parser, T fallback) {
                if (!map.containsKey(key)) return fallback;
                T value = read(key, parser);
                return value == null ? fallback : value;
            }

            void error(String key, String message) {
                errors.put(path + "." + key, message);
            }
        }

        // --- Conversions élémentaires : lèvent IllegalArgumentException avec un message pour le client ---

        private static List<?> list(Object raw) {
            if (raw == null) throw new IllegalArgumentException("Must not be null; send [] to remove all values");
            if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("Expected an array");
            if (list.size() > LIST_MAX) throw new IllegalArgumentException("Must not contain more than " + LIST_MAX + " items");
            return list;
        }

        private static String text(Object value) {
            if (!(value instanceof String string)) throw new IllegalArgumentException("Expected a string");
            return string.trim();
        }

        private static String requiredText(Object value) {
            if (value == null) throw new IllegalArgumentException("Required");
            String string = text(value);
            if (string.isEmpty()) throw new IllegalArgumentException("Must not be blank");
            return string;
        }

        /** Une chaîne vide vaut null : le champ est effacé. max = 0 : pas de limite. */
        private static String optionalText(Object value, int max) {
            if (value == null) return null;
            String string = text(value);
            if (string.isEmpty()) return null;
            if (max > 0 && string.length() > max) throw new IllegalArgumentException("Must not exceed " + max + " characters");
            return string;
        }

        /** Espaces, points, tirets et parenthèses sont retirés : « +33 6 12-34-56-78 » est accepté. */
        private static String phone(Object value) {
            String string = optionalText(value, 0);
            if (string == null) return null;
            String normalized = string.replaceAll("[\\s.()-]", "");
            if (!PHONE.matcher(normalized).matches())
                throw new IllegalArgumentException("Expected an international phone number, e.g. +33612345678");
            return normalized;
        }

        private static String url(Object value) {
            String string = optionalText(value, 0);
            if (string == null) return null;
            try {
                URI uri = new URI(string);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) return string;
            } catch (URISyntaxException ignored) {
                // Message commun ci-dessous.
            }
            throw new IllegalArgumentException("Expected an http or https URL");
        }

        private static LocalDate date(Object value) {
            String string = optionalText(value, 0);
            if (string == null) return null;
            try {
                return LocalDate.parse(string);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Expected a date in YYYY-MM-DD format");
            }
        }

        private static Boolean bool(Object value) {
            if (!(value instanceof Boolean bool)) throw new IllegalArgumentException("Expected true or false");
            return bool;
        }

        /** Refuse les décimales (2.5) mais accepte 2.0, que certains clients JSON envoient pour 2. */
        private static long integer(Object value) {
            if (!(value instanceof Number)) throw new IllegalArgumentException("Expected an integer");
            try {
                return new BigDecimal(value.toString()).longValueExact();
            } catch (ArithmeticException | NumberFormatException ex) {
                throw new IllegalArgumentException("Expected an integer");
            }
        }

        private static Short smallInt(Object value, int min) {
            if (value == null) return null;
            long number = integer(value);
            if (number < min || number > Short.MAX_VALUE)
                throw new IllegalArgumentException("Expected an integer between " + min + " and " + Short.MAX_VALUE);
            return (short) number;
        }

        private static Short year(Object value) {
            if (value == null) return null;
            long number = integer(value);
            if (number < 1950 || number > 2100) throw new IllegalArgumentException("Expected a year between 1950 and 2100");
            return (short) number;
        }

        private static BigDecimal salary(Object value) {
            if (value == null) return null;
            long number = integer(value);
            if (number < 0 || number > SALARY_MAX)
                throw new IllegalArgumentException("Expected a positive integer of at most " + SALARY_MAX);
            return BigDecimal.valueOf(number);
        }

        private static String currency(Object value) {
            if (value == null) throw new IllegalArgumentException("Must not be null");
            String string = text(value);
            if (!CURRENCY.matcher(string).matches()) throw new IllegalArgumentException("Expected a three-letter uppercase currency");
            return string;
        }

        private static <E extends Enum<E>> E enumValue(Object value, Class<E> type) {
            if (value == null) return null;
            String string = text(value);
            try {
                return Enum.valueOf(type, string);
            } catch (IllegalArgumentException ex) {
                Set<String> allowed = new TreeSet<>();
                for (E constant : type.getEnumConstants()) allowed.add(constant.name());
                throw new IllegalArgumentException("Allowed values: " + allowed);
            }
        }

        private static <E extends Enum<E>> E requiredEnum(Object value, Class<E> type) {
            if (value == null) throw new IllegalArgumentException("Required");
            return enumValue(value, type);
        }
    }
}
