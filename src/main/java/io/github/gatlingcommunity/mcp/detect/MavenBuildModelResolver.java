package io.github.gatlingcommunity.mcp.detect;

import io.github.gatlingcommunity.mcp.core.model.BuildTool;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

final class MavenBuildModelResolver implements BuildModelResolver {
    private static final Duration EFFECTIVE_POM_TIMEOUT = Duration.ofSeconds(5);

    private final BuildToolCommandRunner commandRunner;
    private final ConventionOnlyBuildModelResolver fallback = new ConventionOnlyBuildModelResolver();

    MavenBuildModelResolver() {
        this(BuildToolCommandRunner.system());
    }

    MavenBuildModelResolver(BuildToolCommandRunner commandRunner) {
        this.commandRunner = commandRunner == null ? BuildToolCommandRunner.system() : commandRunner;
    }

    @Override
    public BuildTool buildTool() {
        return BuildTool.MAVEN;
    }

    @Override
    public boolean supports(Path root) {
        return Files.isRegularFile(root.resolve("pom.xml"));
    }

    @Override
    public BuildModel resolve(Path root) {
        var normalized = root.toAbsolutePath().normalize();
        var rawModel = fallback.resolve(normalized);
        var command = effectivePomCommand(normalized);
        var result = commandRunner.run(normalized, command, EFFECTIVE_POM_TIMEOUT);
        if (result.exitCode() != 0 || result.timedOut() || result.stdout().isBlank()) {
            return withWarning(rawModel, "build_model.maven.effective_pom.unavailable");
        }
        try {
            return fromEffectivePom(normalized, rawModel, result.stdout());
        } catch (RuntimeException exc) {
            return withWarning(rawModel, "build_model.maven.effective_pom.parse_failed");
        }
    }

    private static List<String> effectivePomCommand(Path root) {
        return Files.isRegularFile(root.resolve("mvnw"))
                ? List.of("./mvnw", "-q", "help:effective-pom", "-DskipTests")
                : List.of("mvn", "-q", "help:effective-pom", "-DskipTests");
    }

    private static BuildModel fromEffectivePom(Path root, BuildModel rawModel, String xml) {
        var document = parseXml(xml);
        var properties = new LinkedHashMap<String, String>(rawModel.properties());
        properties.putAll(properties(document));

        var dependencies = ordered(rawModel.dependencies(), artifactIdsUnder(document, "dependency"));
        var plugins = ordered(rawModel.plugins(), artifactIdsUnder(document, "plugin"));
        var sourceRoots = ordered(rawModel.sourceRoots(), sourceRoots(root, document, "sourceDirectory"));
        var testRoots = ordered(rawModel.testRoots(), sourceRoots(root, document, "testSourceDirectory"));

        return new BuildModel(
                BuildTool.MAVEN,
                properties,
                dependencies,
                plugins,
                sourceRoots,
                testRoots,
                withoutWarning(rawModel.warnings(), "build_model.resolver.convention_only")
        );
    }

    private static BuildModel withWarning(BuildModel model, String warning) {
        return new BuildModel(
                model.buildTool(),
                model.properties(),
                model.dependencies(),
                model.plugins(),
                model.sourceRoots(),
                model.testRoots(),
                ordered(model.warnings(), List.of(warning))
        );
    }

    private static Document parseXml(String xml) {
        try {
            var xmlDocument = extractXmlDocument(xml);
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlDocument)));
        } catch (Exception exc) {
            throw new IllegalArgumentException("Failed to parse Maven effective POM", exc);
        }
    }

    private static String extractXmlDocument(String output) {
        var start = output.indexOf("<project");
        var closingTag = "</project>";
        var end = output.lastIndexOf(closingTag);
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Maven output does not contain an effective POM");
        }
        return output.substring(start, end + closingTag.length());
    }

    private static Map<String, String> properties(Document document) {
        var values = new LinkedHashMap<String, String>();
        var nodes = document.getElementsByTagName("properties");
        for (var i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            var children = node.getChildNodes();
            for (var j = 0; j < children.getLength(); j++) {
                var child = children.item(j);
                if (child instanceof Element element) {
                    values.put(element.getTagName(), element.getTextContent().trim());
                }
            }
        }
        return Map.copyOf(values);
    }

    private static List<String> artifactIdsUnder(Document document, String elementName) {
        var values = new LinkedHashSet<String>();
        var nodes = document.getElementsByTagName(elementName);
        for (var i = 0; i < nodes.getLength(); i++) {
            var artifactId = directChildText(nodes.item(i), "artifactId");
            if (!artifactId.isBlank()) {
                values.add(artifactId);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> sourceRoots(Path root, Document document, String elementName) {
        var values = new LinkedHashSet<String>();
        var nodes = document.getElementsByTagName(elementName);
        for (var i = 0; i < nodes.getLength(); i++) {
            var text = nodes.item(i).getTextContent();
            if (text != null && !text.isBlank()) {
                values.add(normalizeRoot(root, text.trim()));
            }
        }
        return List.copyOf(values);
    }

    private static String directChildText(Node node, String childName) {
        var children = node.getChildNodes();
        for (var i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child instanceof Element element && childName.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return "";
    }

    private static String normalizeRoot(Path root, String value) {
        var raw = Path.of(value);
        var path = raw.isAbsolute() ? raw.normalize() : root.resolve(raw).normalize();
        return path.startsWith(root) ? root.relativize(path).toString() : value;
    }

    private static List<String> ordered(List<String> first, List<String> second) {
        var values = new LinkedHashSet<String>();
        values.addAll(first == null ? List.of() : first);
        values.addAll(second == null ? List.of() : second);
        return List.copyOf(values);
    }

    private static List<String> withoutWarning(List<String> warnings, String warning) {
        var values = new ArrayList<String>();
        for (var item : warnings) {
            if (!warning.equals(item)) {
                values.add(item);
            }
        }
        return List.copyOf(values);
    }
}
