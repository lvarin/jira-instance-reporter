package ch.cern.its;

import org.codehaus.jackson.annotate.JsonProperty;
import java.sql.Date;

public class ProjectData {
    private Long Time = 0L;
    private Integer nbIssues = 0;
    private String Lead = "";

    public void setTime(Long t){
        this.Time = t;
    }

    @JsonProperty
    public Date getCreationDate() {
        return new Date(this.Time);
    }

    public Long getTime(){
        return this.Time;
    }

    public Integer increateNumberIssues(){
        return ++this.nbIssues;
    }

    public void setNbIssues(Integer nbIssues){
        this.nbIssues = nbIssues;
    }

    @JsonProperty
    public Integer getNumberOfIssues(){
        return this.nbIssues;
    }

    @JsonProperty
    public String getLead(){
        return this.Lead;
    }

    public void setLead(String lead){
        this.Lead = lead;
    }
}
