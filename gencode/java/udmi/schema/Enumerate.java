
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "families",
    "point_refs",
    "features"
})
@Generated("jsonschema2pojo")
public class Enumerate {

    @JsonProperty("families")
    public Boolean families;
    @JsonProperty("point_refs")
    public Boolean point_refs;
    @JsonProperty("features")
    public Boolean features;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.features == null)? 0 :this.features.hashCode()));
        result = ((result* 31)+((this.families == null)? 0 :this.families.hashCode()));
        result = ((result* 31)+((this.point_refs == null)? 0 :this.point_refs.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Enumerate) == false) {
            return false;
        }
        Enumerate rhs = ((Enumerate) other);
        return ((((this.features == rhs.features)||((this.features!= null)&&this.features.equals(rhs.features)))&&((this.families == rhs.families)||((this.families!= null)&&this.families.equals(rhs.families))))&&((this.point_refs == rhs.point_refs)||((this.point_refs!= null)&&this.point_refs.equals(rhs.point_refs))));
    }

}
