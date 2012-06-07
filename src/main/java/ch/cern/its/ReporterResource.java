package ch.cern.its;

import static com.atlassian.jira.rest.v1.util.CacheControl.NO_CACHE;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jfree.util.Log;
import org.ofbiz.core.entity.GenericEntityException;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.util.velocity.VelocityRequestContextFactory;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;

/**
 * REST endpoint for egroups gadget
 * 
 * @since v4.0
 */
@Path("/reporter-manager")
@AnonymousAllowed
@Produces({ MediaType.APPLICATION_JSON })
public class ReporterResource {
	// private final static Logger log =
	// Logger.getLogger(ReporterResource.class);

	private final JiraAuthenticationContext authenticationContext;
	private final ProjectManager projectManager;
	private final IssueManager issueManager;
	private SortedSet<Long> projectTimes;
	private SortedSet<Long> issueTimes;
	private int nbIssues = 0;
	private int nbProjects = 0;
	private final Calendar oldDate = Calendar.getInstance();

	public ReporterResource(final SearchService searchService,
			final JiraAuthenticationContext authenticationContext,
			final VelocityRequestContextFactory velocityRequestContextFactory,
			final VersionManager versionManager) {
		this.authenticationContext = authenticationContext;
		projectManager = ComponentManager.getInstance().getProjectManager();
		issueManager = ComponentManager.getInstance().getIssueManager();
		oldDate.set(1970, 2, 1);
	}

	@GET
	@Path("/preCompute")
	public Response preCompute() {
		if (internalIsAuthorized()) {
			Collection<Project> allProjects = projectManager
					.getProjectObjects();
			projectTimes = new TreeSet<Long>();
			issueTimes = new TreeSet<Long>();
			try {
				nbProjects = allProjects.size();
				for (Project p : allProjects) {
					// toReturn.add(p.getName());
					Collection<Long> issuesId = issueManager
							.getIssueIdsForProject(p.getId());
					for (Long id : issuesId) {
						MutableIssue issue = issueManager.getIssueObject(id);
						if (issue.getCreated().after(oldDate.getTime())) {
							issueTimes.add(issue.getCreated().getTime());
						} else {
							issueTimes.add(issue.getUpdated().getTime());
						}

					}
					if (!issuesId.isEmpty())
						projectTimes.add(estimateCreationDate(p.getId()));
					else
						projectTimes.add(Calendar.getInstance().getTime()
								.getTime());
				}
				nbIssues = issueTimes.size();
			} catch (Exception e) {
				Log.error(e.getMessage());
			}
		}
		return Response.noContent().build();
	}

	@GET
	@Path("/getNumberOfProjects")
	public Response getNumberOfProjects() {
		if (!internalIsAuthorized()) {
			return Response.noContent().build();
		}
		return Response.ok(nbProjects).cacheControl(NO_CACHE).build();
	}

	@GET
	@Path("/getNumberOfIssues")
	public Response getNumberOfIssues() {
		if (!internalIsAuthorized()) {
			return Response.noContent().build();
		}
		return Response.ok(nbIssues).cacheControl(NO_CACHE).build();
	}

	@GET
	@Path("/getProjectTimeList")
	public Response getProjectTimeList() {
		if (!internalIsAuthorized()) {
			return Response.noContent().build();
		}
		return Response.ok(projectTimes).cacheControl(NO_CACHE).build();
	}

	@GET
	@Path("/getIssuesTimeList")
	public Response getIssuesTimeList() {
		if (!internalIsAuthorized()) {
			return Response.noContent().build();
		}
		return Response.ok(issueTimes).cacheControl(NO_CACHE).build();
	}

	private Long estimateCreationDate(long projectId)
			throws GenericEntityException {
		Collection<Long> issuesId = issueManager
				.getIssueIdsForProject(projectId);
		Timestamp oldest = new Timestamp(Long.MAX_VALUE);
		for (Long id : issuesId) {
			Timestamp t = issueManager.getIssueObject(id).getCreated();
			if (t.before(oldest))
				oldest = t;
		}
		if (oldest.before(oldDate.getTime())) {
			oldest = new Timestamp(Long.MAX_VALUE);
			for (Long id : issuesId) {
				Timestamp t = issueManager.getIssueObject(id).getUpdated();
				if (t.before(oldest))
					oldest = t;
			}
		}
		return oldest.getTime();
	}

	@GET
	@Path("/isAuthorized")
	public Response isAuthorized() {
		return Response.ok(String.valueOf(internalIsAuthorized()))
				.cacheControl(NO_CACHE).build();
	}

	private boolean internalIsAuthorized() {
		User user = authenticationContext.getLoggedInUser();
		UserUtil userUtil = ComponentManager.getInstance().getUserUtil();
		return userUtil.getJiraAdministrators().contains(user)
				|| userUtil.getJiraSystemAdministrators().contains(user);
	}

	// @GET
	// @Path("/getJiraGroups")
	// public Response getJiraGroups() {
	// if (!internalIsAuthorized()) {
	// return Response.noContent().build();
	// }
	// Collection<String> toReturn = new ArrayList<String>();
	// toReturn.add(CREATE_JGROUP_LABEL);
	//
	// DefaultGroupManager groupManager = new DefaultGroupManager(crowdService);
	// for (Group g : groupManager.getAllGroups()) {
	// toReturn.add(g.getName());
	// }
	//
	// return Response.ok(toReturn).cacheControl(NO_CACHE).build();
	// }

}
