library identifier: 'ci-shared-library@main', retriever: modernSCM(
        [$class: 'GitSCMSource',
         remote: 'https://github.com/cb-ci-templates/ci-shared-library.git'])

// Building the data object
def configYaml = """---
FILE_PATH: 'test.txt'
REPO_NAME: 'holy-generic-local'
ARTIFACT_PATH: 'test/test.txt'
ARTIFACTORY_URL: 'https://trial79nt49.jfrog.io'
"""
Map configMap = readYaml text: "${configYaml}"
println configMap


pipeline {
    agent {
        kubernetes {
            yaml '''
                apiVersion: v1
                kind: Pod
                spec:
                  containers:
                    - name: shell
                      image: curlimages/curl:latest
                      runAsUser: 1000
                      command:
                        - cat
                      tty: true
                      workingDir: "/home/jenkins/agent"
                      securityContext:
                        runAsUser: 1000
                '''
            defaultContainer 'shell'
            retries 2
        }
    }
    stages {
        stage('Main') {
            steps {
                container ("shell"){
                    sh """
                    echo "Hello World" > test.txt
                    """
                    //Upload an artifact
                    jfrogUploadArtifact configMap

                    //Download an artifact
                    jfrogDownloadArtifact configMap

                }
            }
        }
    }
}
