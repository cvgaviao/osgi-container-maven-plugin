package br.com.c8tech.tools.maven.plugin.osgi.container;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.artifact.DefaultArtifact;

import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTracker;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManager;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManagerBuilder;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputSet;

@Mojo(name = "generateConfigurationFile",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
        requiresProject = true, aggregator = false)
public class MojoGenerateContainerConfigFile
        extends AbstractOsgiContainerPackMojo {

    private static final String CLASSPATH_PREFIX = "classpath:";

    private static final String EQUINOX_DEFAULT_BUNDLES_DIR = "bundles/";

    private static final String EQUINOX_LINE_BREAK = ",";

    private static final String FELIX_LINE_BREAK = " ";

    private static final String SLASH_PREFIX = "/";

    protected final AggregatorBuildContext configurationFileAggregatorBuildContext;

    /**
     * Whether the plugin should consider maven optional dependencies in order
     * to generate the repositories.
     */
    @Parameter(defaultValue = "5")
    private Integer startLevelDefault;

    @Inject
    protected MojoGenerateContainerConfigFile(MavenProject pProject,
            AggregatorBuildContext pconfigurationFileAggregatorBuildContext) {
        super(pProject);
        configurationFileAggregatorBuildContext = pconfigurationFileAggregatorBuildContext;
    }

    private void copyCustomConfigurationFile(String pSourceURL, Path outputFile)
            throws MojoFailureException, MojoExecutionException {
        Properties properties = new Properties();
        try (InputStream is = loadUrl(pSourceURL).openStream()) {
            Reader reader = new InputStreamReader(is, getEncoding());
            properties.load(reader);
            Writer writer = new OutputStreamWriter(
                    new FileOutputStream(outputFile.toFile()), getEncoding());
            properties.store(writer, "copyied existent configuration file");
        } catch (IOException e) {
            throw new MojoFailureException(
                    "Failure while copying configuration file at " + pSourceURL,
                    e);
        }
    }

    private void copyCustomConfigurationFile(URL pSourceURL, Path outputFile)
            throws MojoFailureException, MojoExecutionException {
        if (pSourceURL != null) {
            copyCustomConfigurationFile(pSourceURL.toString(), outputFile);
        } else {

            throw new MojoFailureException(
                    "Failure opening provided configuration file at "
                            + getBaseContainerConfigurationFileUrl());
        }
    }

    @Override
    public void executeMojo()
            throws MojoExecutionException, MojoFailureException {

        getLog().info("Setting up generation of a configuration file for "
                + getContainer() + " container.");

        Path outputFile = calculateContainerConfigurationFileOutputPath();
        try {
            Files.createDirectories(outputFile.getParent());
        } catch (IOException e1) {
            throw new MojoFailureException(
                    "Failure while creating config directory", e1);

        }

        // when user skips config generation and do not provides a base
        // configuration
        // file then used the internal one.
        Properties properties = null;
        if (isSkipConfigurationGen()) {
            if (getBaseContainerConfigurationFileUrl() == null) {

                copyCustomConfigurationFile(getInternalConfigurationFileURL(),
                        outputFile);
                return;
            } else {
                // we just need to copy the provided file to the right place
                copyCustomConfigurationFile(
                        getBaseContainerConfigurationFileUrl(), outputFile);
                return;
            }
        } else {
            if (getBaseContainerConfigurationFileUrl() != null) {
                // try to load the file that will be used as base for the final
                // file.
                URL externalFile;
                externalFile = loadUrl(getBaseContainerConfigurationFileUrl());
                properties = loadProperties(externalFile, getEncoding());
            } else {
                URL internalFile = getInternalConfigurationFileURL();
                // load the default internal file
                if (internalFile != null) {
                    properties = loadProperties(internalFile, getEncoding());
                } else {
                    throw new MojoFailureException(
                            "Failure opening internal configuration file at "
                                    + getBaseContainerConfigurationFileUrl());
                }
            }
        }

        ArtifactTrackerManager artifactTrackerManager = ArtifactTrackerManagerBuilder
                .newBuilder(getMavenSession(), getCacheDirectory())
                .withGroupingByTypeDirectory(true)
                .withPreviousCachingRequired(true).mavenSetup()
                .withDependenciesHelper(getDependenciesHelper())
                .withRepositorySystem(getRepositorySystem()).workspaceSetup()
                .withAssemblyUrlProtocolAllowed(true)
                .withPackOnTheFlyAllowed(false).endWorkspaceSetup()
                .mavenFiltering().withOptional(isOptionalConsidered())
                .withArtifactFilter(getBundleValidArtifactFilter())
                .withTransitive(isTransitiveConsidered())
                .withScopes(getScopes())
                .withMavenArtifactSets(getMavenArtifactSets())
                .withExcludedDependencies(getExcludedArtifacts())
                .endMavenFiltering().endMavenSetup().build();

        MavenProject pom;
        try {
            // verify which container was selected and fulfill the
            // MavenArtifactSets
            // with the default values for it.
            pom = loadProject(
                    new DefaultArtifact(getContainerPomDependenciesGAV()),
                    true);
        } catch (ProjectBuildingException e) {
            throw new MojoFailureException(
                    "Failure while obtaining container's POM", e);
        }

        int count = artifactTrackerManager
                .resolveMavenArtifacts(pom.getArtifacts(), getScopes());

        if (isVerbose()) {
            getLog().info("Registering the " + count
                    + " artifacts into the OSGi container configuration generation incremental build context.");
        }
        prepareForConfigurationFileGeneration(outputFile,
                artifactTrackerManager, properties);
    }

    private void generateEquinoxConfigurationFileOutput(
            Output<File> pOutputFile, Iterable<File> pInputFiles,
            ArtifactTrackerManager pArtifactTrackerManager,
            Properties pProperties) throws IOException {

        String bundlesFomProperties = pProperties.getProperty("osgi.bundles");
        StringBuilder bundles = new StringBuilder();
        bundles.append(bundlesFomProperties);
        if (!bundlesFomProperties.isEmpty()) {
            bundles.append(EQUINOX_LINE_BREAK);
        }
        for (File processingArtifactFile : pInputFiles) {
            ArtifactTracker artifactProperty = pArtifactTrackerManager
                    .searchByPath(processingArtifactFile.getPath());
            if (artifactProperty == null
                    || artifactProperty.getSymbolicName() == null
                    || artifactProperty.getSymbolicName().isEmpty()) {
                getLog().warn("Ignoring file '" + processingArtifactFile
                        + "' due a missing metadata.");
                continue;
            }
            if (artifactProperty.getCacheDir().endsWith("bin")) {
                continue;
            }

            if (bundles.length() > 0)
                bundles.append(EQUINOX_LINE_BREAK);

            String name = artifactProperty.getCachedFilePath().toFile()
                    .getName();

            bundles.append(EQUINOX_DEFAULT_BUNDLES_DIR).append(name);
            if (artifactProperty.getStartLevel() != 0) {
                bundles.append("@start:")
                        .append(artifactProperty.getStartLevel());
            }
        }
        pProperties.put("osgi.bundles", bundles.toString());
        Writer writer = new OutputStreamWriter(pOutputFile.newOutputStream(),
                getEncoding());
        pProperties.store(writer, "generated configuration file");
        writer.close();
    }

    private void generateFelixConfigurationFileOutput(Output<File> pOutputFile,
            Iterable<File> pInputFiles,
            ArtifactTrackerManager pArtifactTrackerManager,
            Properties pProperties) throws IOException {

        String defaultStartLevel = pProperties
                .getProperty("felix.startlevel.bundle");
        if (defaultStartLevel == null) {
            pProperties.put("felix.startlevel.bundle",
                    getStartLevelDefault().toString());
        }
        pProperties.put("org.osgi.framework.startlevel.beginning",
                getStartLevelDefault().toString());
        pProperties.remove("felix.auto.deploy.dir");
        pProperties.remove("felix.auto.deploy.action");

        for (File processingArtifactFile : pInputFiles) {
            ArtifactTracker artifactProperty = pArtifactTrackerManager
                    .searchByPath(processingArtifactFile.getPath());
            if (artifactProperty == null
                    || artifactProperty.getSymbolicName() == null
                    || artifactProperty.getSymbolicName().isEmpty()) {
                getLog().warn("Ignoring file '" + processingArtifactFile
                        + "' due a missing metadata.");
                continue;
            }

            if (!artifactProperty.getCacheDir().endsWith("bin")) {

                if (artifactProperty.getStartLevel() == 0) {
                    processFelixInstalableBundles(pProperties,
                            artifactProperty);
                } else {
                    processFelixStartableBundles(pProperties, artifactProperty);
                }
            }
        }

        Writer writer = new OutputStreamWriter(pOutputFile.newOutputStream(),
                getEncoding());
        pProperties.store(writer, "generated configuration file");
        writer.close();

    }

    private URL getInternalConfigurationFileURL() {
        URL internalFile = null;
        if (getContainer().equals(Container.EQUINOX)) {

            internalFile = getClass().getClassLoader()
                    .getResource(EQUINOX_CONFIGURATION_PATH);
        } else
            if (getContainer().equals(Container.FELIX)) {
                internalFile = getClass().getClassLoader()
                        .getResource(FELIX_CONFIGURATION_PATH);
            }
        return internalFile;
    }

    public Integer getStartLevelDefault() {
        return this.startLevelDefault;
    }

    private final URL loadUrl(String pUrlString) throws MojoExecutionException {
        URL url = null;
        if (pUrlString.startsWith(CLASSPATH_PREFIX)) {
            String resource = pUrlString.substring(CLASSPATH_PREFIX.length(),
                    pUrlString.length());
            if (resource.startsWith(SLASH_PREFIX)) {
                resource = resource.substring(1, resource.length());
            }
            url = getClass().getClassLoader().getResource(resource);
            if (url == null) {
                throw new MojoExecutionException(
                        "The resource in the URL " + url + " doesn't exist.");
            }
        } else {
            try {
                url = new URL(pUrlString);
            } catch (MalformedURLException e) {
                throw new MojoExecutionException(
                        "Badly formed URL " + url + " - " + e.getMessage());
            }
        }
        return url;
    }

    private void prepareForConfigurationFileGeneration(final Path outputFile,
            final ArtifactTrackerManager pArtifactTrackerManager,
            Properties pProperties) throws MojoExecutionException {

        InputSet configFileInputSet = registerArtifactsIntoAggregatorBuildContext(
                pArtifactTrackerManager.getAllArtifactTrackers(),
                configurationFileAggregatorBuildContext, true);

        try {
            // build the contents only when necessary
            if (configFileInputSet.aggregateIfNecessary(outputFile.toFile(),
                    (output, inputs) -> {
                        getLog().info(
                                "Starting generation of the OSGi container configuration file for project "
                                        + getProject().getArtifactId());
                        if (getContainer().equals(Container.EQUINOX)) {

                            generateEquinoxConfigurationFileOutput(output,
                                    inputs, pArtifactTrackerManager,
                                    pProperties);
                        } else
                            if (getContainer().equals(Container.FELIX)) {
                                generateFelixConfigurationFileOutput(output,
                                        inputs, pArtifactTrackerManager,
                                        pProperties);
                            }
                    })) {
                getLog().info(String.format(
                        "The OSGi container configuration file was successfully generated at : %s",
                        outputFile.toAbsolutePath()));
            }
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "An error occurred while generating the OSGi container configuration file.",
                    e);
        }
    }

    private void processFelixInstalableBundles(Properties pProperties,
            ArtifactTracker pArtifactProperty) {
        String id = getStartLevelDefault().toString();
        String key = "felix.auto.install.".concat(id);
        String bundlesToInstall = pProperties.getProperty(key) != null
                ? pProperties.getProperty(key)
                : "";
        if (!bundlesToInstall.isEmpty()) {
            bundlesToInstall = bundlesToInstall.concat(FELIX_LINE_BREAK);
        }
        bundlesToInstall = bundlesToInstall.concat("file:./bundles/"
                + pArtifactProperty.getCachedFilePath().getFileName());

        pProperties.put(key, bundlesToInstall);
    }

    private void processFelixStartableBundles(Properties pProperties,
            ArtifactTracker pArtifactProperty) {
        String id = Integer.toString(pArtifactProperty.getStartLevel());
        String key = "felix.auto.start.".concat(id);
        String bundlesToStart = pProperties.getProperty(key) != null
                ? pProperties.getProperty(key)
                : "";
        if (!bundlesToStart.isEmpty()) {
            bundlesToStart = bundlesToStart.concat(FELIX_LINE_BREAK);
        }
        bundlesToStart = bundlesToStart.concat("file:./bundles/"
                + pArtifactProperty.getCachedFilePath().getFileName());
        pProperties.put(key, bundlesToStart);
    }

    public void setStartLevelDefault(Integer pStartLevelDefault) {
        this.startLevelDefault = pStartLevelDefault;
    }

}
