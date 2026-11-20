#!/usr/bin/env bash
# Runs the suite, builds the Allure report with execution history, and opens it.
# Usage: ./run-tests.sh [extra maven args]   e.g. ./run-tests.sh -Dheadless=true -Dbrowser=firefox
cd "$(dirname "$0")" || exit 1

mvn clean test "$@"
status=$?

# allure-report/history is carried into the next run by AllureHistoryListener
allure generate --clean allure-results -o allure-report
allure open allure-report

exit $status
