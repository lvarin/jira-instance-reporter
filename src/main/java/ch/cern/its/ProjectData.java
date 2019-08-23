package ch.cern.its;

import org.codehaus.jackson.annotate.JsonProperty;
import java.sql.Date;

/**
 * Class to store a project data.
 */
public class ProjectData {
    /**
     * The creation time of the project.
     */
    private Long time = 0L;
    /**
     * The number of issues the project has.
     */
    private Integer nbIssues = 0;
    /**
     * The project lead.
     */
    private String lead = "";

    /**
     * Sets the time value.
     *
     * @param t The value to set as time
     */
    public void setTime(final Long t) {
        this.time = t;
    }

    /**
     * Returns the creation date in Date format.
     *
     * @return the creation date in Date format
     */
    @JsonProperty
    public Date getCreationDate() {
        return new Date(this.time);
    }

    /**
     * Returns the value of the current time.
     *
     * @return The value of the current time
     */
    public Long getTime() {
        return this.time;
    }

    /**
     * Increased by one the number of issues.
     *
     * @return The increased number of issues
     */
    public Integer increateNumberIssues() {
        return ++this.nbIssues;
    }

    /**
     * Sets the number of issues.
     *
     * @param nbIssuesP The value to set as number of issues
     */
    public void setNbIssues(final Integer nbIssuesP) {
        this.nbIssues = nbIssuesP;
    }

    /**
     * Returns the number of issues.
     *
     * @return the number of issues
     */
    @JsonProperty
    public Integer getNumberOfIssues() {
        return this.nbIssues;
    }

    /**
     * Returns the lead parameter.
     *
     * @return The string reprepsenting the lead
     */
    @JsonProperty
    public String getLead() {
        return this.lead;
    }

    /**
     * Sets the value for the lead parameter.
     *
     * @param l String of the Lead
     */
    public void setLead(final String l) {
        this.lead = l;
    }
}
