package ch.cern.its;

import static com.atlassian.jira.rest.v1.util.CacheControl.NO_CACHE;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import oracle.jdbc.driver.OracleDriver;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.bc.issue.search.SearchService;
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
@Path("/metric-manager")
@AnonymousAllowed
@Produces({ MediaType.APPLICATION_JSON })
public class MetricResource {
	private final static Logger log = Logger.getLogger(MetricResource.class);
	private final static String dbConfigPath = "/var/jira-home/dbconfig.xml";
	private List<Long> projectTimes;
	private List<Long> issueTimes;
	private int nbIssues = 0;
	private int nbProjects = 0;
	private String jiraDbURL;
	private String jiraDbUser;
	private String jiraDbPassword;
	private final long tOld;
	private final JiraAuthenticationContext authenticationContext;

	public MetricResource(final SearchService searchService,
			final JiraAuthenticationContext authenticationContext,
			final VelocityRequestContextFactory velocityRequestContextFactory,
			final VersionManager versionManager) {
		this.authenticationContext = authenticationContext;
		Calendar oldDate = Calendar.getInstance();
		oldDate.set(1970, 2, 1);
		tOld = oldDate.getTime().getTime();
		// dbconfig infos

		try {
			File file = new File(dbConfigPath);
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			Element root = doc.getDocumentElement();
			root.normalize();
			jiraDbURL = root.getElementsByTagName("url").item(0)
					.getTextContent();
			jiraDbUser = root.getElementsByTagName("username").item(0)
					.getTextContent();
			jiraDbPassword = root.getElementsByTagName("password").item(0)
					.getTextContent();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// driver for oracle db
		try {
			DriverManager.registerDriver(new OracleDriver());
		} catch (Exception e) {
			log.error(e.getMessage());
		}
	}

	@GET
	@Path("/build")
	public Response build() {
		Long tIssue = null;
		String pKey = null;
		Long tProj = null;
		HashMap<String, Long> projects = new HashMap<String, Long>();
		issueTimes = new ArrayList<Long>();
		try {
			Connection conn = DriverManager.getConnection(jiraDbURL,
					jiraDbUser, jiraDbPassword);
			String sql = "select created, updated, project  from jiraissue";
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				tIssue = rs.getDate(1).getTime();
				// System.out.println(i++);
				if (tIssue < tOld) {
					tIssue = rs.getDate(2).getTime();
				}
				issueTimes.add(tIssue);
				// System.out.println(issueTimes.size());
				pKey = rs.getString(3);
				// tIssue is the date of creation of the issue
				if ((tProj = projects.get(pKey)) == null) {
					projects.put(pKey, tIssue);
				} else {
					if (tIssue < tProj) {
						projects.put(pKey, tIssue);
					}
				}
			}
			conn.close();

			projectTimes = new ArrayList<Long>(projects.values());
			Collections.sort(projectTimes);
			Collections.sort(issueTimes);

			nbIssues = issueTimes.size();
			nbProjects = projectTimes.size();
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

	private boolean internalIsAuthorized() {
		User user = authenticationContext.getLoggedInUser();
		UserUtil userUtil = ComponentManager.getInstance().getUserUtil();
		return userUtil.getJiraAdministrators().contains(user)
				|| userUtil.getJiraSystemAdministrators().contains(user);
	}

}
