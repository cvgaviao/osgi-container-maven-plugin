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

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.eclipse.aether.artifact.DefaultArtifact;

import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManager;
import br.com.c8tech.tools.maven.osgi.lib.mojo.incremental.ArtifactTrackerManagerBuilder;
import io.takari.incrementalbuild.BuildContext;

/**
 * This mojo will download or copy into the cache folder all artifacts declared
 * in the configuration tag <b>artifactConfigSets</b>.
 * <p>
 * As the org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher
 * application was designed to work with eclipse generated bundles which uses
 * its symbolic-name and version is separated by "_" instead the one used by
 * maven "-", it will be necessary to rename the downloaded artifact file name.
 * <p>
 * It was not designed be used stand alone, but as an integrated part of the
 * default lifecycle of the <b>osgi.repository</b> packaging type.
 *
 * @author Cristiano Gavião
 *
 */
@Mojo(name = "cacheMavenArtifacts",
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
        requiresProject = true, aggregator = false)
public class MojoCacheMavenArtifacts extends AbstractOsgiContainerPackMojo {

    private final BuildContext copyBuildContext;

    @Inject
    public MojoCacheMavenArtifacts(MavenProject project,
            BuildContext pCopyBuildContext) {
        super(project);
        copyBuildContext = pCopyBuildContext;
    }

    @Override
    protected void doBeforeSkipMojo() throws MojoExecutionException {
        copyBuildContext.markSkipExecution();
    }

    @Override
    protected void executeMojo()
            throws MojoExecutionException, MojoFailureException {

        getLog().info("Setting up caching of maven artifacts for project "
                + getProject().getArtifactId());
        ArtifactTrackerManager artifactTrackerManager = ArtifactTrackerManagerBuilder
                .newBuilder(getMavenSession(), getContainerCacheDirectory())
                .withGroupingByTypeDirectory(true).withVerbose(isVerbose())
                .withPreviousCachingRequired(false).mavenSetup()
                .withDependenciesHelper(getDependenciesHelper())
                .withRepositorySystem(getRepositorySystem())
                .withCachedFileNamePattern(getCachedFileNamePattern())
                .workspaceSetup().withAssemblyUrlProtocolAllowed(false)
                .withPackOnTheFlyAllowed(false).endWorkspaceSetup()
                .mavenFiltering()
                .withOptional(isOptionalConsidered())
                .withTransitive(isTransitiveConsidered())
                .withMavenArtifactSets(getMavenArtifactSets())
                .withScopes(getScopes())
                .withExcludedDependencies(getExcludedArtifacts())
                .endMavenFiltering().endMavenSetup().build();


        MavenProject pom;
        try {
            // verify which container was selected and fulfill the MavenArtifactSets
            // with the default values for it.
            pom = loadProject(
                    new DefaultArtifact(getContainerPomDependenciesGAV()),
                    true);
        } catch (ProjectBuildingException e) {
            throw new MojoFailureException(
                    "Failure while obtaining container's POM", e);
        }

        if (artifactTrackerManager.resolveMavenArtifacts(pom.getArtifacts(),
                getScopes()) > 0) {
            artifactTrackerManager.copyMavenArtifactsToCache(copyBuildContext);
        } else {
            getLog().info(
                    "No artifact needs to be cached from a maven repository for project "
                            + getProject().getArtifactId());
        }
    }

}
