pipeline {
  agent any
  options {
    disableConcurrentBuilds()
    timeout(time: 15, unit: 'MINUTES')
  }
  environment {
    ANTHROPIC_API_KEY = ''
    DEBUG = 'false'
    MAVEN_OPTS = '-Xmx384m'
  }
  stages {
    stage('Java, PostgreSQL, API and Chrome') {
      steps { sh './scripts/verify.sh' }
    }
  }
  post {
    always {
      junit testResults: 'target/surefire-reports/TEST-*.xml', allowEmptyResults: false
      archiveArtifacts artifacts: 'target/surefire-reports/**,target/site/jacoco/**,target/cucumber/**,target/browser/**', allowEmptyArchive: true
    }
  }
}
