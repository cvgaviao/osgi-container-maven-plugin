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
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import br.com.c8tech.tools.maven.plugin.osgi.container.AbstractOsgiContainerPackMojo;

public class GenerateConfigurationFileUnitTest
        extends AbstractOsgiContainerTest {

    public static String RELENG_VERSION;
    {
        URL url = getClass().getClassLoader()
                .getResource("default-container-pom.properties");
        Properties props;
        try {
            props = AbstractOsgiContainerPackMojo.loadProperties(url, "UTF-8");
            RELENG_VERSION = props.getProperty("c8tech.releng.version");
        } catch (MojoFailureException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testFelixConfigurationFileGeneratioWithDefaultValues()
            throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("container", "FELIX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-felix:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.executeMojo(project, "generateConfigurationFile",
                newParameter("verbose", "true"),
                newParameter("container", "FELIX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-felix:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBuild().getDirectory()),
                "/work/felix/config/config.properties");

    }

    @Test
    public void testFelixConfigurationFileGeneratioWithCustomFile()
            throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("container", "FELIX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-felix:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.executeMojo(project, "generateConfigurationFile",
                newParameter("verbose", "true"),
                newParameter("skipConfigurationGen", "false"),
                newParameter("container", "FELIX"),
                newParameter("baseContainerConfigurationFileUrl",
                        "classpath:/files/config.clean.properties"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-felix:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile")

        );

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBuild().getDirectory()),
                "/work/felix/config/config.properties");

        URL expected = getClass().getResource("/files/config.full.properties");
        Properties smExpected = new Properties();
        try (InputStream is = expected.openStream()) {
            Reader reader = new InputStreamReader(is, "UTF-8");
            smExpected.load(reader);

        }

        File outputFile = new File(project.getBuild().getDirectory(),
                "/work/felix/config/config.properties");
        Properties smActual = new Properties();
        try (InputStream is = new FileInputStream(outputFile)) {
            Reader reader = new InputStreamReader(is, "UTF-8");
            smActual.load(reader);

        }
        assertThat(smActual.size()).isEqualTo(smExpected.size());

        assertThat(smActual).isEqualTo(smExpected);
    }

    @Test
    public void testFelixConfigurationFileGeneratioWithCustomFileSkippingBundleCalculation()
            throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("container", "FELIX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-felix:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.executeMojo(project, "generateConfigurationFile",
                newParameter("verbose", "true"),
                newParameter("skipConfigurationGen", "true"),
                newParameter("container", "FELIX"),
                newParameter("baseContainerConfigurationFileUrl",
                        "classpath:/files/config.full.properties"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-felix:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule
                .assertBuildOutputs(new File(project.getBuild().getDirectory(),
                        "/work/felix/config.properties"));

        URL expected = getClass().getResource("/files/config.full.properties");
        Properties smExpected = new Properties();
        try (InputStream is = expected.openStream()) {
            Reader reader = new InputStreamReader(is, "UTF-8");
            smExpected.load(reader);

        }

        File outputFile = new File(project.getBuild().getDirectory(),
                "/work/felix/config/config.properties");
        Properties smActual = new Properties();
        try (InputStream is = new FileInputStream(outputFile)) {
            Reader reader = new InputStreamReader(is, "UTF-8");
            smActual.load(reader);

        }
        assertThat(smActual).isEqualTo(smExpected);
    }

    @Test
    public void testEquinoxConfigurationFileGeneratioWithDefaultValues()
            throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("container", "EQUINOX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-equinox:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.executeMojo(project, "generateConfigurationFile",
                newParameter("verbose", "true"),
                newParameter("container", "EQUINOX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-equinox:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBuild().getDirectory()),
                "/work/equinox/config/config.ini");

    }

//    @Test
    public void testEquinoxConfigurationFileGeneratioWithCustomFile()
            throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("container", "EQUINOX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-equinox:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.executeMojo(project, "generateConfigurationFile",
                newParameter("verbose", "true"),
                newParameter("skipConfigurationGen", "false"),
                newParameter("container", "EQUINOX"),
                newParameter("baseContainerConfigurationFileUrl",
                        "classpath:/files/config.clean.ini"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-equinox:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSets(newParameterMavenArtifactsSet(
                        "org.eclipse.platform:org.eclipse.equinox.console:jar@4")));

        incrementalBuildRule.assertBuildOutputs(
                new File(project.getBuild().getDirectory()),
                "/work/equinox/config.ini");

        URL expected = getClass().getResource("/files/config.full.ini");
        Properties smExpected = new Properties();
        try (InputStream is = expected.openStream()) {
            Reader reader = new InputStreamReader(is, "UTF-8");
            smExpected.load(reader);

        }

        File outputFile = new File(project.getBuild().getDirectory(),
                "/work/equinox/config.ini");
        Properties smActual = new Properties();
        try (InputStream is = new FileInputStream(outputFile)) {
            Reader reader = new InputStreamReader(is, "UTF-8");
            smActual.load(reader);

        }
        assertThat(smActual.size()).isEqualTo(smExpected.size());

        Set<String> setActual = new TreeSet<String>(Arrays.asList(
                smActual.get("osgi.bundles").toString().split("\\s*,\\s*")));
        Set<String> setExpected = new TreeSet<String>(Arrays.asList(
                smExpected.get("osgi.bundles").toString().split("\\s*,\\s*")));

        assertThat(setActual).containsExactlyInAnyOrderElementsOf(setExpected);
    }

    @Test
    public void testEquinoxConfigurationFileGeneratioWithCustomFileSkippingBundleCalculation()
            throws Exception {

        MavenProject project = incrementalBuildRule.readMavenProject(
                testResources.getBasedir("ut-project--normal"));

        incrementalBuildRule.executeMojo(project, "cacheMavenArtifacts",
                newParameter("verbose", "true"),
                newParameter("container", "EQUINOX"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-equinox:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.executeMojo(project, "generateConfigurationFile",
                newParameter("verbose", "true"),
                newParameter("skipConfigurationGen", "true"),
                newParameter("container", "EQUINOX"),
                newParameter("baseContainerConfigurationFileUrl",
                        "classpath:/files/config.full.ini"),
                newParameter("containerPomDependenciesGAV",
                        "br.com.c8tech.releng:fpom-deps-equinox:pom:"
                                + RELENG_VERSION),
                newParameter("scopes", "compile"),
                newParameterMavenArtifactsSet("test:anotherBundle:jar:1.0"));

        incrementalBuildRule.assertBuildOutputs(new File(
                project.getBuild().getDirectory(), "/work/equinox/config.ini"));

        URL expected = getClass().getResource("/files/config.full.ini");
        Properties smExpected = new Properties();
        try (InputStream is = expected.openStream()) {
            Reader reader = new InputStreamReader(is, "UTF-8");
            smExpected.load(reader);

        }

        File outputFile = new File(project.getBuild().getDirectory(),
                "/work/equinox/config/config.ini");
        Properties smActual = new Properties();
        try (InputStream is = new FileInputStream(outputFile)) {
            Reader reader = new InputStreamReader(is, "UTF-8");
            smActual.load(reader);

        }
        assertThat(smActual).isEqualTo(smExpected);
    }

    // @Test(expected = MojoExecutionException.class)
    public void testWrongPackagingFailure() throws Exception {
        File basedir = testResources
                .getBasedir("ut-project--fail-wrong-packaging");
        incrementalBuildRule.executeMojo(basedir, "generateConfigurationFile");

    }

}
