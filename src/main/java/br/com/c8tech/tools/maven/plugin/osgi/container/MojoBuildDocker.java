/**
 * ==========================================================================
 * Copyright © 2015-2019 Cristiano Gavião, C8 Technology ME.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Cristiano Gavião (cvgaviao@c8tech.com.br)- initial API and implementation
 * ==========================================================================
 */
/*-
 * -\-\-
 * Dockerfile Maven Plugin
 * --
 * Copyright (C) 2017 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package br.com.c8tech.tools.maven.plugin.osgi.container;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.google.gson.Gson;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;

import br.com.c8tech.tools.maven.osgi.lib.mojo.beans.BundleRef;

@Mojo(name = "buildDockerImage", defaultPhase = LifecyclePhase.PACKAGE,
        requiresProject = true, threadSafe = true)
public class MojoBuildDocker extends AbstractOsgiDockerMojo {

    public static final String DOCKER_FILE = "Dockerfile";

    /**
     * Custom build arguments.
     */
    @Parameter(property = "dockerfile.buildArgs")
    private Map<String, String> buildArgs;

    /**
     * The name of the base image to use for the created docker image
     * <p>
     * The default is the <b>openjdk:11-jre-alpine</b>.
     */
    @Parameter(property = "dockerfile.from",
            defaultValue = "openjdk:11-jre-alpine")
    private String fromImage;

    /**
     * Do not use cache when building the image.
     */
    @Parameter(property = "dockerfile.build.noCache", defaultValue = "false")
    private boolean noCache;

    /**
     * Updates base images automatically.
     */
    @Parameter(property = "dockerfile.build.pullNewerImage",
            defaultValue = "true")
    private boolean pullNewerImage;

    /**
     * The repository to put the built image into when building the Dockerfile,
     * for example <tt>spotify/foo</tt>. You should also set the <tt>tag</tt>
     * parameter, otherwise the tag <tt>latest</tt> is used by default. If this
     * is not specified, the <tt>tag</tt> goal needs to be ran separately in
     * order to tag the generated image with anything.
     */
    @Parameter(property = "dockerfile.repository")
    private String repository;

    /**
     * The tag to apply when building the Dockerfile, which is appended to the
     * repository.
     */
    @Parameter(property = "dockerfile.tag", defaultValue = "latest")
    private String tag;

    @Inject
    protected MojoBuildDocker(MavenProject pProject) {
        super(pProject);
    }

    @Nullable
    static String buildImage(@Nonnull DockerClient dockerClient, // NOSONAR
            @Nonnull Log log, boolean verbose, @Nonnull File contextDirectory,
            @Nullable String repository, @Nonnull String tag,
            boolean pullNewerImage, boolean noCache,
            @Nullable Map<String, String> buildArgs)
            throws MojoExecutionException, MojoFailureException {

        log.info(MessageFormat.format("Building Docker context {0}",
                contextDirectory));

        if (!new File(contextDirectory, DOCKER_FILE).exists()
                && !new File(contextDirectory, "dockerfile").exists()) {
            log.error("Missing Dockerfile in context directory: "
                    + contextDirectory.getPath());
            throw new MojoFailureException(
                    "Missing Dockerfile in context directory: "
                            + contextDirectory.getPath());
        }

        final LoggingProgressHandler progressHandler = new LoggingProgressHandler(
                log, verbose);
        final ArrayList<DockerClient.BuildParam> buildParameters = new ArrayList<>();
        if (pullNewerImage) {
            buildParameters.add(DockerClient.BuildParam.pullNewerImage());
        }
        if (noCache) {
            buildParameters.add(DockerClient.BuildParam.noCache());
        }

        if (buildArgs != null && !buildArgs.isEmpty()) {
            try {
                final String encodedBuildArgs = URLEncoder
                        .encode(new Gson().toJson(buildArgs), "utf-8");
                buildParameters.add(new DockerClient.BuildParam("buildargs",
                        encodedBuildArgs));
            } catch (UnsupportedEncodingException e) {
                throw new MojoExecutionException("Could not build image", e);
            }
        }

        final DockerClient.BuildParam[] buildParametersArray = buildParameters
                .toArray(new DockerClient.BuildParam[buildParameters.size()]);

        log.info(""); // Spacing around build progress
        try {
            if (repository != null) {
                final String name = formatImageName(repository, tag);
                log.info(MessageFormat.format("Image will be built as {0}",
                        name));
                log.info(""); // Spacing around build progress
                dockerClient.build(contextDirectory.toPath(), name,
                        progressHandler, buildParametersArray);
            } else {
                log.info("Image will be built without a name");
                log.info(""); // Spacing around build progress
                dockerClient.build(contextDirectory.toPath(), progressHandler,
                        buildParametersArray);
            }
        } catch (DockerException | IOException | InterruptedException e) {
            throw new MojoExecutionException("Could not build image", e);
        }
        log.info(""); // Spacing around build progress

        return progressHandler.builtImageId();
    }

    @Override
    public void execute(DockerClient dockerClient)
            throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();

        final String imageId = buildImage(dockerClient, log, isVerbose(),
                getBuildDir(), repository, tag, pullNewerImage, noCache,
                buildArgs);

        if (imageId == null) {
            log.warn("Docker build was successful, but no image was built");
        } else {
            log.info(MessageFormat.format("Detected build of image with id {0}",
                    imageId));
            writeMetadata(Metadata.IMAGE_ID, imageId);
        }

        // Do this after the build so that other goals don't use the tag if it
        // doesn't exist
        if (repository != null) {
            writeImageInfo(repository, tag);
        }

        writeMetadata(log);

        if (repository == null) {
            log.info(MessageFormat.format("Successfully built {0}", imageId));
        } else {
            log.info(MessageFormat.format("Successfully built {0}",
                    formatImageName(repository, tag)));
        }
    }

    @Override
    protected void executeExtraInitializationSteps()
            throws MojoExecutionException, MojoFailureException {

        try {
            if (getContainer().equals(Container.EQUINOX)
                    && (dockerConfigFile == null
                            || "".equals(dockerConfigFile.getName()))) {
                // copy the resources for equinox to cache directory
                copyInternalFileToProjectDir("/distrib/equinox/", DOCKER_FILE,
                        getBuildDir().toPath());
            }

            // copy the resources for felix to cache directory
            if (getContainer().equals(Container.FELIX)
                    && (dockerConfigFile == null
                            || "".equals(dockerConfigFile.getName()))) {
                copyInternalFileToProjectDir("/distrib/felix/", DOCKER_FILE,
                        getBuildDir().toPath());
            }
        } catch (IOException e) {
            throw new MojoFailureException("Failure copying Dockerfile.", e);
        }

        Map<String, String> args = new HashMap<>();
        args.put("PROJECT_ID", getProject().getArtifactId());
        args.put("IMAGE_VERSION", getProject().getVersion());
        args.put("IMAGE_RELEASE_DATE", Instant.now().toString());

        String buildArchivePath = getProject().getBuild().getFinalName()
                + ".tar.gz";

        args.put("ZIP_FILE", buildArchivePath);
        if (getConfiguratorInitialFile() != null)
            args.put("CONFIGURATOR_INIT_FILE_NAME",
                    getConfiguratorInitialFile().getName());

        if (getLocalRepositoryGAV() != null
                && !getLocalRepositoryGAV().isEmpty()) {

            BundleRef br = new BundleRef(getLocalRepositoryGAV());
            br.setType("osgi.repository");
            if (!br.isValid()) {
                throw new MojoExecutionException(
                        "LocalRepositoryGAV is not valid: "
                                + getLocalRepositoryGAV());
            }

            try {
                Artifact art = getDependenciesHelper().resolveArtifact(br,
                        getRepositorySystem(), getRemoteRepositories(),
                        getLocalRepository());
                if (art == null) {
                    throw new MojoExecutionException(
                            "LocalRepositoryGAV was not resolved: "
                                    + getLocalRepositoryGAV());
                }

                args.put("LOCAL_REPOSITORY_ID", art.getFile().getName());
            } catch (IOException e) {
                throw new MojoFailureException(
                        "Failure getting repository artifact.", e);
            }

        }

        if (buildArgs == null) {
            buildArgs = args;
        } else {
            buildArgs.putAll(args);
        }
    }

}
