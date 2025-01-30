package com.github.mc1arke.sonarqube.plugin.ce.pullrequest.filter;

import com.github.mc1arke.sonarqube.plugin.ce.pullrequest.PostAnalysisIssueVisitor;

import java.util.Comparator;

public class TypeComparator implements Comparator<PostAnalysisIssueVisitor.ComponentIssue> {
    @Override
    public int compare(PostAnalysisIssueVisitor.ComponentIssue o1, PostAnalysisIssueVisitor.ComponentIssue o2) {
        return o2.getIssue().getType().getDbConstant() - o1.getIssue().getType().getDbConstant();
    }
}
