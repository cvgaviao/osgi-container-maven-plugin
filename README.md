[![CircleCI](https://circleci.com/gh/cvgaviao/osgi-container-maven-plugin.svg?style=svg)](https://circleci.com/gh/cvgaviao/osgi-container-maven-plugin)

[![Maven Central](https://img.shields.io/maven-central/v/br.com.c8tech.tools/osgi-container-maven-plugin.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22br.com.c8tech.tools%22%20AND%20a:%22osgi-container-maven-plugin%22)

OSGi Container Distribution Archive Generator Maven Plugin 
================================

This git repository contains a maven plugin project that is aimed to generate a compressed archive containing an OSGi container structure plus the necessary OSGi bundles necessary to be distributed and ready to be used.

## Docker ready

We have have borrowed some code from the `spotify's dockerfile maven plugin` and integrated into the plugin's lifecycle in order to produce an docker image  from the produced archive and ready to be deployed.

------------
## Documentation, download and usage instructions details

Full usage details, FAQs, examples and more are available on the
**[project documentation website](http://cvgaviao.github.io/osgi-container-maven-plugin/index.html)**.

## Development


### Building

To build and run the tests, you need Java 8 or later and Maven 3.5.4 or later. 
Simply clone this repository and run `mvn clean install`

In order to run the with test coverage support then run `mvn clean install -Dc8tech.build.test.coverage`

#### Using Eclipse IDE + m2e
You can use the Eclipse IDE justing importing the project into a workspace. It will automatically ask you to install the m2e related plugins.
Also, in order to facilitate to build, some m2e launcher files are provided in the .m2e-launchers directory.

### Contributing
Note that the tests run the plugin against a number of sample test projects, located in the `test-projects` folder.
If adding new functionality, or fixing a bug, it is recommended that a sample project be set up so that the scenario
can be tested end-to-end.
See also [CONTRIBUTING.md](CONTRIBUTING.md) for information on deploying to Nexus and releasing the plugin.

