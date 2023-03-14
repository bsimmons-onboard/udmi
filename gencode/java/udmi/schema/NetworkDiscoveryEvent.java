
package udmi.schema;

import java.util.HashMap;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Network Discovery Event
 * <p>
 * Discovery information for an individual network.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "networks"
})
@Generated("jsonschema2pojo")
public class NetworkDiscoveryEvent {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("networks")
    public HashMap<String, DiscoveredNetwork> networks;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.networks == null)? 0 :this.networks.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof NetworkDiscoveryEvent) == false) {
            return false;
        }
        NetworkDiscoveryEvent rhs = ((NetworkDiscoveryEvent) other);
        return ((this.networks == rhs.networks)||((this.networks!= null)&&this.networks.equals(rhs.networks)));
    }

}