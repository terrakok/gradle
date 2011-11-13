/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec

/**
 * by Szczepan Faber, created at: 11/9/11
 */
class DependencyNotationIntegrationSpec extends AbstractIntegrationSpec {

    def "understands dependency notations"() {
        when:
        settingsFile << "include 'otherProject'"

        buildFile <<  """
import org.gradle.api.internal.artifacts.dependencies.*
configurations {
    conf
    gradleStuff
    allowsCollections
}

def someDependency = new DefaultSelfResolvingDependency(files('foo.txt'))
dependencies {
    conf someDependency
    conf "org.mockito:mockito-core:1.8"
    conf group: 'org.spockframework', name: 'spock-core', version: '1.0'
    conf project(':otherProject')
    conf module('org.foo:moduleOne:1.0'), module('org.foo:moduleTwo:1.0')

    gradleStuff gradleApi()

    allowsCollections "org.mockito:mockito-core:1.8", project(':otherProject')
}

task checkDeps << {
    def deps = configurations.conf.incoming.dependencies
    assert deps.contains(someDependency)
    assert deps.find { it instanceof ExternalDependency && it.group == 'org.mockito' && it.name == 'mockito-core' && it.version == '1.8'  }
    assert deps.find { it instanceof ExternalDependency && it.group == 'org.spockframework' && it.name == 'spock-core' && it.version == '1.0'  }
    assert deps.find { it instanceof ProjectDependency && it.dependencyProject.path == ':otherProject' }
    assert deps.find { it instanceof ClientModule && it.name == 'moduleOne' && it.group == 'org.foo' }
    assert deps.find { it instanceof ClientModule && it.name == 'moduleTwo' && it.version == '1.0' }

    deps = configurations.gradleStuff.dependencies
    assert deps.findAll { it instanceof SelfResolvingDependency }.size() > 0 : "should include gradle api jars"

    deps = configurations.allowsCollections.dependencies
    assert deps.size() == 2
    assert deps.find { it instanceof ExternalDependency && it.group == 'org.mockito' }
    assert deps.find { it instanceof ProjectDependency && it.dependencyProject.path == ':otherProject' }
}
"""
        then:
        succeeds 'checkDeps'
    }

      def "understands client module dependency notations with dependencies"() {
        when:
        buildFile <<  """
configurations {
    conf
}

dependencies {
    conf module('org.foo:moduleOne:1.0') {
        dependency 'org.foo:bar:1.0'
    }
}

task checkDeps << {
    def deps = configurations.conf.incoming.dependencies
    assert deps.size() == 1
    def dep = deps.find { it instanceof ClientModule && it.name == 'moduleOne' }
    assert dep
    assert dep.dependencies.size() == 1
    assert dep.dependencies.find { it.group == 'org.foo' && it.name == 'bar' && it.version == '1.0' }
}
"""
        then:
        succeeds 'checkDeps'
    }

    def "fails gracefully for invalid notations"() {
        when:
        buildFile <<  """
configurations {
    conf
}

dependencies {
    conf 100
}

task checkDeps
"""
        then:
        fails 'checkDeps'
        errorOutput.contains 'notation is invalid'
    }
}
