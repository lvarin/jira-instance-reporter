package ch.cern.its;

import static com.atlassian.jira.rest.v1.util.CacheControl.NO_CACHE;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;

import com.atlassian.config.bootstrap.AtlassianBootstrapManager;
import com.atlassian.config.bootstrap.DefaultAtlassianBootstrapManager;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.config.database.DatabaseConfigurationManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;

/**
 * REST endpoint for Reporter Gadget Provides some method to access metrics
 * about the instance (ex : number of projects, issues over time, ...).
 * 
 * @author CERN ITS team
 * @since v4.0
 */
@Path("/metric-manager")
@AnonymousAllowed
@Produces({ MediaType.APPLICATION_JSON })
public class MetricResource {
	private final static Logger log = Logger.getLogger(MetricResource.class);
	private List<Long> projectTimes;
	private List<Long> issueTimes;
	private List<Long> userTimes;
	private int nbIssues = 0;
	private int nbProjects = 0;
	private int nbUsers = 0;
	private final long tOld;
	private final JiraAuthenticationContext authenticationContext;
	private final DatabaseConfigurationManager dbConfigManager;
	private final AtlassianBootstrapManager bootstrapManager;

	public MetricResource(final SearchService searchService,
			final JiraAuthenticationContext authenticationContext) {
		this.authenticationContext = authenticationContext;
		this.dbConfigManager = ComponentManager
				.getComponent(DatabaseConfigurationManager.class);
		// TODO probably not the right way to do that.
		this.bootstrapManager = new DefaultAtlassianBootstrapManager();

		Calendar oldDate = Calendar.getInstance();
		oldDate.set(1970, 2, 1);
		tOld = oldDate.getTime().getTime();

	}

	@GET
	@Path("/build")
	public Response build() {
		Long t = null;
		String pKey = null;
		Long tProj = null;
		HashMap<String, Long> projects = new HashMap<String, Long>();
		issueTimes = new ArrayList<Long>();
		userTimes = new ArrayList<Long>();

		try {
			Connection conn = dbConfigManager.getDatabaseConfiguration()
					.getDatasource().getConnection(bootstrapManager);

			// looking for issues and projects infos
			String sql = "select created, updated, project  from jiraissue";
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				t = rs.getDate(1).getTime();
				if (t < tOld) {
					t = rs.getDate(2).getTime();
				}
				issueTimes.add(t);
				pKey = rs.getString(3);
				// t is the date of creation of the issue
				if ((tProj = projects.get(pKey)) == null) {
					projects.put(pKey, t);
				} else {
					if (t < tProj) {
						projects.put(pKey, t);
					}
				}
			}

			// looking for users info
			sql = "select created_date, updated_date  from cwd_user";
			stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				t = rs.getDate(1).getTime();
				if (t < tOld) {
					t = rs.getDate(2).getTime();
				}
				userTimes.add(t);
			}

			conn.close();

			projectTimes = new ArrayList<Long>(projects.values());
			Collections.sort(projectTimes);
			Collections.sort(issueTimes);
			Collections.sort(userTimes);

			nbIssues = issueTimes.size();
			nbProjects = projectTimes.size();
			nbUsers = userTimes.size();
		} catch (Exception e) {
			log.error("SQL Error : " + e.getMessage());
		}
		return null;
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
	@Path("/getNumberOfUsers")
	public Response getNumberOfUsers() {
		if (!internalIsAuthorized()) {
			return Response.noContent().build();
		}
		return Response.ok(nbUsers).cacheControl(NO_CACHE).build();
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
	@Path("/getUsersTimeList")
	public Response getUsersTimeList() {
		if (!internalIsAuthorized()) {
			return Response.noContent().build();
		}
		return Response.ok(userTimes).cacheControl(NO_CACHE).build();
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

	private boolean internalIsAuthorized() {
		User user = authenticationContext.getLoggedInUser();
		UserUtil userUtil = ComponentManager.getInstance().getUserUtil();
		return userUtil.getJiraAdministrators().contains(user)
				|| userUtil.getJiraSystemAdministrators().contains(user);
	}

}
