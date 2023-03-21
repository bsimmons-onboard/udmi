
package udmi.schema;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Building Translation
 * <p>
 * [Discovery result](../docs/specs/discovery.md) with implicit enumeration
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "present_value2",
    "units",
    "ref",
    "states"
})
@Generated("jsonschema2pojo")
public class BuildingTranslation {

    /**
     * dotted path to present_value field
     * 
     */
    @JsonProperty("present_value2")
    @JsonPropertyDescription("dotted path to present_value field")
    public String present_value2;
    @JsonProperty("units")
    public Object units;
    @JsonProperty("ref")
    public String ref;
    @JsonProperty("states")
    public Object states;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.ref == null)? 0 :this.ref.hashCode()));
        result = ((result* 31)+((this.units == null)? 0 :this.units.hashCode()));
        result = ((result* 31)+((this.present_value2 == null)? 0 :this.present_value2 .hashCode()));
        result = ((result* 31)+((this.states == null)? 0 :this.states.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof BuildingTranslation) == false) {
            return false;
        }
        BuildingTranslation rhs = ((BuildingTranslation) other);
        return (((((this.ref == rhs.ref)||((this.ref!= null)&&this.ref.equals(rhs.ref)))&&((this.units == rhs.units)||((this.units!= null)&&this.units.equals(rhs.units))))&&((this.present_value2 == rhs.present_value2)||((this.present_value2 != null)&&this.present_value2 .equals(rhs.present_value2))))&&((this.states == rhs.states)||((this.states!= null)&&this.states.equals(rhs.states))));
    }

}
