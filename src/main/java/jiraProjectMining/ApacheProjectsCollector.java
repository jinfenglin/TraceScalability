package jiraProjectMining;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.domain.*;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Collect issues and project metrics from Apache Software Foundation.
 */
public class ApacheProjectsCollector {
    private static String accountConfigPath = "account.config";
    private Map<String, String> config;
    private JiraRestClient client;
    private String ASF_JIRA_URL = "https://issues.apache.org/jira";
    private static String CSV_SPLITOR = "|";

    public ApacheProjectsCollector() throws IOException, URISyntaxException {
        config = getConfig();
        JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
        URI uri = new URI(ASF_JIRA_URL);
        client = factory.createWithBasicHttpAuthentication(uri, config.get("username"), config.get("passwd"));
    }

    private Map<String, String> getConfig() throws IOException, URISyntaxException {
        Map<String, String> config = new HashMap<>();
        URI configUrl = this.getClass().getClassLoader().getResource(accountConfigPath).toURI();
        List<String> lines = Files.readAllLines(Paths.get(configUrl));
        for (String line : lines) {
            String[] attribs = line.split(":");
            config.put(attribs[0], attribs[1]);
        }
        return config;
    }

//    public void getProjects() throws ExecutionException, InterruptedException {
//        List<Project> projects = new ArrayList<>();
//        for (BasicProject project : client.getProjectClient().getAllProjects().claim()) {
//            SearchResult res = client.getSearchClient().searchJql("project=" + project.getName()).claim();
//            for (Issue issue : res.getIssues()) {
//                System.out.println(issue.getKey());
//                Issue issue1 = client.getIssueClient().getIssue(issue.getKey(), Arrays.asList(IssueRestClient.Expandos.CHANGELOG)).claim();
//                Iterable<ChangelogGroup> changelogGroups = issue.getChangelog();
//                if (changelogGroups == null)
//                    continue;
//                for (ChangelogGroup group : issue.getChangelog()) {
//                    for (ChangelogItem item : group.getItems()) {
//                        System.out.println(item.getFrom() + "|" + item.getTo());
//                        System.out.println(item.toString());
//                    }
//                }
//            }
//            System.out.println("Total issue:" + i);
//            break;
//        }
//    }


    public List<Issue> getIssuesWithChangeLog(String projectName) throws Exception {
        SearchResult res = client.getSearchClient().searchJql("project=" + projectName).claim();
        List<Issue> issues = new ArrayList<>();
        for (Issue issue : res.getIssues()) {
            Issue issueWithChangeLog = client.getIssueClient().getIssue(issue.getKey(), Arrays.asList(IssueRestClient.Expandos.CHANGELOG)).claim();
            issues.add(issueWithChangeLog);
        }
        return issues;
    }

    private String cleanCSVContent(String content) {
        content = content.replace("\n", " ");
        content = content.replace(CSV_SPLITOR, "");
        return content;
    }

    private String issueToString(Issue issue) {
        StringJoiner sj = new StringJoiner("|");
        sj.add(issue.getKey());
        if (issue.getSummary() != null) {
            String summary = issue.getSummary();
            summary = cleanCSVContent(summary);
            sj.add(summary);
        }
        if (issue.getDescription() != null) {
            String description = issue.getDescription();
            description = cleanCSVContent(description);
            sj.add(description);
        }
        sj.add(issue.getIssueType().getName());
        if (issue.getAssignee() != null)
            sj.add(issue.getAssignee().getName());
        if (issue.getReporter() != null)
            sj.add(issue.getReporter().getName());
        return sj.toString();
    }

    public void createDirectory(String projectName) throws URISyntaxException, IOException {
        Path projectDirPath = Paths.get("data", projectName);
        if (Files.notExists(projectDirPath)) {
            Files.createDirectories(projectDirPath);
        }
    }

    public void writeIssues(String projectName, Iterable<Issue> issues) throws URISyntaxException, IOException {
        Path contentPath = Paths.get("data", projectName, "content.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(contentPath)) {
            for (Issue issue : issues) {
                writer.write(issueToString(issue) + "\n");
            }
        }
    }

    public void writeSummary(String projectName, Iterable<Issue> issues) throws IOException {
        Path summaryPath = Paths.get("data", projectName, "summary.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(summaryPath)) {
            ProjectSummary summary = new ProjectSummary(issues);
            writer.write(summary.toString());
        }
    }

    public void writeChangeLogs(String projectName, Iterable<Issue> issues) throws IOException {
        Path changeLogPath = Paths.get("data", projectName, "changelog.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(changeLogPath)) {
            for (Issue issue : issues) {
                for (ChangelogGroup group : issue.getChangelog()) {
                    for (ChangelogItem item : group.getItems()) {
                        StringJoiner sj = new StringJoiner(CSV_SPLITOR);
                        sj.add(group.getCreated().toString());
                        sj.add(issue.getKey());
                        String field = item.getField();
                        if (field == null)
                            field = "null";
                        sj.add(cleanCSVContent(field));
                        String from = item.getFromString();
                        if (from == null)
                            from = "null";
                        sj.add(cleanCSVContent(from));
                        String to = item.getToString();
                        if (to == null)
                            to = "null";
                        sj.add(cleanCSVContent(to));
                        writer.write(sj.toString() + "\n");
                    }
                }
            }
        }
    }

    public void collect() throws Exception {
        Iterable<BasicProject> projects = client.getProjectClient().getAllProjects().claim();
        for (BasicProject project : projects) {
            String projectName = project.getName().trim();
            createDirectory(projectName);
            List<Issue> issues = getIssuesWithChangeLog(projectName);
            writeIssues(projectName, issues);
            writeSummary(projectName, issues);
            writeChangeLogs(projectName, issues);
            break;
        }
    }

    public static void main(String[] args) throws Exception {
        ApacheProjectsCollector apc = new ApacheProjectsCollector();
        apc.collect();
    }
}
