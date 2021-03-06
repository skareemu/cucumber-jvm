package io.cucumber.core.options;

import io.cucumber.core.exception.CucumberException;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.core.feature.GluePath;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.cucumber.core.options.Constants.ANSI_COLORS_DISABLED_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.EXECUTION_DRY_RUN_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.EXECUTION_LIMIT_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.EXECUTION_ORDER_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.EXECUTION_STRICT_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FEATURES_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FILTER_NAME_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.OBJECT_FACTORY_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.SNIPPET_TYPE_PROPERTY_NAME;
import static io.cucumber.core.options.Constants.WIP_PROPERTY_NAME;
import static io.cucumber.core.options.OptionsFileParser.parseFeatureWithLinesFile;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public final class CucumberPropertiesParser {

    public RuntimeOptionsBuilder parse(Map<String, String> properties) {
        RuntimeOptionsBuilder builder = new RuntimeOptionsBuilder();

        parse(properties,
            ANSI_COLORS_DISABLED_PROPERTY_NAME,
            Boolean::parseBoolean,
            builder::setMonochrome);

        parse(properties,
            EXECUTION_DRY_RUN_PROPERTY_NAME,
            Boolean::parseBoolean,
            builder::setDryRun);

        parse(properties,
            EXECUTION_LIMIT_PROPERTY_NAME,
            Integer::parseInt,
            builder::setCount);

        parse(properties,
            EXECUTION_ORDER_PROPERTY_NAME,
            PickleOrderParser::parse,
            builder::setPickleOrder);

        parse(properties,
            EXECUTION_STRICT_PROPERTY_NAME,
            Boolean::parseBoolean,
            CucumberPropertiesParser::errorOnNonStrict);

        parseAll(properties,
            FEATURES_PROPERTY_NAME,
            splitAndThenFlatMap(CucumberPropertiesParser::parseFeatureFile),
            builder::addFeature);
        parseAll(properties,
            FEATURES_PROPERTY_NAME,
            splitAndMap(CucumberPropertiesParser::parseRerunFile),
            builder::addRerun);

        parse(properties,
            FILTER_NAME_PROPERTY_NAME,
            Pattern::compile,
            builder::addNameFilter);

        parse(properties,
            FILTER_TAGS_PROPERTY_NAME,
            Function.identity(),
            builder::addTagFilter);

        parseAll(properties,
            GLUE_PROPERTY_NAME,
            splitAndMap(GluePath::parse),
            builder::addGlue);

        parse(properties,
            OBJECT_FACTORY_PROPERTY_NAME,
            ObjectFactoryParser::parseObjectFactory,
            builder::setObjectFactoryClass);

        parseAll(properties,
            PLUGIN_PROPERTY_NAME,
            splitAndMap(Function.identity()),
            builder::addPluginName);

        parse(properties,
            SNIPPET_TYPE_PROPERTY_NAME,
            SnippetTypeParser::parseSnippetType,
            builder::setSnippetType);
        parse(properties,
            WIP_PROPERTY_NAME,
            Boolean::parseBoolean,
            builder::setWip);

        return builder;
    }

    private <T> void parse(
            Map<String, String> properties, String propertyName, Function<String, T> parser, Consumer<T> setter
    ) {
        parseAll(properties, propertyName, parser.andThen(Collections::singletonList), setter);
    }

    private static void errorOnNonStrict(Boolean strict) {
        if (!strict) {
            throw new CucumberException(EXECUTION_STRICT_PROPERTY_NAME
                    + "=false is no longer effective. Please use =true (the default) or remove this property");
        }
    }

    private <T> void parseAll(
            Map<String, String> properties, String propertyName, Function<String, Collection<T>> parser,
            Consumer<T> setter
    ) {
        String property = properties.get(propertyName);
        if (property == null || property.isEmpty()) {
            return;
        }
        try {
            Collection<T> parsed = parser.apply(property);
            parsed.forEach(setter);
        } catch (Exception e) {
            throw new CucumberException("Failed to parse '" + propertyName + "' with value '" + property + "'", e);
        }
    }

    private static <T> Function<String, Collection<T>> splitAndThenFlatMap(Function<String, Stream<T>> parse) {
        return combined -> stream(combined.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .flatMap(parse)
                .collect(toList());
    }

    private static Stream<FeatureWithLines> parseFeatureFile(String property) {
        if (property.startsWith("@")) {
            return Stream.empty();
        }
        return Stream.of(FeatureWithLines.parse(property));
    }

    private static <T> Function<String, Collection<T>> splitAndMap(Function<String, T> parse) {
        return combined -> stream(combined.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(parse)
                .collect(toList());
    }

    private static Collection<FeatureWithLines> parseRerunFile(String property) {
        if (property.startsWith("@")) {
            Path rerunFile = Paths.get(property.substring(1));
            return parseFeatureWithLinesFile(rerunFile);
        }
        return Collections.emptyList();
    }

}
