package ch.cern.its;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonAutoDetect
public class ProjectData {
    private Long Time = 0L;

    @JsonProperty
    public void setTime(Long t){
        this.Time = t;
    }

    @JsonProperty
    public Long getTime(){
        return this.Time;
    }
}