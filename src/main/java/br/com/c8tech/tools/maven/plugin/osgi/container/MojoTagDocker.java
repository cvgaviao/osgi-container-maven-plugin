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

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;

import java.text.MessageFormat;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "tagDockerImage", defaultPhase = LifecyclePhase.PACKAGE,
        requiresProject = true, threadSafe = true)
public class MojoTagDocker extends AbstractOsgiDockerMojo {

    /**
     * Whether to force re-assignment of an already assigned tag.
     */
    @Parameter(property = "dockerfile.force", defaultValue = "true",
            required = true)
    private boolean force;

    /**
     * The repository to put the built image into, for example
     * <tt>spotify/foo</tt>. You should also set the <tt>tag</tt> parameter,
     * otherwise the tag <tt>latest</tt> is used by default.
     */
    @Parameter(property = "dockerfile.repository",
            defaultValue = "${project.groupId}/${project.artifactId}",
            required = true)
    private String repository;

    /**
     * Disables the tag goal; it becomes a no-op.
     */
    @Parameter(property = "dockerfile.tag.skip", defaultValue = "true")
    private boolean skipTag;

    /**
     * The tag to apply to the built image.
     */
    @Parameter(property = "dockerfile.tag", defaultValue = "latest",
            required = true)
    private String tag;

    @Inject
    protected MojoTagDocker(MavenProject pProject) {
        super(pProject);
    }

    @Override
    protected void execute(DockerClient dockerClient)
            throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();

        if (skipTag) {
            log.info("Skipping execution because 'dockerfile.tag.skip' is set");
            return;
        }

        final String imageId = readMetadata(Metadata.IMAGE_ID);
        final String imageName = formatImageName(repository, tag);

        final String message = MessageFormat.format("Tagging image {0} as {1}",
                imageId, imageName);
        log.info(message);

        try {
            dockerClient.tag(imageId, imageName, force);
        } catch (DockerException | InterruptedException e) {
            throw new MojoExecutionException("Could not tag Docker image", e);
        }

        writeImageInfo(repository, tag);

        writeMetadata(log);
    }
}
