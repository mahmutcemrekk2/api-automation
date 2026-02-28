package com.apiautomation.hooks;

import com.apiautomation.utils.CoverageAnalyzer;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * JUnit Platform Launcher listener that runs CoverageAnalyzer
 * after the test suite completes.
 *
 * <p>
 * Registered via
 * META-INF/services/org.junit.platform.launcher.LauncherSessionListener
 */
public class CoverageReportListener implements LauncherSessionListener {

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        CoverageAnalyzer.analyze();
    }
}
