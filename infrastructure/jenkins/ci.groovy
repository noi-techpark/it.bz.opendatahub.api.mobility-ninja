pipeline {
    agent {
        dockerfile {
            filename 'infrastructure/docker/java.dockerfile'
            additionalBuildArgs '--build-arg JENKINS_USER_ID=$(id -u jenkins) --build-arg JENKINS_GROUP_ID=$(id -g jenkins)'
        }
    }

    stages {
        stage('Test - Ninja') {
            steps {
                sh 'mvn -B -U clean test'
            }
        }
    }
}
