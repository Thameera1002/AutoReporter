package com.ucsc.automation.listeners;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import com.ucsc.automation.utils.PropertyHandler;

/**
 * Re-runs a failed or broken test up to "retry.count" times (config.properties).
 * TestNG marks each discarded attempt as retried; Allure groups those attempts
 * under the test's Retries tab. (Only tests annotated with @Flaky get Allure's flaky mark.)
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final int MAX_RETRY = PropertyHandler.getInt("retry.count", 0);

    private int attempt = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (attempt < MAX_RETRY) {
            attempt++;
            System.out.printf("[RETRY] %s failed (%s). Retry %d of %d%n",
                    result.getName(),
                    result.getThrowable() == null ? "no error" : result.getThrowable().getClass().getSimpleName(),
                    attempt, MAX_RETRY);
            return true;
        }
        return false;
    }

    /** Number of retries done so far for this test (0 on the first attempt). */
    public int getAttempt() {
        return attempt;
    }

    public static int getMaxRetry() {
        return MAX_RETRY;
    }
}
