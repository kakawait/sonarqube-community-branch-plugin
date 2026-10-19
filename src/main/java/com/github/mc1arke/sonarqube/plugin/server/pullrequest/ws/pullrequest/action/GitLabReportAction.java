package com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest.action;

import static com.github.mc1arke.sonarqube.plugin.server.pullrequest.ws.pullrequest.action.ListAction.checkPermission;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.issue.IssueStatus;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.issue.IssueDao;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.rule.RuleDao;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.user.UserSession;
import org.springframework.beans.factory.annotation.Autowired;

public class GitLabReportAction extends ProjectWsAction {

    private static final String PULL_REQUEST_PARAMETER = "pullRequest";
    private static final String PATH_PREFIX_PARAMETER = "pathPrefix";

    private static final Map<String, String> ruleKeyToCheckName = new ConcurrentHashMap<>();

    private final UserSession userSession;

    @Autowired
    public GitLabReportAction(DbClient dbClient, ComponentFinder componentFinder, UserSession userSession) {
        super("gitlab_report", dbClient, componentFinder);
        this.userSession = userSession;
    }

    @Override
    protected void configureAction(WebService.NewAction action) {
        action.createParam(PULL_REQUEST_PARAMETER).setRequired(true);
        action.createParam(PATH_PREFIX_PARAMETER)
                .setDescription("Prefix added to every path, e.g. the monorepo sub-project directory relative to the repository root");
    }

    @Override
    protected void handleProjectRequest(ProjectDto project, Request request, Response response, DbSession dbSession) {
        checkPermission(project, userSession);

        String pullRequestId = request.mandatoryParam(PULL_REQUEST_PARAMETER);
        String pathPrefix = normalisePathPrefix(request.param(PATH_PREFIX_PARAMETER));

        BranchDto pullRequest = getDbClient()
                .branchDao()
                .selectByPullRequestKey(dbSession, project.getUuid(), pullRequestId)
                .filter(branch -> branch.getBranchType() == BranchType.PULL_REQUEST)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Pull request '%s' is not found for project '%s'", pullRequestId,
                                project.getKey())));

        IssueDao issueDao = getDbClient().issueDao();
        RuleDao ruleDao = getDbClient().ruleDao();
        // selectOpenByComponentUuids seems to not work as expected
        Set<String> issueKeys = issueDao.selectIssueKeysByComponentUuid(dbSession, pullRequest.getUuid());

        JsonWriter writer = response.newJsonWriter();
        writer.beginArray();
        issueDao
                .selectByKeys(dbSession, issueKeys)
                .stream()
                .filter(i -> i.getIssueStatus() == IssueStatus.OPEN)
                .forEach(i -> buildGitLabIssue(dbSession, i, ruleDao, writer, pathPrefix));
        writer.endArray();
        writer.close();
    }

    private static String normalisePathPrefix(@Nullable String pathPrefix) {
        String prefix = StringUtils.strip(pathPrefix, "/");
        return StringUtils.isEmpty(prefix) ? "" : prefix + "/";
    }

    private void buildGitLabIssue(DbSession dbSession, IssueDto issue, RuleDao ruleDao, JsonWriter writer, String pathPrefix) {
        writer.beginObject();
        // https://docs.gitlab.com/ee/ci/testing/code_quality.html#implement-a-custom-tool
        writer.prop("check_name", getCheckName(dbSession, ruleDao, issue.getRuleKey()));
        writer.prop("fingerprint", DigestUtils.md5Hex(issue.getKey()));
        writer.prop("description", issue.getMessage());
        writer.prop("severity", toGitlabSeverity(issue));
        buildLocation(issue, writer, pathPrefix);
        writer.endObject();
    }

    private static String toGitlabSeverity(IssueDto issue) {
        Map<SoftwareQuality, Severity> impacts = issue.getEffectiveImpacts();
        // a CRITICAL issue whose only HIGH-or-worse impact is maintainability is reported as major
        boolean maintainabilityHighOnly = impacts.get(SoftwareQuality.MAINTAINABILITY) == Severity.HIGH
                && impacts.entrySet().stream().noneMatch(e -> e.getKey() != SoftwareQuality.MAINTAINABILITY
                && (e.getValue() == Severity.HIGH || e.getValue() == Severity.BLOCKER));
        String severity = issue.getSeverity();
        return "CRITICAL".equals(severity) && maintainabilityHighOnly ? "major" : severity.toLowerCase();
    }

    private void buildLocation(IssueDto issue, JsonWriter jsonWriter, String pathPrefix) {
        jsonWriter.name("location").beginObject();
        jsonWriter.prop("path", pathPrefix + issue.getFilePath());
        jsonWriter.name("lines").beginObject();
        jsonWriter.prop("begin", issue.getLine());
        jsonWriter.prop("end", issue.getLine());
        jsonWriter.endObject();
        jsonWriter.endObject();
    }

    private String getCheckName(DbSession dbSession, RuleDao ruleDao, RuleKey ruleKey) {
        return ruleKeyToCheckName.computeIfAbsent(ruleKey.rule(), k -> ruleDao
                .selectByKey(dbSession, ruleKey)
                .map(r -> r.getName().toLowerCase().replaceAll("[^a-zA-Z ]", "").replaceAll("\\s+", "-"))
                .orElse(ruleKey.toString()));
    }

}
