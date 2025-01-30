package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.filter;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.PostAnalysisIssueVisitor;
import org.sonar.api.rule.Severity;

import java.util.Comparator;

import static org.sonar.api.issue.impact.Severity.HIGH;

public class SeverityComparator implements Comparator<PostAnalysisIssueVisitor.ComponentIssue> {
    @Override
    public int compare(PostAnalysisIssueVisitor.ComponentIssue o1, PostAnalysisIssueVisitor.ComponentIssue o2) {
        String s2 = getSeverity(o2);
        String s1 = getSeverity(o1);
        return Severity.ALL.indexOf(s2) - Severity.ALL.indexOf(s1);
    }

    static String getSeverity(PostAnalysisIssueVisitor.ComponentIssue componentIssue) {
        return componentIssue.getIssue()
                .impacts()
                .values()
                .stream()
                .max(Comparator.comparing(Enum::ordinal))
                .orElse(HIGH).toString();
    }
}
