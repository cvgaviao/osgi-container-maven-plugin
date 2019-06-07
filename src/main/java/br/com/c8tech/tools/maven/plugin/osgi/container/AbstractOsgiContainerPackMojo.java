/**
 * ============================================================================
 *  Copyright ©  2015-2019,    Cristiano V. Gavião
 *
 *  All rights reserved.
 *  This program and the accompanying materials are made available under
 *  the terms of the Eclipse Public License v1.0 which accompanies this
 *  distribution and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * ============================================================================
 */
package br.com.c8tech.tools.maven.plugin.osgi.container;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;

import br.com.c8tech.tools.maven.osgi.lib.mojo.AbstractCustomPackagingMojo;
import br.com.c8tech.tools.maven.osgi.lib.mojo.CommonMojoConstants;
import br.com.c8tech.tools.maven.osgi.lib.mojo.beans.BundleRef;
import br.com.c8tech.tools.maven.osgi.lib.mojo.beans.MavenArtifactSet;
import br.com.c8tech.tools.maven.osgi.lib.mojo.beans.MavenArtifactSets;
import br.com.c8tech.tools.maven.osgi.lib.mojo.filters.ValidTypeArtifactFilter;
import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;

/**
 * 
 * @author Cristiano Gavião
 *
 */
public abstract class AbstractOsgiContainerPackMojo
        extends AbstractCustomPackagingMojo {

    private static final String[] DEFAULT_SUPPORTED_PACKAGING = {
            "osgi.container" };

    private static final String DEFAULT_WORK_DIR_NAME = "work";

    public static final String DOWNLOAD_STATUS = "downloadStatus";

    public static final String EQUINOX_CONFIGURATION_NAME = "config.ini";

    public static final String EQUINOX_CONFIGURATION_PATH = "distrib/equinox/"
            + EQUINOX_CONFIGURATION_NAME;

    public static final String FELIX_CONFIGURATION_NAME = "config.properties";

    public static final String FELIX_CONFIGURATION_PATH = "distrib/felix/"
            + FELIX_CONFIGURATION_NAME;

    protected static final String PROPERTY_CACHE_DIR = "osgi.container.cacheDirectory";

    protected static final String PROPERTY_WORK_DIR = "osgi.container.workDirectory";

    /**
     * A path pointing to a custom configuration file that will be used by the
     * OSGi container.
     * <p>
     * If no file is informed an internal default one containing the basic
     * bundle of the choose container will be used.
     * 
     * @see #container
     * @see #skipConfigurationGen
     */
    @Parameter(required = false)
    private String baseContainerConfigurationFileUrl;

    /**
     * The default name of the cache directory.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/"
            + CommonMojoConstants.DEFAULT_CACHE_DIR_NAME)
    private File cacheDirectory;

    /**
     * Points to an OSGi Configurator's initialization file.
     * 
     * @link https://osgi.org/specification/osgi.cmpn/7.0.0/service.configurator.html
     */
    @Parameter
    private File configuratorInitialFile;

    /**
     * The OSGi container that will be used to create the distribution archive
     * and the docker image.
     * <p>
     * The plugin uses this information to create a proper directory structure
     * and also to select the artifacts that will be used.
     */
    @Parameter(required = true)
    private Container container;

    /**
     * An optional maven artifact of type <b>'POM'</b> that will be used to
     * obtain all the bundles to be embedded in the distribution archive for the
     * chosen OSGi container.
     * <p>
     * In case it is not informed the plugin will use one of the following
     * artifacts:
     * <li>Equinox: br.com.c8tech.releng:fpom-deps-equinox:pom
     * <li>Felix: br.com.c8tech.releng:fpom-deps-felix:pom
     * 
     * <p>
     * <br>
     * 
     * The specified POM <b>must</b> have at least the container's main bundle
     * and its launcher jar.<br>
     * 
     * The string format is:<br>
     * <b>{@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}"</b>".
     */
    @Parameter(required = false)
    private String containerPomDependenciesGAV;


    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    /**
     * A list of dependencies's artifactId that must be ignored by the plugin.
     * <p>
     * Example:
     *
     * <pre>
     * {@code
     *  <excludedArtifacts>
     *     <excludeArtifact>osgi.core</excludeArtifact>
     *     <excludeArtifact>org.osgi.annotation</excludeArtifact>
     *  </excludedArtifacts>}
     * </pre>
     */
    @Parameter(property = "osgi.container.excludedArtifacts")
    private List<String> excludedArtifacts = new ArrayList<>();

    /**
     * An maven artifact of type <b>'osgi.repository'<b> that can be used as a
     * local R5 repository and will be attached to the generated container
     * archive.
     * <p>
     * 
     * The repository archive will be copied into the 'repository'
     * directory.<br>
     * 
     * The string format is "<b>groupId:artifactId:[version]</b>". If the
     * version is not informed the latest one will be automatically used.
     */
    @Parameter(required = false)
    private String localRepositoryGAV;

    /**
     * Points to a custom LogBack configuration file.
     * 
     * @link https://logback.qos.ch/manual/configuration.html
     */
    @Parameter
    private File logbackConfigurationFile;

    /**
     * An optional set of OSGi bundle artifacts that will be downloaded to the
     * cache directory.
     * <p>
     * Those artifacts will be used to create the container's configuration file
     * and also to compose the distribution archive and docker image.
     * <p>
     * The developer can instead declare the bundles to be used as normal maven
     * dependencies in the POM. But doing so, it won't be possible to define the
     * bundle's start level.
     * <p>
     * Each MavenArtifact represents one bundle that will be embedded into the
     * container's archive. It should be declared as a string composed by 4
     * parts: '<b>groupId:artifactId:[version][@startLevel]</b>'.
     * 
     * <p>
     * The version will be optional only if the bundle was declared as a POM's
     * dependency.
     * <p>
     * If the version was declared in both places, the one from this artifacts
     * will override the one declared as POM's dependency.
     */
    @Parameter(required = false)
    private MavenArtifactSets mavenArtifactSets;

    /**
     * Whether the plugin should consider maven optional dependencies in order
     * to generate the repositories.
     */
    @Parameter(required = true, defaultValue = "false")
    private boolean optionalConsidered;

    @Inject
    private ProjectBuilder projectBuilder;

    /**
     * A list of scopes to be considered by the plugin when collecting maven
     * dependencies to be used in order to generate the OSGi repositories.
     * <p>
     * The default value is <b>compile</b>.
     * <p>
     * When needed, the user can use the empty value this way:
     *
     * <pre>
     * {@code <scopes><scope>empty</scope></scopes>}
     * </pre>
     */
    @Parameter(property = "osgi.container.scopes")
    private Set<String> scopes = new HashSet<>();

    /**
     * Set this to <code>true</code> to skip the plugin execution.
     */
    @Parameter(defaultValue = "false", property = "osgi.container.skip")
    @Incremental(configuration = Configuration.ignore)
    private boolean skip;

    /**
     * Skips the generation of the container's configuration file.
     * <p>
     * By default this plugin will generate the OSGi container's configuration
     * file based on a base configuration file and the declared dependencies.
     * 
     * @see #baseContainerConfigurationFileUrl
     * @see #mavenArtifactSets
     */
    @Parameter(required = true, defaultValue = "false",
            property = "osgi.container.skip.config")
    private boolean skipConfigurationGen;

    /**
     * Sets the TarArchiver behavior on file paths with more than 100 characters
     * length. Valid values are: "warn" (default), "fail", "truncate", "gnu",
     * "posix", "posix_warn" or "omit".
     */
    @Parameter(property = "osgi.container.assembly.tarLongFileMode",
            defaultValue = "warn")
    private String tarLongFileMode;

    /**
     * Whether the plugin should consider maven transitive dependencies in order
     * to generate repositories.
     */
    @Parameter(required = true, defaultValue = "false")
    private boolean transitiveConsidered;

    private ValidTypeArtifactFilter validTypeArtifactFilter;

    /**
     * A path pointing to the plugin's work directory.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/"
            + DEFAULT_WORK_DIR_NAME)
    private File workDirectory;

    protected AbstractOsgiContainerPackMojo(final MavenProject project) {

        super(project, getDefaultSupportedPackagings());
    }

    /**
     * The default constructor.
     *
     * @param project
     *                       the current project.
     * @param packagings
     *                       the packagings supported by the plugin.
     */
    protected AbstractOsgiContainerPackMojo(final MavenProject project,
            final String... packagings) {
        super(project, false, packagings);
    }

    public static String[] getDefaultSupportedPackagings() {
        return DEFAULT_SUPPORTED_PACKAGING;
    }

    public static MessageFormat getMsgChoiceArtifact() {
        return CommonMojoConstants.MSG_CHOICE_ARTIFACT;
    }

    public static Properties loadProperties(URL pPropertiesFileURL,
            String pEncoding) throws MojoFailureException {
        Properties properties = new Properties();
        try (InputStream is = pPropertiesFileURL.openStream()) {
            Reader reader = new InputStreamReader(is, pEncoding);
            properties.load(reader);
        } catch (IOException e) {
            throw new MojoFailureException(
                    "Failure opening provided configuration file at "
                            + pPropertiesFileURL,
                    e);
        }
        return properties;
    }

    public final void addMavenArtifactSet(MavenArtifactSet pMavenArtifactSet) {
        mavenArtifactSets.getMavenArtifactSets().add(pMavenArtifactSet);
    }

    public final void addScope(String scope) {
        this.scopes.add(scope);
    }

    protected final Path calculateContainerConfigurationFileOutputPath() {
        if (Container.EQUINOX == getContainer()) {
            return getContainerWorkDirectory().resolve("config")
                    .resolve(EQUINOX_CONFIGURATION_NAME);
        } else {
            return getContainerWorkDirectory().resolve("config")
                    .resolve(FELIX_CONFIGURATION_NAME);
        }
    }

    @Override
    protected void createDefaultDirectories() throws MojoExecutionException {
        try {
            if (getWorkDirectory() != null) {
                Files.createDirectories(getWorkDirectory());
                Files.createDirectories(getContainerWorkDirectory());
            } else {
                throw new MojoExecutionException(
                        "Work directory was not properly configured.");
            }
            if (getCacheDirectory() != null) {
                Files.createDirectories(getCacheDirectory());
                Files.createDirectories(getContainerCacheDirectory());
            }
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Fail to create the plugin directories.", e);
        }

    }

    /**
     * Used to specify any action that must be executed when the mojo are being
     * skipped by maven reactor.
     *
     * @throws MojoExecutionException
     *                                    When the skipping process had any
     *                                    problem
     */
    protected void doBeforeSkipMojo() throws MojoExecutionException {
    }

    @Override
    protected void executeExtraInitializationSteps()
            throws MojoExecutionException, MojoFailureException {

        if (containerPomDependenciesGAV == null
                || containerPomDependenciesGAV.isEmpty()) {

            URL url = getClass().getClassLoader()
                    .getResource("default-container-pom.properties");
            Properties props = loadProperties(url, getEncoding());
            String version = props.getProperty("c8tech.releng.version");
            if (Container.EQUINOX == getContainer()) {
                containerPomDependenciesGAV = "br.com.c8tech.releng:fpom-deps-equinox:pom:"
                        .concat(version);
            } else {
                containerPomDependenciesGAV = "br.com.c8tech.releng:fpom-deps-felix:"
                        .concat(version);
            }
        }

        if (getLocalRepositoryGAV() != null
                && !getLocalRepositoryGAV().isEmpty()) {
            MavenArtifactSet mas = new MavenArtifactSet();

            mas.addArtifact(new BundleRef(getLocalRepositoryGAV()));

            getMavenArtifactSets().addMavenArtifactSet(mas);

        }

        if (getContainer().equals(Container.EQUINOX)) {

            MavenArtifactSet masbin = new MavenArtifactSet();
            masbin.setCacheDirectory(
                    getContainerCacheDirectory().resolve("bin"));
            BundleRef equinoxBundle = new BundleRef(
                    "org.eclipse.platform:org.eclipse.osgi");
            equinoxBundle.setCopyName("equinox.jar");
            masbin.addArtifact(equinoxBundle);
            getMavenArtifactSets().addMavenArtifactSet(masbin);

            if (!isSkipConfigurationGen()) {

                MavenArtifactSet masbundles = new MavenArtifactSet();
                masbundles.addArtifact(new BundleRef(
                        "org.apache.felix:org.apache.felix.logback@1"));
                masbundles.addArtifact(new BundleRef("org.slf4j:slf4j-api@1"));
                masbundles.addArtifact(
                        new BundleRef("ch.qos.logback:logback-classic@1"));
                masbundles.addArtifact(
                        new BundleRef("ch.qos.logback:logback-core@1"));
                masbundles.addArtifact(
                        new BundleRef("org.osgi:org.osgi.util.pushstream@1"));
                masbundles.addArtifact(
                        new BundleRef("org.osgi:org.osgi.util.promise@1"));
                masbundles.addArtifact(
                        new BundleRef("org.osgi:org.osgi.util.function@1"));
                masbundles.addArtifact(new BundleRef(
                        "org.apache.felix:org.apache.felix.configadmin@2"));
                masbundles.addArtifact(new BundleRef(
                        "org.eclipse.platform:org.eclipse.equinox.coordinator@2"));
                masbundles.addArtifact(new BundleRef(
                        "org.eclipse.platform:org.eclipse.equinox.metatype@2"));
                masbundles.addArtifact(new BundleRef(
                        "org.apache.felix:org.apache.felix.scr@2"));
                masbundles.addArtifact(new BundleRef(
                        "org.eclipse.platform:org.eclipse.equinox.event@3"));
                masbundles.addArtifact(new BundleRef(
                        "org.eclipse.platform:org.eclipse.equinox.preferences@3"));
                masbundles.addArtifact(new BundleRef(
                        "org.apache.felix:org.apache.felix.bundlerepository@4"));
                masbundles.addArtifact(
                        new BundleRef("org.fusesource.jansi:jansi@4"));
                masbundles.addArtifact(new BundleRef("org.jline:jline@4"));
                masbundles.addArtifact(new BundleRef(
                        "org.apache.felix:org.apache.felix.gogo.jline@4"));
                masbundles.addArtifact(new BundleRef(
                        "org.apache.felix:org.apache.felix.converter@4"));
                masbundles.addArtifact(new BundleRef(
                        "org.eclipse.platform:org.eclipse.equinox.console@4"));
                masbundles.addArtifact(new BundleRef(
                        "org.apache.felix:org.apache.felix.gogo.command@4"));
                masbundles.addArtifact(new BundleRef(
                        "org.apache.felix:org.apache.felix.configurator@4"));
                getMavenArtifactSets().addMavenArtifactSet(masbundles);
            }
        } else
            if (getContainer().equals(Container.FELIX)) {
                MavenArtifactSet masbin = new MavenArtifactSet();
                masbin.setCacheDirectory(
                        getContainerCacheDirectory().resolve("bin"));
                BundleRef equinoxBundle = new BundleRef(
                        "org.apache.felix:org.apache.felix.main@1");
                equinoxBundle.setCopyName("felix.jar");
                masbin.addArtifact(equinoxBundle);
                getMavenArtifactSets().addMavenArtifactSet(masbin);

                if (!isSkipConfigurationGen()) {
                    MavenArtifactSet masbundles = new MavenArtifactSet();
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.logback@1"));
                    masbundles.addArtifact(
                            new BundleRef("org.slf4j:slf4j-api@1"));
                    masbundles.addArtifact(
                            new BundleRef("ch.qos.logback:logback-classic@1"));
                    masbundles.addArtifact(
                            new BundleRef("ch.qos.logback:logback-core@1"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.log@1"));
                    masbundles.addArtifact(
                            new BundleRef("org.osgi:org.osgi.service.log@1"));
                    masbundles.addArtifact(new BundleRef(
                            "org.osgi:org.osgi.util.pushstream@1"));
                    masbundles.addArtifact(
                            new BundleRef("org.osgi:org.osgi.util.promise@1"));
                    masbundles.addArtifact(
                            new BundleRef("org.osgi:org.osgi.util.function@1"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.configadmin@2"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.coordinator@2"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.metatype@2"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.scr@2"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.resolver@2"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.eventadmin@3"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.prefs@3"));
                    masbundles.addArtifact(
                            new BundleRef("org.osgi:org.osgi.service.prefs@3"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.configurator@4"));
                    masbundles.addArtifact(
                            new BundleRef("org.fusesource.jansi:jansi@4"));
                    masbundles.addArtifact(new BundleRef("org.jline:jline@4"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.converter@4"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.bundlerepository@4"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.gogo.runtime@4"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.gogo.jline@4"));
                    masbundles.addArtifact(new BundleRef(
                            "org.apache.felix:org.apache.felix.gogo.command@4"));
                    getMavenArtifactSets().addMavenArtifactSet(masbundles);
                }
            }
    }

    protected String getBaseContainerConfigurationFileUrl() {
        return this.baseContainerConfigurationFileUrl;
    }

    public ArtifactFilter getBundleValidArtifactFilter() {

        if (validTypeArtifactFilter == null) {

            validTypeArtifactFilter = new ValidTypeArtifactFilter();
            validTypeArtifactFilter.addItem("bundle");
            validTypeArtifactFilter.addItem("jar");
        }

        return this.validTypeArtifactFilter;
    }

    public final Path getCacheDirectory() {
        return cacheDirectory.toPath();
    }

    public File getConfiguratorInitialFile() {
        return this.configuratorInitialFile;
    }

    protected final Container getContainer() {
        if (container == null) {
            getLog().info(
                    "A container to build against was not selected. Using Felix as default.");
            container = Container.FELIX;
        }
        return container;
    }

    protected final Path getContainerCacheDirectory() {
        if (getContainer().equals(Container.EQUINOX)) {

            return getCacheDirectory().resolve("equinox");
        } else
            if (getContainer().equals(Container.FELIX)) {

                return getCacheDirectory().resolve("felix");

            }
        return getCacheDirectory();
    }

    protected String getContainerPomDependenciesGAV() {

        return this.containerPomDependenciesGAV;
    }

    protected final Path getContainerWorkDirectory() {
        if (getContainer().equals(Container.EQUINOX)) {
            return getWorkDirectory().resolve("equinox");
        } else
            if (getContainer().equals(Container.FELIX)) {
                return getWorkDirectory().resolve("felix");
            }
        return getWorkDirectory();
    }

    public String getEncoding() {
        if (encoding == null) {
            encoding = "UTF-8";
        }
        return this.encoding;
    }

    protected final List<String> getExcludedArtifacts() {
        return excludedArtifacts;
    }

    public String getLocalRepositoryGAV() {
        return localRepositoryGAV;
    }

    public File getLogbackConfigurationFile() {
        return this.logbackConfigurationFile;
    }

    protected final MavenArtifactSets getMavenArtifactSets() {
        if (mavenArtifactSets == null) {
            mavenArtifactSets = new MavenArtifactSets();
        }
        return mavenArtifactSets;
    }

    public ProjectBuilder getProjectBuilder() {
        return projectBuilder;
    }

    protected final Set<String> getScopes() {
        if (scopes.isEmpty()) {
            scopes.add("compile");
        }
        return scopes;
    }

    protected final boolean isOptionalConsidered() {
        return optionalConsidered;
    }

    public boolean isRunOnlyAtExecutionRoot() {
        return false;
    }

    @Override
    protected boolean isSkip() {
        return skip;
    }

    protected boolean isSkipConfigurationGen() {
        return this.skipConfigurationGen;
    }

    /**
     * Returns true if the current project is located at the Execution Root
     * Directory (where mvn was launched)
     *
     * @return if this is the execution root
     */
    boolean isThisTheExecutionRoot() {
        final Log log = getLog();
        log.debug(
                "Root Folder:" + getMavenSession().getExecutionRootDirectory());
        log.debug("Current Folder:" + getBasedir());
        final boolean result = getMavenSession().getExecutionRootDirectory()
                .equalsIgnoreCase(getBasedir().toString());
        if (result) {
            log.debug("This is the execution root.");
        } else {
            log.debug("This is NOT the execution root.");
        }

        return result;
    }

    protected final boolean isTransitiveConsidered() {
        return transitiveConsidered;
    }

    protected void setBaseContainerConfigurationFileUrl(
            String pBaseContainerConfigurationFileUrl) {
        this.baseContainerConfigurationFileUrl = pBaseContainerConfigurationFileUrl;
    }

    public void setConfiguratorInitialFile(File pConfiguratorInitialFile) {
        this.configuratorInitialFile = pConfiguratorInitialFile;
    }

    protected void setContainerPomDependenciesGAV(
            String pContainerPomDependenciesGAV) {
        this.containerPomDependenciesGAV = pContainerPomDependenciesGAV;
    }

    public void setEncoding(String pEncoding) {
        this.encoding = pEncoding;
    }

    public void setLocalRepositoryGAV(String pLocalRepositoryGAV) {
        this.localRepositoryGAV = pLocalRepositoryGAV;
    }

    public void setLogbackConfigurationFile(File pLogbackConfigurationFile) {
        this.logbackConfigurationFile = pLogbackConfigurationFile;
    }

    public final void setScopes(List<String> scopes) {
        for (String scope : scopes) {
            addScope(scope);
        }
    }

    protected void setSkipConfigurationGen(boolean pSkipConfigurationGen) {
        this.skipConfigurationGen = pSkipConfigurationGen;
    }

}
