/**
 * ==========================================================================
 * Copyright © 2015-2018 Cristiano Gavião, C8 Technology ME.
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
package br.com.c8tech.tools.maven.plugin.osgi.container.ut;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;

import java.io.File;
import java.net.URL;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import br.com.c8tech.tools.maven.plugin.osgi.container.AbstractOsgiContainerPackMojo;

public class DownloadDependenciesUnitTest extends AbstractOsgiContainerTest {

    public static String RELENG_VERSION;
    {
        URL url = getClass().getClassLoader()
                .getResource("default-container-pom.properties");
        Properties props;
        try {
            props = AbstractOsgiContainerPackMojo.loadProperties(url, "UTF-8");
            RELENG_VERSION = props.getProperty("version.c8tech.releng");
        } catch (MojoFailureException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testCopyFelixFilesFromMavenToCache() throws Exception {
        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        addDependency(project, "jars/anotherBundle.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);
        // intentionally added with invalid manifest file
        addDependency(project, "jars/aNonValidBundle.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);
        addDependency(project, "jars/aTransitiveDependencyBundle.jar", "1.0",
                false, Artifact.SCOPE_COMPILE, "jar", false);

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("container", "FELIX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-felix:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBasedir(), "target/cache"),
                "felix/bin/plugins/felix.jar",
                "felix/plugins/anotherBundle-1.0.0.jar",
                "felix/plugins/jline-3.9.0.jar",
                "felix/plugins/logback-core-1.2.3.jar",
                "felix/plugins/logback-classic-1.2.3.jar",
                "felix/plugins/jansi-1.17.1.jar",
                "felix/plugins/org.osgi.service.log-1.4.0.201802012107.jar",
                "felix/plugins/org.osgi.service.prefs-1.1.1.201505202023.jar",
                "felix/plugins/org.osgi.util.function-1.1.0.201802012106.jar",
                "felix/plugins/org.osgi.util.promise-1.1.0.201802012106.jar",
                "felix/plugins/org.osgi.util.pushstream-1.0.0.201802012107.jar",
                "felix/plugins/org.apache.felix.bundlerepository-2.0.10.jar",
                "felix/plugins/org.apache.felix.eventadmin-1.5.0.jar",
                "felix/plugins/org.apache.felix.configadmin-1.9.8.jar",
                "felix/plugins/org.apache.felix.configurator-1.0.6.jar",
                "felix/plugins/org.apache.felix.converter-1.0.2.jar",
                "felix/plugins/org.apache.felix.coordinator-1.0.2.jar",
                "felix/plugins/org.apache.felix.log-1.2.0.jar",
                "felix/plugins/org.apache.felix.metatype-1.2.2.jar",
                "felix/plugins/org.apache.felix.prefs-1.1.0.jar",
                "felix/plugins/org.apache.felix.resolver-2.0.0.jar",
                "felix/plugins/org.apache.felix.scr-2.1.10.jar",
                "felix/plugins/org.apache.felix.log-1.2.0.jar",
                "felix/plugins/org.apache.felix.logback-1.0.0.jar",
                "felix/plugins/slf4j-api-1.7.25.jar",
                "felix/plugins/org.apache.felix.gogo.jline-1.1.0.jar",
                "felix/plugins/org.apache.felix.gogo.command-1.0.2.jar",
                "felix/plugins/org.apache.felix.gogo.runtime-1.1.0.jar");
    }

    @Test
    public void testCopyEquinoxFilesFromMavenToCache() throws Exception {
        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        // intentionally added with wrong type
        addDependency(project, "jars/anotherBundle.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);
        addDependency(project, "jars/aNonValidBundle.jar", "1.0", true,
                Artifact.SCOPE_COMPILE, "jar", false);
        addDependency(project, "jars/aTransitiveDependencyBundle.jar", "1.0",
                false, Artifact.SCOPE_COMPILE, "jar", false);

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("container", "EQUINOX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-equinox:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBasedir(), "target/cache"),
                "equinox/bin/plugins/equinox.jar",
                "equinox/plugins/anotherBundle-1.0.0.jar",
                "equinox/plugins/jline-3.9.0.jar",
                "equinox/plugins/jansi-1.17.1.jar",
                "equinox/plugins/logback-core-1.2.3.jar",
                "equinox/plugins/logback-classic-1.2.3.jar",
                "equinox/plugins/slf4j-api-1.7.25.jar",
                "equinox/plugins/org.osgi.util.function-1.1.0.201802012106.jar",
                "equinox/plugins/org.osgi.util.promise-1.1.0.201802012106.jar",
                "equinox/plugins/org.osgi.util.pushstream-1.0.0.201802012107.jar",
                "equinox/plugins/org.apache.felix.gogo.jline-1.1.0.jar",
                "equinox/plugins/org.apache.felix.gogo.command-1.0.2.jar",
                "equinox/plugins/org.apache.felix.gogo.runtime-1.1.0.jar",
                "equinox/plugins/org.apache.felix.gogo.shell-1.1.0.jar",
                "equinox/plugins/org.apache.felix.configadmin-1.9.8.jar",
                "equinox/plugins/org.apache.felix.scr-2.1.10.jar",
                "equinox/plugins/org.apache.felix.bundlerepository-2.0.10.jar",
                "equinox/plugins/org.apache.felix.configurator-1.0.6.jar",
                "equinox/plugins/org.apache.felix.converter-1.0.2.jar",
                "equinox/plugins/org.apache.felix.logback-1.0.0.jar",
                "equinox/plugins/org.osgi.util.function-1.1.0.201802012106.jar",
                "equinox/plugins/org.eclipse.equinox.common-3.10.100.v20180827-1235.jar",
                "equinox/plugins/org.eclipse.equinox.console-1.3.100.v20180827-1235.jar",
                "equinox/plugins/org.eclipse.equinox.coordinator-1.3.600.v20180827-1235.jar",
                "equinox/plugins/org.eclipse.equinox.event-1.4.300.v20180827-1235.jar",
                "equinox/plugins/org.eclipse.equinox.metatype-1.4.500.v20180827-1235.jar",
                "equinox/plugins/org.eclipse.equinox.preferences-3.7.200.v20180827-1235.jar",
                "equinox/plugins/org.eclipse.equinox.util-1.1.100.v20180827-1235.jar",
                "equinox/plugins/org.eclipse.osgi.services-3.7.100.v20180827-1536.jar",
                "equinox/plugins/org.eclipse.osgi.util-3.5.100.v20180827-1536.jar");
    }

}
