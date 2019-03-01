package ch.cern.its;

import org.codehaus.jackson.annotate.JsonProperty;
import java.sql.Date;

public class ProjectData {
    private Long Time = 0L;

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


}