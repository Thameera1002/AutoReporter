// AutoReporter – Jenkins pipeline
// Runs the Selenium/TestNG suite headless and publishes JUnit + Allure reports (with history).
// Setup and job configuration: reporter/docs/JENKINS_PLAN.md

pipeline {
    agent any

    tools {
        // Names must match Manage Jenkins → Tools
        jdk   'jdk17'
        maven 'maven3'
    }

    parameters {
        choice(name: 'SUITE',
               choices: ['testng.xml', 'testng-demo.xml'],
               description: 'testng.xml = real tests, testng-demo.xml = intentional failed/broken/skipped/flaky demo tests')
        choice(name: 'BROWSER', choices: ['chrome', 'firefox'], description: 'Browser to run on')
        string(name: 'RETRY_COUNT', defaultValue: '2', description: 'How many times a failed test is retried (0 = no retry)')
    }

    // Triggers declared here replace the ones ticked in the job's Configure page on every build.
    triggers {
        githubPush()        // run on every push (GitHub webhook → <JENKINS_URL>/github-webhook/)
        cron('H 2 * * *')   // nightly run; remove if not needed
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                dir('reporter') {
                    sh """
                        mvn -B clean test \
                            -Dsuite=${params.SUITE} \
                            -Dheadless=true \
                            -Dbrowser=${params.BROWSER} \
                            -Dretry.count=${params.RETRY_COUNT} \
                            -Dmaven.test.failure.ignore=true
                    """
                }
            }
        }
    }

    post {
        always {
            // Jenkins's own test results view (failed tests mark the build UNSTABLE)
            junit allowEmptyResults: true, testResults: 'reporter/target/surefire-reports/*.xml'

            // Allure report + history trend (Allure Jenkins plugin); report goes to ./allure-report
            allure includeProperties: false, results: [[path: 'reporter/allure-results']]

            // Downloadable copy of the report (Pipeline Utility Steps plugin)
            zip zipFile: 'allure-report.zip', dir: 'allure-report', archive: true, overwrite: true
        }
    }
}
