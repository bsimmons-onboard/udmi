
package udmi.schema;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Sequence Validation State
 * <p>
 * Sequence Validation State
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "result",
    "status"
})
@Generated("jsonschema2pojo")
public class SequenceValidationState {

    /**
     * Sequence Result
     * <p>
     * 
     * 
     */
    @JsonProperty("result")
    public SequenceValidationState.SequenceResult result;
    /**
     * Entry
     * <p>
     * 
     * 
     */
    @JsonProperty("status")
    public Entry status;

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.result == null)? 0 :this.result.hashCode()));
        result = ((result* 31)+((this.status == null)? 0 :this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SequenceValidationState) == false) {
            return false;
        }
        SequenceValidationState rhs = ((SequenceValidationState) other);
        return (((this.result == rhs.result)||((this.result!= null)&&this.result.equals(rhs.result)))&&((this.status == rhs.status)||((this.status!= null)&&this.status.equals(rhs.status))));
    }


    /**
     * Sequence Result
     * <p>
     * 
     * 
     */
    @Generated("jsonschema2pojo")
    public enum SequenceResult {

        START("start"),
        SKIP("skip"),
        PASS("pass"),
        FAIL("fail");
        private final String value;
        private final static Map<String, SequenceValidationState.SequenceResult> CONSTANTS = new HashMap<String, SequenceValidationState.SequenceResult>();

        static {
            for (SequenceValidationState.SequenceResult c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        SequenceResult(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static SequenceValidationState.SequenceResult fromValue(String value) {
            SequenceValidationState.SequenceResult constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
