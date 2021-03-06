package ch.cern.its;

//import ch.cern.its.ProjectData;

import static com.atlassian.jira.rest.v1.util.CacheControl.NO_CACHE;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
//import java.util.Iterator;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;

import com.atlassian.config.bootstrap.AtlassianBootstrapManager;
import com.atlassian.config.bootstrap.DefaultAtlassianBootstrapManager;
import com.atlassian.config.bootstrap.BootstrapException;
//import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.component.ComponentAccessor;
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
    /** Declaration of the logger, so we can send log messages to JIRA's log. */
    private static final Logger LOG = Logger.getLogger(MetricResource.class);

    /**
     * List of project dates.
     */
    private List<Long> projectsDates;
    /**
     * List of issue dates.
     */
    private List<Long> issuesDates;
    /**
     * List of user dates.
     */
    private List<Long> usersDates;
    /**
     * Dictionary that stores the project data, the key is the project key.
     */
    private HashMap<String, ProjectData> projects;
    //HashMap<String, HashMap<String, String>> projects;

    /**
     * Number of issues.
     */
    private int nbIssues = 0;
    /**
     * Number of Projects.
     */
    private int nbProjects = 0;
    /**
     * Number of Users.
     */
    private int nbUsers = 0;
    /**
     * Number of active users.
     */
    private int nbActiveUsers = 0;

    /**
     * We have ~400 issues with a corrupted create date.
     * All corrupted dates are from the 1st Jan 1970 from 1h20 till 1h22.
     * Maybe there is a better way to fix this, but this date is used to
     * ignore old issues.
     */
    private final long tOld; // timestamp of 01/02/1970
    /**
     * Base URL of the instance.
     */
    private String baseURL;

    /**
     * Used t get the current logged in user.
     */
    private final JiraAuthenticationContext authenticationContextP;
    /**
     * Component used to store the database configuration options.
     */
    private final DatabaseConfigurationManager dbConfigManager;
    /**
     * Component used to get database configuration options.
     */
    private final AtlassianBootstrapManager bootstrapManager;

    /**
     * Year to mark the issues that have corrupted date.
     */
    public static final int YEAR = 1970;

    /**
     * Stores the connection to the DB.
     */
    private Connection conn = null;

    /**
     * Constructor. It is instatiated by JIRA
     *
     * @param searchService Unused parameter
     * @param authenticationContext So we can get the logged in used
     */
    public MetricResource(final SearchService searchService,
        final JiraAuthenticationContext authenticationContext) {
        this.authenticationContextP = authenticationContext;
        this.dbConfigManager = ComponentAccessor.getComponent(
            DatabaseConfigurationManager.class);
        // TODO probably not the right way to do that.
        this.bootstrapManager = new DefaultAtlassianBootstrapManager();

        this.projects = new HashMap<String, ProjectData>();
        //this.projects = new HashMap<String, HashMap<String,String>>();

        // we are using "tOld" as a trick for imported entities
        // who have no "created" date set (then we use "updated").
        Calendar oldDate = Calendar.getInstance();
        oldDate.set(YEAR, 2, 1);
        tOld = oldDate.getTime().getTime();

        baseURL = ComponentAccessor.getApplicationProperties()
            .getString("jira.baseurl")
            + "/rest/reporter-rest/1.0/metric-manager";
    }

    /**
     * Shows the available endpoints.
     *
     * @return The list of availabler commands
     */
    @GET
    @Path("/")
    public Response myCommands() {

        List<String> message = new ArrayList<String>();

        message.add(baseURL + "/build");
        message.add(baseURL + "/getNumberOfProjects");
        message.add(baseURL + "/getNumberOfUsers");
        message.add(baseURL + "/getNumberOfActiveUsers");
        message.add(baseURL + "/getNumberOfIssues");
        message.add(baseURL + "/getUsersDates");
        message.add(baseURL + "/getProjectsDates");
        message.add(baseURL + "/getIssuesDates");
        message.add(baseURL + "/getProjectsData");

        //return Response.status(200).entity(toReturn).build();
        return Response.status(Response.Status.OK).entity(message).build();
    }

    /**
     * This method is called to build (or refresh) inner data (all data that are
     * provided via rest endpoints).
     *
     * @return Forbidden (403) or OK (200)
     */
    @GET
    @Path("/build")
    public Response build() {

        if (!internalIsAuthorized()) {
            return forbidden();
        }

        this.connect();

        Response buildProjects = this.buildProjects();

        ///////////////////////////////////////////////////////////////////

        Response buildUsers = this.buildUsers();

        ///////////////////////////////////////////////////////////////////

        Response buildActiveUsers = this.buildActiveUsers();

        this.disConnect();

        return Response.ok("\"built\"").build();
    }

    /**
     * SQL query to get the number of active users.
     */
    private static final String PROJECTS_ISSUE_SQL
        = ("SELECT created, updated, project.pkey, project.lead FROM jiraissue"
           + " inner join project on jiraissue.project = project.id");

    /**
     * Position of the field 'project.pkey'.
     */
    private static final int PROJECT_KEY_POS = 3;
    /**
     * Position of the field 'project.lead'.
     */
    private static final int PROJECT_LEAD_POS = 4;

    /**
     * This method is called to build (or refresh) project data.
     *
     * @return The response Internal server error (500) if any problem was
     *  found, OK (200) otherwise.
     */
    private Response buildProjects() {

        Long t = null;
        String pKey = null;
        String  lead = null;
        Long tProj = null;
        issuesDates = new ArrayList<Long>();

        // Reset number of issues per project
        for (String pk : projects.keySet()) {
            projects.get(pk).setNbIssues(0);
        }

        // looking for issues and projects infos
        //String sql = "SELECT created, updated, project FROM jiraissue";
        String sql = PROJECTS_ISSUE_SQL;
        try {

            Statement stmt = this.conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                t = rs.getDate(1).getTime();
                if (t < tOld) {
                    t = rs.getDate(2).getTime();
                }
                // Produce an array of created issues
                issuesDates.add(t);

                // Retrieve the Project Key
                pKey = rs.getString(PROJECT_KEY_POS);
                // Retrieve the Project lead
                lead = rs.getString(PROJECT_LEAD_POS);

                // t is the date of creation of the issue

                // Better use a try catch ?
                // tProj is the current stored t for the project
                // we will update it if 't' is < 'tProj'
                if (projects.get(pKey) == null) {
                    projects.put(pKey, new ProjectData());
                    projects.get(pKey).setTime(t);
                    projects.get(pKey).setLead(lead);
                }
                tProj = projects.get(pKey).getTime();

                if (t < tProj) {
                    projects.get(pKey).setTime(t);
                }
                projects.get(pKey).increateNumberIssues();
            }

        } catch (SQLException sqle) {
            LOG.error("SQLException: " + sqle.getMessage());
                    sqle.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        projectsDates = new ArrayList<Long>(projects.size());

        for (ProjectData pd : projects.values()) {
            projectsDates.add(pd.getTime());
        }

        Collections.sort(projectsDates);
        nbProjects = projectsDates.size();

        try {
            Collections.sort(issuesDates);
        } catch (NullPointerException npe) {
            LOG.error("NullPointerException while doing"
                + " Collections.sort(issuesDates);");
        }

        nbIssues = issuesDates.size();

        return Response.ok("\"Projects Built\"").build();
    }

    /**
     * This method is called to build (or refresh) User data.
     *
     * @return OK (200) if success, internal server error (500) otherwise
     */
    private Response buildUsers() {
        // looking for users info
        Long t;
        this.usersDates = new ArrayList<Long>();

        String sql = "SELECT created_date, updated_date FROM cwd_user";

        if (this.conn == null) {
            this.connect();
        }

        try {
            Statement stmt = this.conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                t = rs.getDate(1).getTime();
                if (t < tOld) {
                    t = rs.getDate(2).getTime();
                }
                usersDates.add(t);
            }
        } catch (SQLException sqle) {
            LOG.error("SQLException: " + sqle.getMessage());
            sqle.printStackTrace();

            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        Collections.sort(usersDates);
        nbUsers = usersDates.size();

        return Response.ok("\"Users Built\"").build();
    }

    /**
     * SQL query to get the number of active users.
     */
    private static final String ACTIVE_USERS_SQL
        = "SELECT COUNT(*) AS count FROM cwd_user WHERE active='1'";

    /**
     * This method is called to build (or refresh) active User data.
     *
     * @return OK (200) if success, internal server error (500) otherwise
     */
    private Response buildActiveUsers() {

        try {
            // looking for users info
            Statement stmt = this.conn.createStatement();
            ResultSet rs = stmt.executeQuery(ACTIVE_USERS_SQL);
            while (rs.next()) {
                nbActiveUsers = rs.getInt("count");
            }

        } catch (SQLException sqle) {
            LOG.error("SQLException: " + sqle.getMessage());
            sqle.printStackTrace();

            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok("\"Active Users Built\"").build();
    }

    /**
     * If user is not administrator, Forbidden is returned.
     *
     * @return number of projects with at least one issue
     */
    @GET
    @Path("/getNumberOfProjects")
    public Response getNumberOfProjects() {
        if (!internalIsAuthorized()) {
            return forbidden();
        }

        return Response.ok(nbProjects).cacheControl(NO_CACHE).build();
    }

    /**
     * If user is not administrator, Forbidden is returned.
     *
     * @return A JSON representation of the current list of projects
     *     and some of its data
     */
    @GET
    @Path("/getProjectsData")
    public Response getProjectsData() {
        if (!internalIsAuthorized()) {
            return forbidden();
        }

        return Response.ok(projects).cacheControl(NO_CACHE).build();
    }

    /**
     * If user is not administrator, Forbidden is returned.
     *
     * @return number of users in CWD_USER table
     */
    @GET
    @Path("/getNumberOfUsers")
    public Response getNumberOfUsers() {
        if (!internalIsAuthorized()) {
            return forbidden();
        }

        if (this.nbUsers == 0) {
            this.buildUsers();
        }

        return Response.ok(nbUsers).cacheControl(NO_CACHE).build();
    }

    /**
     * If user is not administrator, Forbidden is returned.
     *
     * @return number of active users in CWD_USER table
     */
    @GET
    @Path("/getNumberOfActiveUsers")
    public Response getNumberOfActiveUsers() {
        if (!internalIsAuthorized()) {
            return forbidden();
        }

        return Response.ok(nbActiveUsers).cacheControl(NO_CACHE).build();
    }

    /**
     * If user is not administrator, Forbidden is returned.
     *
     * @return total number of issues
     */
    @GET
    @Path("/getNumberOfIssues")
    public Response getNumberOfIssues() {
        if (!internalIsAuthorized()) {
            return forbidden();
        }
        return Response.ok(nbIssues).cacheControl(NO_CACHE).build();
    }

    /**
     * If user is not administrator, Forbidden is returned.
     *
     * @return sorted list of dates corresponding to User creation
     *         (usersDates.size() --> number of users)
     */
    @GET
    @Path("/getUsersDates")
    public Response getUsersDates() {
        if (!internalIsAuthorized()) {
            return forbidden();
        }
        return Response.ok(usersDates).cacheControl(NO_CACHE).build();
    }

    /**
     * If user is not administrator, Forbidden is returned. Project date
     * creation is estimated with its oldest issue creation date.
     *
     * @return sorted list of dates corresponding to Project creation
     *         (projectsDates.size() --> number of projects)
     */
    @GET
    @Path("/getProjectsDates")
    public Response getProjectsDates() {
        if (!internalIsAuthorized()) {
            return forbidden();
        }
        return Response.ok(projectsDates).cacheControl(NO_CACHE).build();
    }

    /**
     * If user is not administrator, Forbidden is returned.
     *
     * @return sorted list of dates corresponding to issues creation
     *         (issuesDates.size() --> number of issues)
     */
    @GET
    @Path("/getIssuesDates")
    public Response getIssuesDates() {
        if (!internalIsAuthorized()) {
            return forbidden();
        }
        return Response.ok(issuesDates).cacheControl(NO_CACHE).build();
    }

    /**
     * @return true if logged in user is administrator, else false
     */
    private boolean internalIsAuthorized() {
        try {
         ApplicationUser user = authenticationContextP.getLoggedInUser();
         UserUtil userUtil = ComponentAccessor.getUserUtil();
         return userUtil.getJiraAdministrators().contains(user)
            || userUtil.getJiraSystemAdministrators().contains(user);
        } catch (NoSuchMethodError nsm) {
            LOG.error("No Such Method: " + nsm.getMessage());
            return false;
        }
    }

    /**
     * Called to return a forbidden response.
     *
     * @return Forbidden (403)
     */
    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity("{\"msg\":\"FORBIDDEN\"}").build();
    }


    /**
     * Connects to the DB is there is no conection yet.
     */
    private void connect() {
        if (this.conn == null) {
            try {
                this.conn = dbConfigManager.getDatabaseConfiguration()
                        .getDatasource().getConnection(bootstrapManager);
            } catch (BootstrapException bse) {
                LOG.error("BootstrapException: " + bse.getMessage());
                bse.printStackTrace();
            }
        }
    }

    /**
     * Disconnects from the database.
     */
    private void disConnect() {
        try {
            this.conn.close();
            this.conn = null;
        } catch (SQLException sqle) {
            LOG.error("SQLException: " + sqle.getMessage());
            sqle.printStackTrace();
        }
    }
}
