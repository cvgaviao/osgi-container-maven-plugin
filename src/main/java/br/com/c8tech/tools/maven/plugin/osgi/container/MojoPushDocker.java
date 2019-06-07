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
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;

@Mojo(name = "pushDockerImage", defaultPhase = LifecyclePhase.DEPLOY,
        requiresProject = true, threadSafe = true)
public class MojoPushDocker extends AbstractOsgiDockerMojo {

    /**
     * The repository to put the built image into, for example
     * <tt>spotify/foo</tt>. You should also set the <tt>tag</tt> parameter,
     * otherwise the tag <tt>latest</tt> is used by default.
     */
    @Parameter(property = "dockerfile.repository")
    private String repository;

    /**
     * Disables the push goal; it becomes a no-op.
     */
    @Parameter(property = "dockerfile.push.skip", defaultValue = "true")
    private boolean skipPush;

    /**
     * The tag to apply to the built image.
     */
    @Parameter(property = "dockerfile.tag")
    private String tag;

    @Inject
    protected MojoPushDocker(MavenProject pProject) {
        super(pProject);
    }

    @Override
    protected void execute(DockerClient dockerClient)
            throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();

        if (skipPush) {
            log.info(
                    "Skipping execution because 'dockerfile.push.skip' is set");
            return;
        }

        if (repository == null) {
            repository = readMetadata(Metadata.REPOSITORY);
        }

        // Do this hoop jumping so that the override order is correct
        if (tag == null) {
            tag = readMetadata(Metadata.TAG);
        }
        if (tag == null) {
            tag = "latest";
        }

        if (repository == null) {
            throw new MojoExecutionException(
                    "Can't push image; image repository not known "
                            + "(specify dockerfile.repository parameter, or run the tag goal before)");
        }

        try {
            dockerClient.push(formatImageName(repository, tag),
                    LoggingProgressHandler.forLog(log, isVerbose()));
        } catch (DockerException e) {
            throw new MojoExecutionException("Could not push image", e);
        } catch (InterruptedException e) {
            getLog().warn("Thread Interrupted!", e);
            // Restore interrupted state...
            Thread.currentThread().interrupt();
        }
    }
}
