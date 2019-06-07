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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.InvalidAssemblerConfigurationException;
import org.apache.maven.plugins.assembly.archive.ArchiveCreationException;
import org.apache.maven.plugins.assembly.archive.AssemblyArchiver;
import org.apache.maven.plugins.assembly.format.AssemblyFormattingException;
import org.apache.maven.plugins.assembly.io.AssemblyReadException;
import org.apache.maven.plugins.assembly.io.AssemblyReader;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.plugins.assembly.utils.AssemblyFormatUtils;
import org.apache.maven.plugins.assembly.utils.InterpolationConstants;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenReaderFilter;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.codehaus.plexus.interpolation.fixed.FixedStringSearchInterpolator;
import org.codehaus.plexus.interpolation.fixed.PrefixedPropertiesValueSource;
import org.codehaus.plexus.interpolation.fixed.PropertiesBasedValueSource;

/**
 * This mojo is aimed to build a distributable ZIP archive containing an OSGi
 * container plus bundle dependencies and configuration files.
 * <p>
 * The developer must choose which OSGi framework to use in the configurations
 * session.<br>
 * All compile dependencies of the project will be added into the generated
 * archive.
 * 
 * @author <a href="cvgaviao@gmail.com">Cristiano Gavião</a>
 */

@Mojo(name = "assembly", defaultPhase = LifecyclePhase.PACKAGE,
        threadSafe = true, requiresProject = true,
        instantiationStrategy = InstantiationStrategy.PER_LOOKUP)
public class MojoGenerateOsgiAssemblyArchive extends
        AbstractOsgiContainerPackMojo implements AssemblerConfigurationSource {

    /**
     * This is a set of instructions to the archive builder, especially for
     * building .jar files. It enables you to specify a Manifest file for the
     * jar, in addition to other options. See
     * <a href="http://maven.apache.org/shared/maven-archiver/index.html">Maven
     * Archiver Reference</a>.
     */
    @Parameter
    private MavenArchiveConfiguration archive;

    /**
     */
    @Inject
    private AssemblyArchiver assemblyArchiver;

    /**
     */
    @Inject
    private AssemblyReader assemblyReader;

    protected FixedStringSearchInterpolator commandLinePropertiesInterpolator;

    /**
     * <p>
     * Set of delimiters for expressions to filter within the resources. These
     * delimiters are specified in the form 'beginToken*endToken'. If no '*' is
     * given, the delimiter is assumed to be the same for start and end.
     * </p>
     * <p>
     * So, the default filtering delimiters might be specified as:
     * </p>
     * <p/>
     * 
     * <pre>
     * &lt;delimiters&gt;
     *   &lt;delimiter&gt;${*}&lt;/delimiter&gt;
     *   &lt;delimiter&gt;@&lt;/delimiter&gt;
     * &lt;/delimiters&gt;
     * </pre>
     * <p>
     * Since the '@' delimiter is the same on both ends, we don't need to
     * specify '@*@' (though we can).
     * </p>
     *
     * @since 2.4
     */
    // @Parameter
    private List<String> delimiters;

    /**
     * If this flag is set, everything up to the call to
     * Archiver.createArchive() will be executed.
     */
    @Parameter(property = "osgi.container.assembly.dryRun",
            defaultValue = "false")
    private boolean dryRun;

    /**
     * The character encoding scheme to be applied when filtering resources.
     */
    @Parameter(property = "osgi.container.assembly.encoding",
            defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    protected FixedStringSearchInterpolator envInterpolator;
    /**
     * Expressions preceded with this String won't be interpolated. If you use
     * "\" as the escape string then \${foo} will be replaced with ${foo}.
     *
     * @since 2.4
     */
    // @Parameter(property = "osgi.container.assembly.escapeString")
    private String escapeString;

    /**
     * The list of extra filter properties files to be used along with System
     * properties, project properties, and filter properties files specified in
     * the POM build/filters section, which should be used for the filtering
     * during the current mojo execution. <br/>
     * Normally, these will be configured from a plugin's execution section, to
     * provide a different set of filters for a particular execution.
     */
    // @Parameter
    private List<String> filters;

    /**
     * Specifies the format of the assembly.
     */
    private String format = "tar.gz";
    /**
     * If this flag is set, the ".dir" suffix will be suppressed in the output
     * directory name when using assembly/format == 'dir' and other formats that
     * begin with 'dir'. <br/>
     * <b>NOTE:</b> Since 2.2-beta-3, the default-value for this is true, NOT
     * false as it used to be.
     */
    // @Parameter(defaultValue = "true")
    private boolean ignoreDirFormatExtensions = true;

    /**
     * Set to true in order to not fail when a descriptor is missing.
     */
    // @Parameter(property = "osgi.container.assembly.ignoreMissingDescriptor",
    // defaultValue = "false")
    private boolean ignoreMissingDescriptor = false;

    /**
     * <p>
     * Set to <code>true</code> in order to avoid all chmod calls.
     * </p>
     * <p/>
     * <p>
     * <b>NOTE:</b> This will cause the assembly plugin to <b>DISREGARD</b> all
     * fileMode/directoryMode settings in the assembly descriptor, and all file
     * permissions in unpacked dependencies!
     * </p>
     *
     * @since 2.2
     */
    // @Parameter(property = "osgi.container.assembly.ignorePermissions",
    // defaultValue = "false")
    private boolean ignorePermissions = false;
    /**
     * If True (default) then the ${project.build.filters} are also used in
     * addition to any further filters defined for the Assembly.
     *
     * @since 2.4.2
     */
    // @Parameter(property =
    // "osgi.container.assembly.includeProjectBuildFilters",
    // defaultValue = "true")
    private boolean includeProjectBuildFilters = true;

    protected FixedStringSearchInterpolator mainProjectInterpolator;
    /**
     * Maven shared filtering utility.
     */
    @Inject
    private MavenReaderFilter mavenReaderFilter;

    /**
     * sets the merge manifest mode in the JarArchiver
     * 
     * @since 3
     */
    // @Parameter
    private String mergeManifestMode;

    /**
     * The output directory of the assembled distribution file.
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File outputDirectory;

    /**
     * Contains the full list of projects in the reactor.
     */
    @Parameter(defaultValue = "${reactorProjects}", required = true,
            readonly = true)
    private List<MavenProject> reactorProjects;

    /**
     * Indicates if zip archives (jar,zip etc) being added to the assembly
     * should be compressed again. Compressing again can result in smaller
     * archive size, but gives noticeably longer execution time.
     *
     * @since 2.4
     */
    @Parameter(defaultValue = "true")
    private boolean recompressZippedFiles;

    protected FixedStringSearchInterpolator rootInterpolator;
    /**
     * Directory for site generated.
     */
    @Parameter(defaultValue = "${project.reporting.outputDirectory}",
            readonly = true)
    private File siteDirectory;

    /**
     * Sets the TarArchiver behavior on file paths with more than 100 characters
     * length. Valid values are: "warn" (default), "fail", "truncate", "gnu",
     * "posix", "posix_warn" or "omit".
     */
    // @Parameter(property = "osgi.container.assembly.tarLongFileMode",
    // defaultValue = "warn")
    private String tarLongFileMode = "posix";

    /**
     * Temporary directory that contain the files to be assembled.
     */
    @Parameter(defaultValue = "${project.build.directory}/archive-tmp",
            required = true, readonly = true)
    private File tempRoot;

    /**
     * This will cause the assembly to only update an existing archive, if it
     * exists.
     * <p>
     * <strong>Note:</strong> The property that can be used on the command line
     * was misspelled as "assembly.updatOnly" in versions prior to version 2.4.
     * </p>
     *
     * @since 2.2
     */
    // @Parameter(property = "osgi.container.assembly.updateOnly",
    // defaultValue = "false")
    private boolean updateOnly = false;

    /**
     * <p>
     * will use the jvm chmod, this is available for user and all level group
     * level will be ignored As of assembly-plugin 2.5, this flag is ignored for
     * users of java7+
     * </p>
     *
     * @since 2.2
     */
    // @Parameter(property = "osgi.container.assembly.useJvmChmod",
    // defaultValue = "false")
    private boolean useJvmChmod;

    @Inject
    public MojoGenerateOsgiAssemblyArchive(final MavenProject project) {
        super(project);
    }

    public static FixedStringSearchInterpolator mainProjectInterpolator(
            MavenProject mainProject) {
        if (mainProject != null) {
            // 5
            return FixedStringSearchInterpolator.create(
                    new org.codehaus.plexus.interpolation.fixed.PrefixedObjectValueSource(
                            InterpolationConstants.PROJECT_PREFIXES,
                            mainProject, true),

                    // 6
                    new org.codehaus.plexus.interpolation.fixed.PrefixedPropertiesValueSource(
                            InterpolationConstants.PROJECT_PROPERTIES_PREFIXES,
                            mainProject.getProperties(), true));
        } else {
            return FixedStringSearchInterpolator.empty();
        }
    }

    private FixedStringSearchInterpolator createCommandLinePropertiesInterpolator() {
        Properties commandLineProperties = System.getProperties();
        final MavenSession session = getMavenSession();

        if (session != null) {
            commandLineProperties = new Properties();
            commandLineProperties.putAll(session.getSystemProperties());
            commandLineProperties.putAll(session.getUserProperties());
        }

        PropertiesBasedValueSource cliProps = new PropertiesBasedValueSource(
                commandLineProperties);
        return FixedStringSearchInterpolator.create(cliProps);

    }

    private FixedStringSearchInterpolator createEnvInterpolator() {
        PrefixedPropertiesValueSource envProps = new PrefixedPropertiesValueSource(
                Collections.singletonList("env."),
                CommandLineUtils.getSystemEnvVars(false), true);
        return FixedStringSearchInterpolator.create(envProps);
    }

    private FixedStringSearchInterpolator createRepositoryInterpolator() {
        final Properties settingsProperties = new Properties();
        final MavenSession session = getMavenSession();

        if (getLocalRepository() != null) {
            settingsProperties.setProperty("localRepository",
                    getLocalRepository().getBasedir());
            settingsProperties.setProperty("settings.localRepository",
                    getLocalRepository().getBasedir());
        } else
            if (session != null && session.getSettings() != null) {
                settingsProperties.setProperty("localRepository",
                        session.getSettings().getLocalRepository());
                settingsProperties.setProperty("settings.localRepository",
                        getLocalRepository().getBasedir());
            }

        return FixedStringSearchInterpolator
                .create(new PropertiesBasedValueSource(settingsProperties));

    }

    @Override
    protected void executeExtraInitializationSteps()
            throws MojoExecutionException {

        getProject().getProperties().put(PROPERTY_CACHE_DIR,
                getContainerCacheDirectory().toString());
        getProject().getProperties().put(PROPERTY_WORK_DIR,
                getContainerWorkDirectory().toString());

        Path configPath = getContainerWorkDirectory().resolve("config");
        String logbackConfigName = "logback.xml";
        try {

            if (getConfiguratorInitialFile() != null) {
                Files.copy(getConfiguratorInitialFile().toPath(),
                        configPath.resolve(
                                getConfiguratorInitialFile().getName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            if (getLogbackConfigurationFile() != null) {
                Files.copy(getLogbackConfigurationFile().toPath(),
                        configPath.resolve(logbackConfigName),
                        StandardCopyOption.REPLACE_EXISTING);

            } else {

                copyToWorkdir("/distrib", logbackConfigName, "config");
            }

            copyToWorkdir("/distrib", "gitkeep.txt", "");

        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failure while copying file from plugin.", e);
        }

    }

    @Override
    protected boolean doInitialValidation() throws MojoExecutionException {

        // run only at the execution root.
        if (isRunOnlyAtExecutionRoot() && !isThisTheExecutionRoot()) {
            getLog().info(
                    "Skipping the assembly in this project because it's not the Execution Root");
            return false;
        }

        return super.doInitialValidation();
    }

    private void copyToWorkdir(String pSourceDir, String pFileName,
            String pTargetDir) throws IOException {

        copyInternalFileToProjectDir(pSourceDir, pFileName,
                getContainerWorkDirectory().resolve(pTargetDir).toString());
    }

    @Override
    protected void executeMojo()
            throws MojoExecutionException, MojoFailureException {

        List<Assembly> assemblies;
        try {
            assemblies = getAssemblyReader().readAssemblies(this);
        } catch (final AssemblyReadException e) {
            throw new MojoExecutionException(
                    "Error reading assemblies: " + e.getMessage(), e);
        } catch (final InvalidAssemblerConfigurationException e) {
            throw new MojoFailureException(getAssemblyReader(), e.getMessage(),
                    "Mojo configuration is invalid: " + e.getMessage());
        }

        for (final Assembly assembly : assemblies) {
            try {
                final String fullName = AssemblyFormatUtils
                        .getDistributionName(assembly, this);

                final File destFile = getAssemblyArchiver().createArchive(
                        assembly, fullName, format, this,
                        isRecompressZippedFiles(), getMergeManifestMode());

                final MavenProject project = getProject();
                final String type = project.getArtifact().getType();

                if (destFile.isFile() && "osgi.container".equals(type)) {

                    project.getArtifact().setFile(destFile);
                }
            } catch (final ArchiveCreationException
                    | AssemblyFormattingException e) {
                throw new MojoExecutionException(
                        "Failed to create assembly: " + e.getMessage(), e);
            } catch (final InvalidAssemblerConfigurationException e) {
                throw new MojoFailureException(assembly,
                        "Assembly is incorrectly configured: "
                                + assembly.getId(),
                        "Assembly: " + assembly.getId()
                                + " is not configured correctly: "
                                + e.getMessage());
            }
        }

    }

    @Override
    public File getArchiveBaseDirectory() {
        return null;
    }

    @Override
    public String getArchiverConfig() {
        return null;
    }

    public AssemblyArchiver getAssemblyArchiver() {
        return this.assemblyArchiver;
    }

    public AssemblyReader getAssemblyReader() {
        return this.assemblyReader;
    }

    @Override
    @Nonnull
    public FixedStringSearchInterpolator getCommandLinePropsInterpolator() {
        if (commandLinePropertiesInterpolator == null) {
            this.commandLinePropertiesInterpolator = createCommandLinePropertiesInterpolator();
        }
        return commandLinePropertiesInterpolator;
    }

    @Override
    public List<String> getDelimiters() {
        return delimiters;
    }

    @Override
    public String[] getDescriptorReferences() {
        if (getContainer() == Container.EQUINOX) {
            return new String[] { "assembly-equinox" };
        }
        if (getContainer() == Container.FELIX) {
            return new String[] { "assembly-felix" };
        }
        return new String[] {};
    }

    public String[] getDescriptors() {
        return new String[] {};
    }

    @Override
    public File getDescriptorSourceDirectory() {
        return null;
    }

    @Override
    public String getEncoding() {
        return encoding;
    }

    @Override
    @Nonnull
    public FixedStringSearchInterpolator getEnvInterpolator() {
        if (envInterpolator == null) {
            this.envInterpolator = createEnvInterpolator();
        }
        return envInterpolator;
    }

    @Override
    public String getEscapeString() {
        return escapeString;
    }

    @Override
    public List<String> getFilters() {
        if (filters == null) {
            filters = getProject().getBuild().getFilters();
            if (filters == null) {
                filters = Collections.emptyList();
            }
        }
        return filters;
    }

    @Override
    public MavenArchiveConfiguration getJarArchiveConfiguration() {
        return archive;
    }

    @Override
    @Nonnull
    public FixedStringSearchInterpolator getMainProjectInterpolator() {
        if (mainProjectInterpolator == null) {
            this.mainProjectInterpolator = mainProjectInterpolator(
                    getProject());
        }
        return mainProjectInterpolator;
    }

    public MavenReaderFilter getMavenReaderFilter() {
        return mavenReaderFilter;
    }

    public String getMergeManifestMode() {
        return mergeManifestMode;
    }

    @Override
    public File getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    public List<MavenProject> getReactorProjects() {
        return reactorProjects;
    }

    @Override
    @Nonnull
    public FixedStringSearchInterpolator getRepositoryInterpolator() {
        if (rootInterpolator == null) {
            this.rootInterpolator = createRepositoryInterpolator();
        }
        return rootInterpolator;
    }

    @Override
    public File getSiteDirectory() {
        return siteDirectory;
    }

    @Override
    public String getTarLongFileMode() {
        return tarLongFileMode;
    }

    @Override
    public File getTemporaryRootDirectory() {
        return tempRoot;
    }

    @Override
    public File getWorkingDirectory() {
        return getContainerWorkDirectory().toFile();
    }

    @Override
    public boolean isAssemblyIdAppended() {
        return false;
    }

    @Override
    public boolean isDryRun() {
        return dryRun;
    }

    @Override
    public boolean isIgnoreDirFormatExtensions() {
        return ignoreDirFormatExtensions;
    }

    @Override
    public boolean isIgnoreMissingDescriptor() {
        return ignoreMissingDescriptor;
    }

    @Override
    public boolean isIgnorePermissions() {
        return ignorePermissions;
    }

    @Override
    public boolean isIncludeProjectBuildFilters() {
        return includeProjectBuildFilters;
    }

    boolean isRecompressZippedFiles() {
        return recompressZippedFiles;
    }

    @Override
    public boolean isUpdateOnly() {
        return updateOnly;
    }

    @Override
    public boolean isUseJvmChmod() {
        return useJvmChmod;
    }

    public void setArchive(final MavenArchiveConfiguration archive) {
        this.archive = archive;
    }

    public void setDelimiters(List<String> delimiters) {
        this.delimiters = delimiters;
    }

    public void setReactorProjects(final List<MavenProject> reactorProjects) {
        this.reactorProjects = reactorProjects;
    }

    public void setTempRoot(final File tempRoot) {
        this.tempRoot = tempRoot;
    }

}
