package io.swagger.codegen.languages;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import io.swagger.codegen.CliOption;
import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.SupportingFile;
import io.swagger.codegen.mustache.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KotlinServerCodegen extends AbstractKotlinCodegen {

    static Logger LOGGER = LoggerFactory.getLogger(KotlinServerCodegen.class);
    private Boolean autoHeadFeatureEnabled = true;
    private Boolean conditionalHeadersFeatureEnabled = false;
    private Boolean hstsFeatureEnabled = true;
    private Boolean corsFeatureEnabled = false;
    private Boolean compressionFeatureEnabled = true;

    // This is here to potentially warn the user when an option is not supoprted by the target framework.
    private final Map<String, List<String>> optionsSupportedPerFramework = new ImmutableMap.Builder<String, List<String>>()
            .put(Constants.KTOR, Arrays.asList(
                    Constants.AUTOMATIC_HEAD_REQUESTS,
                    Constants.CONDITIONAL_HEADERS,
                    Constants.HSTS,
                    Constants.CORS,
                    Constants.COMPRESSION
            ))
            .build();

    /**
     * Constructs an instance of `KotlinServerCodegen`.
     */
    public KotlinServerCodegen() {
        super();

        artifactId = "kotlin-server";
        packageName = "io.swagger.server";
        outputFolder = "generated-code" + File.separator + "kotlin-server";
        modelTemplateFiles.put("model.mustache", ".kt");
        apiTemplateFiles.put("api.mustache", ".kt");
        embeddedTemplateDir = templateDir = "kotlin-server";
        apiPackage = packageName + ".apis";
        modelPackage = packageName + ".models";

        cliOptions.add(new CliOption(Constants.ENGINE, Constants.ENGINE_DESC) {{
            setDefault(Constants.ENGINE_DEFAULT.getName());
            setEnum(new LinkedHashMap<String, String>() {{
                for (DevelopmentEngine engine : Constants.ENGINES) {
                    put(engine.getName(), engine.getDescription());
                }
            }});
        }});

        addSwitch(Constants.AUTOMATIC_HEAD_REQUESTS, Constants.AUTOMATIC_HEAD_REQUESTS_DESC, autoHeadFeatureEnabled);
        addSwitch(Constants.CONDITIONAL_HEADERS, Constants.CONDITIONAL_HEADERS_DESC, conditionalHeadersFeatureEnabled);
        addSwitch(Constants.HSTS, Constants.HSTS_DESC, hstsFeatureEnabled);
        addSwitch(Constants.CORS, Constants.CORS_DESC, corsFeatureEnabled);
        addSwitch(Constants.COMPRESSION, Constants.COMPRESSION_DESC, compressionFeatureEnabled);
    }


    public String getHelp() {
        return "Generates a kotlin server.";
    }

    public String getName() {
        return "kotlin-server";
    }

    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    private boolean handleAdditionalBoolProperty(boolean value, String name) {
        if (additionalProperties.containsKey(name)) {
            return convertPropertyToBooleanAndWriteBack(name);
        } else {
            additionalProperties.put(name, value);
            return value;
        }
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.LIBRARY)) {
            this.setLibrary((String) additionalProperties.get(CodegenConstants.LIBRARY));
        }

        autoHeadFeatureEnabled = handleAdditionalBoolProperty(autoHeadFeatureEnabled, Constants.AUTOMATIC_HEAD_REQUESTS);
        conditionalHeadersFeatureEnabled = handleAdditionalBoolProperty(conditionalHeadersFeatureEnabled, Constants.CONDITIONAL_HEADERS);
        hstsFeatureEnabled = handleAdditionalBoolProperty(hstsFeatureEnabled, Constants.HSTS);
        corsFeatureEnabled = handleAdditionalBoolProperty(corsFeatureEnabled, Constants.CORS);
        compressionFeatureEnabled = handleAdditionalBoolProperty(compressionFeatureEnabled, Constants.COMPRESSION);

        final String engineName = additionalProperties.containsKey(Constants.ENGINE)
                ? additionalProperties.get(Constants.ENGINE).toString()
                : Constants.ENGINE_DEFAULT.getName();

        final DevelopmentEngine engine = Constants.ENGINES_BY_NAME.get(engineName);
        additionalProperties.put("engineName", engine.getName());
        additionalProperties.put("engineMainClass", engine.getMainClass());
        additionalProperties.put("engineArtifact", engine.getArtifact());
        additionalProperties.put("engineClass", engine.getEngineClass());

        Boolean generateApis = additionalProperties.containsKey(CodegenConstants.GENERATE_APIS) && (Boolean) additionalProperties.get(CodegenConstants.GENERATE_APIS);
        String packageFolder = (sourceFolder + File.separator + packageName).replace(".", File.separator);
        String resourcesFolder = "src/main/resources"; // not sure this can be user configurable.

        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("Dockerfile.mustache", "", "Dockerfile"));

        supportingFiles.add(new SupportingFile("build.gradle.mustache", "", "build.gradle"));
        supportingFiles.add(new SupportingFile("settings.gradle.mustache", "", "settings.gradle"));
        supportingFiles.add(new SupportingFile("gradle.properties", "", "gradle.properties"));

        supportingFiles.add(new SupportingFile("AppMain.kt.mustache", packageFolder, "AppMain.kt"));
        supportingFiles.add(new SupportingFile("Configuration.kt.mustache", packageFolder, "Configuration.kt"));

        if (generateApis) {
            supportingFiles.add(new SupportingFile("Paths.kt.mustache", packageFolder, "Paths.kt"));
        }

        supportingFiles.add(new SupportingFile("application.conf.mustache", resourcesFolder, "application.conf"));
        supportingFiles.add(new SupportingFile("logback.xml", resourcesFolder, "logback.xml"));

        final String infrastructureFolder = (sourceFolder + File.separator + packageName + File.separator + "infrastructure").replace(".", File.separator);

        supportingFiles.add(new SupportingFile("ApiKeyAuth.kt.mustache", infrastructureFolder, "ApiKeyAuth.kt"));

        addMustacheLambdas(additionalProperties);
    }

    private void addMustacheLambdas(Map<String, Object> objs) {

        Map<String, Mustache.Lambda> lambdas = new ImmutableMap.Builder<String, Mustache.Lambda>()
                .put("lowercase", new LowercaseLambda().generator(this))
                .put("uppercase", new UppercaseLambda())
                .put("titlecase", new TitlecaseLambda())
                .put("camelcase", new CamelCaseLambda().generator(this))
                .put("indented", new IndentedLambda())
                .put("indented_4", new IndentedLambda(4, " "))
                .put("indented_8", new IndentedLambda(8, " "))
                .put("indented_12", new IndentedLambda(12, " "))
                .put("indented_16", new IndentedLambda(16, " "))
                .build();

        if (objs.containsKey("lambda")) {
            LOGGER.warn("An property named 'lambda' already exists. Mustache lambdas renamed from 'lambda' to '_lambda'. " +
                    "You'll likely need to use a custom template, " +
                    "see https://github.com/swagger-api/swagger-codegen#modifying-the-client-library-format. ");
            objs.put("_lambda", lambdas);
        } else {
            objs.put("lambda", lambdas);
        }
    }

    public static class Constants {
        public final static String KTOR = "ktor";
        public final static String AUTOMATIC_HEAD_REQUESTS = "featureAutoHead";
        public final static String AUTOMATIC_HEAD_REQUESTS_DESC = "Automatically provide responses to HEAD requests for existing routes that have the GET verb defined.";
        public final static String CONDITIONAL_HEADERS = "featureConditionalHeaders";
        public final static String CONDITIONAL_HEADERS_DESC = "Avoid sending content if client already has same content, by checking ETag or LastModified properties.";
        public final static String HSTS = "featureHSTS";
        public final static String HSTS_DESC = "Avoid sending content if client already has same content, by checking ETag or LastModified properties.";
        public final static String CORS = "featureCORS";
        public final static String CORS_DESC = "Ktor by default provides an interceptor for implementing proper support for Cross-Origin Resource Sharing (CORS). See enable-cors.org.";
        public final static String COMPRESSION = "featureCompression";
        public final static String COMPRESSION_DESC = "Adds ability to compress outgoing content using gzip, deflate or custom encoder and thus reduce size of the response.";
        public static final String ENGINE = "engine";
        public static final String ENGINE_DESC = "Engine to use with the backend";
        public static final DevelopmentEngine[] ENGINES = {
                new DevelopmentEngine("cio", "io.ktor.server.cio.DevelopmentEngine", "io.ktor:ktor-server-cio:$ktor_version", "io.ktor.server.cio.CIO"),
                new DevelopmentEngine("netty", "io.ktor.server.netty.DevelopmentEngine", "io.ktor:ktor-server-netty:$ktor_version", "io.ktor.server.netty.Netty"),
                new DevelopmentEngine("jetty", "io.ktor.server.jetty.DevelopmentEngine", "io.ktor:ktor-server-jetty:$ktor_version", "io.ktor.server.netty.Jetty"),
                new DevelopmentEngine("tomcat", "io.ktor.server.tomcat.DevelopmentEngine", "io.ktor:ktor-server-tomcat:$ktor_version", "io.ktor.server.netty.Tomcat")
        };
        public static final Map<String, DevelopmentEngine> ENGINES_BY_NAME = new LinkedHashMap<String, DevelopmentEngine>() {{
            for (DevelopmentEngine engine : ENGINES) {
                put(engine.getName(), engine);
            }
        }};
        public static final DevelopmentEngine ENGINE_DEFAULT = ENGINES_BY_NAME.get("netty");
    }

    private static class DevelopmentEngine {
        private String name;
        private String mainClass;
        private String artifact;
        private String engineClass;

        public DevelopmentEngine(String name, String mainClass, String artifact, String engineClass) {
            this.name = name;
            this.mainClass = mainClass;
            this.artifact = artifact;
            this.engineClass = engineClass;
        }

        public String getName() {
            return name;
        }

        public String getMainClass() {
            return mainClass;
        }

        public String getArtifact() {
            return artifact;
        }

        public String getEngineClass() {
            return engineClass;
        }

        public String getDescription() {
            return getName();
        }
    }
}
