package ch.cern.its;

import org.codehaus.jackson.annotate.JsonProperty;
import java.sql.Date;

public class ProjectData {
    private Long Time = 0L;
    private Integer nbIssues = 0;

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
}