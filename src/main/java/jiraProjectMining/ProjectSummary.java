package jiraProjectMining;

import com.atlassian.jira.rest.client.api.domain.Attachment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.User;
import org.joda.time.DateTime;

import java.util.*;

/**
 * Some statistics of a project.
 */
public class ProjectSummary {
    private Map<String, Integer> artifactTypeCount;
    private DateTime startTime, lastModification;
    private Set<String> people, attachmentSet;

    ProjectSummary(Iterable<Issue> issues) {
        people = new HashSet<>();
        attachmentSet = new HashSet<>();
        artifactTypeCount = new HashMap<>();

        for (Issue issue : issues) {
            String type = issue.getIssueType().getName();
            addToTypeCount(type);
            processAttachment(issue);
            processPeople(issue);
            startTime = getEarilierTime(startTime, issue.getCreationDate());
            lastModification = getLaterTime(lastModification, issue.getUpdateDate());
        }
    }

    private void processAttachment(Issue issue) {
        for (Attachment attachment : issue.getAttachments()) {
            String fileName = attachment.getFilename();
            String[] nameConmponents = fileName.split(".");
            if (nameConmponents.length == 0)
                continue;
            String postfix = nameConmponents[nameConmponents.length - 1];
            if (!attachmentSet.contains(attachment)) {
                attachmentSet.add(fileName);
                addToTypeCount(postfix);
            }
        }
    }

    private void processPeople(Issue issue) {
        User reporter = issue.getReporter();
        User assignee = issue.getAssignee();
        if (reporter != null) {
            people.add(reporter.getName());
        }
        if (assignee != null) {
            people.add(assignee.getName());
        }
    }

    private void addToTypeCount(String type) {
        if (!artifactTypeCount.containsKey(type)) {
            artifactTypeCount.put(type, 0);
        }
        artifactTypeCount.put(type, artifactTypeCount.get(type) + 1);
    }

    public String toString() {
        StringJoiner sj = new StringJoiner("\n");
        for (String attName : artifactTypeCount.keySet()) {
            sj.add(String.format("%s:%s", attName, artifactTypeCount.get(attName)));
        }
        sj.add(String.format("From %s to %s", startTime, lastModification));
        return sj.toString();
    }

    private DateTime getEarilierTime(DateTime t1, DateTime t2) {
        if (t1 == null)
            return t2;
        if (t2 == null)
            return t1;
        if (t1.isAfter(t2)) {
            return t2;
        } else {
            return t1;
        }
    }

    private DateTime getLaterTime(DateTime t1, DateTime t2) {
        if (t1 == null)
            return t2;
        if (t2 == null)
            return t1;
        if (t1.isAfter(t2)) {
            return t1;
        } else {
            return t2;
        }
    }
}
