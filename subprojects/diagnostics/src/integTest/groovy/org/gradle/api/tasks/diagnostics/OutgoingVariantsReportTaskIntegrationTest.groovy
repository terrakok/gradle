/*
 * Copyright 2019 the original author or authors.
 *
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
 */

package org.gradle.api.tasks.diagnostics

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InspectsConfigurationReport
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class OutgoingVariantsReportTaskIntegrationTest extends AbstractIntegrationSpec implements InspectsConfigurationReport {
    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "if no configurations present in project, task reports complete absence"() {
        expect:
        succeeds ':outgoingVariants'
        reportsCompleteAbsenceOfResolvableVariants()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "if only resolvable configurations present, task reports complete absence"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false
            }
        """

        expect:
        succeeds ':outgoingVariants'
        reportsCompleteAbsenceOfResolvableVariants()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "if only legacy configuration present, and --all not specified, task produces empty report and prompts for rerun"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My legacy configuration"
                canBeResolved = true
                canBeConsumed = true
            }
        """

        expect:
        succeeds ':outgoingVariants'
        reportsNoProperVariants()
        promptsForRerunToFindMoreVariants()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "if only legacy configuration present, task reports it if --all flag is set"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My custom legacy configuration"
                canBeResolved = true
                canBeConsumed = true
            }
        """

        when:
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':outgoingVariants', '--all'

        then:
        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant legacy (l)
--------------------------------------------------
My custom legacy configuration""")

        and:
        hasLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreConfigurations()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "if single outgoing variant with no attributes or artifacts present, task reports it"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = false
                canBeConsumed = true
            }
        """

        when:
        succeeds ':outgoingVariants'

        then:
        result.groupedOutput.task(":outgoingVariants").assertOutputContains """--------------------------------------------------
Variant custom
--------------------------------------------------
My custom configuration
"""
        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreVariants()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "if single outgoing variant present with attributes, task reports it and them"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = false
                canBeConsumed = true

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }
        """

        when:
        succeeds ':outgoingVariants'

        then:
        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant custom
--------------------------------------------------
My custom configuration

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime""".stripIndent())

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreVariants()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "If multiple outgoing variants present with attributes, task reports them all, sorted alphabetically"() {
        given:
        buildFile << """
            configurations.create("someConf") {
                description = "My first custom configuration"
                canBeResolved = false
                canBeConsumed = true

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }

            configurations.create("otherConf") {
                description = "My second custom configuration"
                canBeResolved = false
                canBeConsumed = true

                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.DOCUMENTATION));
                }
            }
        """

        when:
        succeeds ':outgoingVariants'

        then:
        result.groupedOutput.task(":outgoingVariants").assertOutputContains """--------------------------------------------------
Variant otherConf
--------------------------------------------------
My second custom configuration

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.category = documentation

--------------------------------------------------
Variant someConf
--------------------------------------------------
My first custom configuration

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime"""

        and:
        doesNotHaveLegacyLegend()
        doesNotHaveIncubatingLegend()
        doesNotPromptForRerunToFindMoreVariants()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "reports outgoing variants of a Java Library"() {
        buildFile << """
            plugins { id 'java-library' }
            group = 'org'
            version = '1.0'
        """

        when:
        run ':outgoingVariants'

        then:
        def jarPath = file('build/libs/myLib-1.0.jar').getRelativePathFromBase()
        def builtMainClassesPath = file('build/classes/java/main').getRelativePathFromBase()
        def builtMainResourcesPath = file('build/resources/main').getRelativePathFromBase()
        def sourceMainJavaPath = file('src/main/java').getRelativePathFromBase()
        def sourceMainResourcePath = file('src/main/resources').getRelativePathFromBase()
        def resultsBinPath = file('build/test-results/test/binary').getRelativePathFromBase()
        result.groupedOutput.task(":outgoingVariants").assertOutputContains """--------------------------------------------------
Variant apiElements
--------------------------------------------------
API elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-api
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

--------------------------------------------------
Variant mainSourceElements (i)
--------------------------------------------------
List of source directories contained in the Main SourceSet.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = verification
    - org.gradle.dependency.bundling = external
    - org.gradle.verificationtype    = main-sources
Artifacts
    - $sourceMainJavaPath (artifactType = directory)
    - $sourceMainResourcePath (artifactType = directory)

--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Elements of runtime for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

    --------------------------------------------------
    Secondary Variant resources
    --------------------------------------------------
    Directories containing the project's assembled resource files for use at runtime.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = resources
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainResourcesPath (artifactType = java-resources-directory)

--------------------------------------------------
Variant testResultsElementsForTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the test Test Suite's test target.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = test
    - org.gradle.testsuite.target.name = test
    - org.gradle.testsuite.type        = unit-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsBinPath (artifactType = directory)
"""
        and:
        doesNotHaveLegacyLegend()
        hasIncubatingLegend()
        hasSecondaryVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "reports outgoing variants of a Java Library with documentation"() {
        buildFile << """
            plugins { id 'java-library' }
            java {
                withJavadocJar()
                withSourcesJar()
            }
            group = 'org'
            version = '1.0'
        """

        when:
        run ':outgoingVariants'

        then:
        def jarPath = file('build/libs/myLib-1.0.jar').getRelativePathFromBase()
        def javadocJarPath = file('build/libs/myLib-1.0-javadoc.jar').getRelativePathFromBase()
        def sourcesJarPath = file('build/libs/myLib-1.0-sources.jar').getRelativePathFromBase()
        def builtMainClassesPath = file('build/classes/java/main').getRelativePathFromBase()
        def builtMainResourcesPath = file('build/resources/main').getRelativePathFromBase()
        def sourceMainJavaPath = file('src/main/java').getRelativePathFromBase()
        def sourceMainResourcePath = file('src/main/resources').getRelativePathFromBase()
        def resultsBinPath = file('build/test-results/test/binary').getRelativePathFromBase()
        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant apiElements
--------------------------------------------------
API elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-api
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

--------------------------------------------------
Variant javadocElements
--------------------------------------------------
javadoc elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = documentation
    - org.gradle.dependency.bundling = external
    - org.gradle.docstype            = javadoc
    - org.gradle.usage               = java-runtime
Artifacts
    - $javadocJarPath (artifactType = jar, classifier = javadoc)

--------------------------------------------------
Variant mainSourceElements (i)
--------------------------------------------------
List of source directories contained in the Main SourceSet.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = verification
    - org.gradle.dependency.bundling = external
    - org.gradle.verificationtype    = main-sources
Artifacts
    - $sourceMainJavaPath (artifactType = directory)
    - $sourceMainResourcePath (artifactType = directory)

--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Elements of runtime for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

    --------------------------------------------------
    Secondary Variant resources
    --------------------------------------------------
    Directories containing the project's assembled resource files for use at runtime.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = resources
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainResourcesPath (artifactType = java-resources-directory)

--------------------------------------------------
Variant sourcesElements
--------------------------------------------------
sources elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = documentation
    - org.gradle.dependency.bundling = external
    - org.gradle.docstype            = sources
    - org.gradle.usage               = java-runtime
Artifacts
    - $sourcesJarPath (artifactType = jar, classifier = sources)

--------------------------------------------------
Variant testResultsElementsForTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the test Test Suite's test target.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = test
    - org.gradle.testsuite.target.name = test
    - org.gradle.testsuite.type        = unit-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsBinPath (artifactType = directory)
""")
        and:
        doesNotHaveLegacyLegend()
        hasSecondaryVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "reports outgoing variants of a Java Library with documentation including test data variants"() {
        buildFile << """
            plugins { id 'java-library' }
            java {
                withJavadocJar()
                withSourcesJar()
            }
            group = 'org'
            version = '1.0'
        """.stripIndent()

        when:
        run ':outgoingVariants'

        then:
        def jarPath = file('build/libs/myLib-1.0.jar').getRelativePathFromBase()
        def javadocJarPath = file('build/libs/myLib-1.0-javadoc.jar').getRelativePathFromBase()
        def sourcesJarPath = file('build/libs/myLib-1.0-sources.jar').getRelativePathFromBase()
        def builtMainClassesPath = file('build/classes/java/main').getRelativePathFromBase()
        def builtMainResourcesPath = file('build/resources/main').getRelativePathFromBase()
        def sourceMainJavaPath = file('src/main/java').getRelativePathFromBase()
        def sourceMainResourcePath = file('src/main/resources').getRelativePathFromBase()
        def resultsBinPath = file('build/test-results/test/binary').getRelativePathFromBase()
        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant apiElements
--------------------------------------------------
API elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-api
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

--------------------------------------------------
Variant javadocElements
--------------------------------------------------
javadoc elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = documentation
    - org.gradle.dependency.bundling = external
    - org.gradle.docstype            = javadoc
    - org.gradle.usage               = java-runtime
Artifacts
    - $javadocJarPath (artifactType = jar, classifier = javadoc)

--------------------------------------------------
Variant mainSourceElements (i)
--------------------------------------------------
List of source directories contained in the Main SourceSet.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = verification
    - org.gradle.dependency.bundling = external
    - org.gradle.verificationtype    = main-sources
Artifacts
    - $sourceMainJavaPath (artifactType = directory)
    - $sourceMainResourcePath (artifactType = directory)

--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Elements of runtime for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

    --------------------------------------------------
    Secondary Variant resources
    --------------------------------------------------
    Directories containing the project's assembled resource files for use at runtime.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = resources
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainResourcesPath (artifactType = java-resources-directory)

--------------------------------------------------
Variant sourcesElements
--------------------------------------------------
sources elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = documentation
    - org.gradle.dependency.bundling = external
    - org.gradle.docstype            = sources
    - org.gradle.usage               = java-runtime
Artifacts
    - $sourcesJarPath (artifactType = jar, classifier = sources)

--------------------------------------------------
Variant testResultsElementsForTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the test Test Suite's test target.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = test
    - org.gradle.testsuite.target.name = test
    - org.gradle.testsuite.type        = unit-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsBinPath (artifactType = directory)
""")

        and:
        doesNotHaveLegacyLegend()
        hasSecondaryVariantsLegend()
        hasIncubatingLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "reports a single outgoing variant of a Java Library"() {
        buildFile << """
            plugins { id 'java-library' }
            group = 'org'
            version = '1.0'
        """

        when:
        run ':outgoingVariants', '--variant', 'runtimeElements'

        then:
        def jarPath = file('build/libs/myLib-1.0.jar').getRelativePathFromBase()
        def builtMainClassesPath = file('build/classes/java/main').getRelativePathFromBase()
        def builtMainResourcesPath = file('build/resources/main').getRelativePathFromBase()
        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Elements of runtime for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

    --------------------------------------------------
    Secondary Variant resources
    --------------------------------------------------
    Directories containing the project's assembled resource files for use at runtime.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = resources
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainResourcesPath (artifactType = java-resources-directory)
""")

        and:
        doesNotHaveLegacyLegend()
        hasSecondaryVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "can show all variants"() {
        buildFile << """
            plugins { id 'java-library' }
            group = 'org'
            version = '1.0'
        """

        when:
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':outgoingVariants', '--all'

        then:
        def jarPath = file('build/libs/myLib-1.0.jar').getRelativePathFromBase()
        def builtMainClassesPath = file('build/classes/java/main').getRelativePathFromBase()
        def builtMainResourcesPath = file('build/resources/main').getRelativePathFromBase()
        def sourceMainJavaPath = file('src/main/java').getRelativePathFromBase()
        def sourceMainResourcePath = file( 'src/main/resources').getRelativePathFromBase()
        def resultsBinPath = file('build/test-results/test/binary').getRelativePathFromBase()

        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant apiElements
--------------------------------------------------
API elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-api
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

--------------------------------------------------
Variant archives (l)
--------------------------------------------------
Configuration for archive artifacts.

Capabilities
    - org:myLib:1.0 (default capability)
Artifacts
    - $jarPath (artifactType = jar)

--------------------------------------------------
Variant default (l)
--------------------------------------------------
Configuration for default artifacts.

Capabilities
    - org:myLib:1.0 (default capability)
Artifacts
    - $jarPath (artifactType = jar)

--------------------------------------------------
Variant mainSourceElements (i)
--------------------------------------------------
List of source directories contained in the Main SourceSet.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = verification
    - org.gradle.dependency.bundling = external
    - org.gradle.verificationtype    = main-sources
Artifacts
    - $sourceMainJavaPath (artifactType = directory)
    - $sourceMainResourcePath (artifactType = directory)

--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Elements of runtime for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

    --------------------------------------------------
    Secondary Variant resources
    --------------------------------------------------
    Directories containing the project's assembled resource files for use at runtime.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = resources
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainResourcesPath (artifactType = java-resources-directory)

--------------------------------------------------
Variant testResultsElementsForTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the test Test Suite's test target.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = test
    - org.gradle.testsuite.target.name = test
    - org.gradle.testsuite.type        = unit-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsBinPath (artifactType = directory)
""")

        and:
        hasLegacyLegend()
        hasIncubatingLegend()
        hasSecondaryVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "can show all variants including test data variants"() {
        buildFile << """
            plugins { id 'java-library' }
            group = 'org'
            version = '1.0'
        """.stripIndent()

        when:
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':outgoingVariants', '--all'

        then:
        def jarPath = file('build/libs/myLib-1.0.jar').getRelativePathFromBase()
        def builtMainClassesPath = file('build/classes/java/main').getRelativePathFromBase()
        def builtMainResourcesPath = file('build/resources/main').getRelativePathFromBase()
        def sourceMainJavaPath = file('src/main/java').getRelativePathFromBase()
        def sourceMainResourcePath = file('src/main/resources').getRelativePathFromBase()
        def resultsBinPath = file('build/test-results/test/binary').getRelativePathFromBase()
        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant apiElements
--------------------------------------------------
API elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-api
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

--------------------------------------------------
Variant archives (l)
--------------------------------------------------
Configuration for archive artifacts.

Capabilities
    - org:myLib:1.0 (default capability)
Artifacts
    - $jarPath (artifactType = jar)

--------------------------------------------------
Variant default (l)
--------------------------------------------------
Configuration for default artifacts.

Capabilities
    - org:myLib:1.0 (default capability)
Artifacts
    - $jarPath (artifactType = jar)

--------------------------------------------------
Variant mainSourceElements (i)
--------------------------------------------------
List of source directories contained in the Main SourceSet.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = verification
    - org.gradle.dependency.bundling = external
    - org.gradle.verificationtype    = main-sources
Artifacts
    - $sourceMainJavaPath (artifactType = directory)
    - $sourceMainResourcePath (artifactType = directory)

--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Elements of runtime for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

    --------------------------------------------------
    Secondary Variant resources
    --------------------------------------------------
    Directories containing the project's assembled resource files for use at runtime.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = resources
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainResourcesPath (artifactType = java-resources-directory)

--------------------------------------------------
Variant testResultsElementsForTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the test Test Suite's test target.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = test
    - org.gradle.testsuite.target.name = test
    - org.gradle.testsuite.type        = unit-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsBinPath (artifactType = directory)
""")

        and:
        hasLegacyLegend()
        hasIncubatingLegend()
        hasSecondaryVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "prints explicit capabilities"() {
        buildFile << """
            plugins { id 'java-library' }

            configurations.runtimeElements.outgoing {
                capability("org.test:extra:1.0")
                capability("org.test:other:3.0")
            }
"""

        when:
        run ':outgoingVariants', '--variant', 'runtimeElements'

        then:
        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Elements of runtime for main.

Capabilities
    - org.test:extra:1.0
    - org.test:other:3.0
""")
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "reports artifacts without explicit type"() {
        buildFile << """
            plugins { id 'java-library' }

            group = 'org'
            version = '1.0'

            configurations.runtimeElements.outgoing.variants {
                classes {
                   artifact(file("foo"))
                }
            }
        """

        when:
        run ':outgoingVariants', '--variant', 'runtimeElements'

        then:
        def jarPath = file('build/libs/myLib-1.0.jar').getRelativePathFromBase()
        def builtMainClassesPath = file('build/classes/java/main').getRelativePathFromBase()
        def builtMainResourcesPath = file('build/resources/main').getRelativePathFromBase()
        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Elements of runtime for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-runtime
    Artifacts
        - foo
        - $builtMainClassesPath (artifactType = java-classes-directory)

    --------------------------------------------------
    Secondary Variant resources
    --------------------------------------------------
    Directories containing the project's assembled resource files for use at runtime.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = resources
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainResourcesPath (artifactType = java-resources-directory)
""")

        and:
        doesNotHaveLegacyLegend()
        hasSecondaryVariantsLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "variants using custom VERIFICATION_TYPE attribute values are reported as incubating"() {
        buildFile << """
            plugins { id 'java-library' }

            group = 'org'
            version = '1.0'

            def sample = configurations.create("sample") {
                visible = true
                canBeResolved = false
                canBeConsumed = true

                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                }
            }
        """

        when:
        run ':outgoingVariants'

        then:
        def jarPath = file('build/libs/myLib-1.0.jar').getRelativePathFromBase()
        def builtMainClassesPath = file('build/classes/java/main').getRelativePathFromBase()
        def builtMainResourcesPath = file('build/resources/main').getRelativePathFromBase()
        def sourceMainJavaPath = file('src/main/java').getRelativePathFromBase()
        def sourceMainResourcePath = file('src/main/resources').getRelativePathFromBase()
        def resultsBinPath = file('build/test-results/test/binary').getRelativePathFromBase()
        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant apiElements
--------------------------------------------------
API elements for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-api
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-api
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

--------------------------------------------------
Variant mainSourceElements (i)
--------------------------------------------------
List of source directories contained in the Main SourceSet.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = verification
    - org.gradle.dependency.bundling = external
    - org.gradle.verificationtype    = main-sources
Artifacts
    - $sourceMainJavaPath (artifactType = directory)
    - $sourceMainResourcePath (artifactType = directory)

--------------------------------------------------
Variant runtimeElements
--------------------------------------------------
Elements of runtime for main.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
Artifacts
    - $jarPath (artifactType = jar)

Secondary Variants (*)

    --------------------------------------------------
    Secondary Variant classes
    --------------------------------------------------
    Directories containing compiled class files for main.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = classes
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainClassesPath (artifactType = java-classes-directory)

    --------------------------------------------------
    Secondary Variant resources
    --------------------------------------------------
    Directories containing the project's assembled resource files for use at runtime.

    Attributes
        - org.gradle.category            = library
        - org.gradle.dependency.bundling = external
        - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
        - org.gradle.libraryelements     = resources
        - org.gradle.usage               = java-runtime
    Artifacts
        - $builtMainResourcesPath (artifactType = java-resources-directory)

--------------------------------------------------
Variant sample (i)
--------------------------------------------------

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category = verification

--------------------------------------------------
Variant testResultsElementsForTest (i)
--------------------------------------------------
Directory containing binary results of running tests for the test Test Suite's test target.

Capabilities
    - org:myLib:1.0 (default capability)
Attributes
    - org.gradle.category              = verification
    - org.gradle.testsuite.name        = test
    - org.gradle.testsuite.target.name = test
    - org.gradle.testsuite.type        = unit-test
    - org.gradle.verificationtype      = test-results
Artifacts
    - $resultsBinPath (artifactType = directory)
""")

        and:
        doesNotHaveLegacyLegend()
        hasSecondaryVariantsLegend()
        hasIncubatingLegend()
    }

    @ToBeFixedForConfigurationCache(because = ":outgoingVariants")
    def "custom artifact with classifier is printed"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = false
                canBeConsumed = true
            }

            task redJar(type: Jar) {
                archiveClassifier = 'red'
                from(sourceSets.main.output)
            }

            artifacts {
                custom redJar
            }
        """.stripIndent()

        and:
        file("src/main/java/Hello.java") << """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello World!");
                }
            }
        """

        when:
        succeeds ':outgoingVariants'

        then:
        def jarPath = file('build/libs/myLib-red.jar').getRelativePathFromBase()

        result.groupedOutput.task(":outgoingVariants").assertOutputContains("""--------------------------------------------------
Variant custom
--------------------------------------------------
My custom configuration

Capabilities
    - :myLib:unspecified (default capability)
Artifacts
    - $jarPath (artifactType = jar, classifier = red)
""")

        and:
        doesNotHaveLegacyLegend()
        hasIncubatingLegend()
        doesNotPromptForRerunToFindMoreVariants()
    }
}
