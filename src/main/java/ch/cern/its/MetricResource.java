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
import java.util.Iterator;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;

import com.atlassian.jira.component.ComponentAccessor;

import com.atlassian.config.bootstrap.AtlassianBootstrapManager;
import com.atlassian.config.bootstrap.DefaultAtlassianBootstrapManager;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.config.database.DatabaseConfigurationManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.user.ApplicationUser;
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

    private List<Long> projectsDates;
    private List<Long> issuesDates;
    private List<Long> usersDates;

    private int nbIssues = 0;
    private int nbProjects = 0;
    private int nbUsers = 0;
    private int nbActiveUsers = 0;

    private final long tOld;// timestamp of 01/02/1970

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

        // we are using "tOld" as a trick for imported entities
        // who have no "created" date set (then we use "updated").
        Calendar oldDate = Calendar.getInstance();
        oldDate.set(1970, 2, 1);
        tOld = oldDate.getTime().getTime();
    }

	/**
	 * Shows the available endpoints
	 *
	 */
	@GET
	@Path("/")
	public Response myCommands() {

		String baseURL = ComponentAccessor.getApplicationProperties().getString("jira.baseurl")+
			"/rest/reporter-rest/1.0/metric-manager";
		String toReturn = "[";

		List<String> message = new ArrayList<String>();

		message.add("/build");
		message.add("/getNumberOfProjects");
		message.add("/getNumberOfUsers");
		message.add("/getNumberOfActiveUsers");
		message.add("/getNumberOfIssues");
		message.add("/getUsersDates");
		message.add("/getProjectsDates");
		message.add("/getIssuesDates");

		Iterator<String> iter = message.listIterator();
        toReturn += "\""+baseURL+iter.next()+"\"";
        for (; iter.hasNext();)
            toReturn += ",\""+baseURL+iter.next()+"\"";
		toReturn += "]";

		return Response.status(200).entity(toReturn).build();
	}

	/**
	 * This method is called to build (or refresh) inner data (all data that are
	 * provided via rest endpoints)
	 *
	 * @return null
	 */
	@GET
	@Path("/build")
	public Response build() {
		Long t = null;
		String pKey = null;
		Long tProj = null;
		HashMap<String, Long> projects = new HashMap<String, Long>();
		issuesDates = new ArrayList<Long>();
		usersDates = new ArrayList<Long>();

		try {
			Connection conn = dbConfigManager.getDatabaseConfiguration()
					.getDatasource().getConnection(bootstrapManager);

			// looking for issues and projects infos
			String sql = "SELECT created, updated, project FROM jiraissue";
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				t = rs.getDate(1).getTime();
				if (t < tOld) {
					t = rs.getDate(2).getTime();
				}
				issuesDates.add(t);
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
			sql = "SELECT created_date, updated_date FROM cwd_user";
			stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				t = rs.getDate(1).getTime();
				if (t < tOld) {
					t = rs.getDate(2).getTime();
				}
				usersDates.add(t);
			}

			// looking for users info
			sql = "SELECT COUNT(*) AS count FROM cwd_user WHERE active='1'";
			stmt = conn.createStatement();
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				nbActiveUsers = rs.getInt("count");
			}

      conn.close();

			projectsDates = new ArrayList<Long>(projects.values());
			Collections.sort(projectsDates);
                        try {
                            Collections.sort(issuesDates);
                        } catch (NullPointerException npe) {
                            log.error("NullPointerException while doing Collections.sort(issuesDates);");
                        }
			Collections.sort(usersDates);

			nbIssues = issuesDates.size();
			nbProjects = projectsDates.size();
			nbUsers = usersDates.size();

		} catch (Exception e) {
		    log.error("Exception: " + e.getMessage());
                    e.printStackTrace();
		    return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		return Response.ok("\"built\"").build();
	}

	/**
	 * If user is not administrator, no content is returned
	 *
	 * @return number of projects with at least one issue
	 */
	@GET
	@Path("/getNumberOfProjects")
	public Response getNumberOfProjects() {
		if (!internalIsAuthorized()) {
			return Forbidden();
		}

		return Response.ok(nbProjects).cacheControl(NO_CACHE).build();
	}

	/**
	 * If user is not administrator, no content is returned
	 *
	 * @return number of users in CWD_USER table
	 */
	@GET
	@Path("/getNumberOfUsers")
	public Response getNumberOfUsers() {
		if (!internalIsAuthorized()) {
			return Forbidden();
		}

		return Response.ok(nbUsers).cacheControl(NO_CACHE).build();
	}

	/**
	 * If user is not administrator, no content is returned
	 *
	 * @return number of active users in CWD_USER table
	 */
	@GET
	@Path("/getNumberOfActiveUsers")
	public Response getNumberOfActiveUsers() {
		if (!internalIsAuthorized()) {
			return Forbidden();
		}

		return Response.ok(nbActiveUsers).cacheControl(NO_CACHE).build();
	}

	/**
	 * If user is not administrator, no content is returned
	 *
	 * @return total number of issues
	 */
	@GET
	@Path("/getNumberOfIssues")
	public Response getNumberOfIssues() {
		if (!internalIsAuthorized()) {
			return Forbidden();
		}
		return Response.ok(nbIssues).cacheControl(NO_CACHE).build();
	}

	/**
	 * If user is not administrator, no content is returned
	 *
	 * @return sorted list of dates corresponding to User creation
	 *         (usersDates.size() --> number of users)
	 */
	@GET
	@Path("/getUsersDates")
	public Response getUsersDates() {
		if (!internalIsAuthorized()) {
			return Forbidden();
		}
		return Response.ok(usersDates).cacheControl(NO_CACHE).build();
	}

	/**
	 * If user is not administrator, no content is returned. Project date
	 * creation is estimated with its oldest issue creation date
	 *
	 * @return sorted list of dates corresponding to Project creation
	 *         (projectsDates.size() --> number of projects)
	 */
	@GET
	@Path("/getProjectsDates")
	public Response getProjectsDates() {
		if (!internalIsAuthorized()) {
			return Forbidden();
		}
		return Response.ok(projectsDates).cacheControl(NO_CACHE).build();
	}

	/**
	 * If user is not administrator, no content is returned
	 *
	 * @return sorted list of dates corresponding to issues creation
	 *         (issuesDates.size() --> number of issues)
	 */
	@GET
	@Path("/getIssuesDates")
	public Response getIssuesDates() {
		if (!internalIsAuthorized()) {
			return Forbidden();
		}
		return Response.ok(issuesDates).cacheControl(NO_CACHE).build();
	}

	/**
	 * @return true if logged in user is administrator, else false
	 */
	private boolean internalIsAuthorized() {
		try{
		 ApplicationUser user = authenticationContext.getLoggedInUser();
		 UserUtil userUtil = ComponentAccessor.getUserUtil();
		 return userUtil.getJiraAdministrators().contains(user)
			|| userUtil.getJiraSystemAdministrators().contains(user);
		} catch(NoSuchMethodError nsm){
			log.error("No Such Method: "+ nsm.getMessage());
			return false;
		}
	}

	private Response Forbidden(){
		return Response.status(Response.Status.FORBIDDEN).entity("{\"msg\":\"FORBIDDEN\"}").build();
	}
}
