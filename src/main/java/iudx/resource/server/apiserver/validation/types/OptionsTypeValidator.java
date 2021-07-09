package iudx.resource.server.apiserver.validation.types;

import static iudx.resource.server.apiserver.response.ResponseUrn.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import iudx.resource.server.apiserver.exceptions.DxRuntimeException;
import iudx.resource.server.apiserver.util.HttpStatusCode;

public final class OptionsTypeValidator implements Validator {

  private static final Logger LOGGER = LogManager.getLogger(OptionsTypeValidator.class);

  private final String value;
  private final boolean required;

  public OptionsTypeValidator(final String value, final boolean required) {
    this.value = value;
    this.required = required;
  }

  @Override
  public boolean isValid() {
    LOGGER.debug("value : " + value + "required : " + required);
    if (required && (value == null || value.isBlank())) {
      LOGGER.error("Validation error : null or blank value for required mandatory field");
      throw new DxRuntimeException(failureCode(), INVALID_PARAM_VALUE, failureMessage());
    } else {
      if (value == null) {
        return true;
      }
      if (value.isBlank()) {
        LOGGER.error("Validation error :  blank value passed");
        throw new DxRuntimeException(failureCode(), INVALID_PARAM_VALUE, failureMessage(value));
      }
    }
    if (!value.equals("count")) {
      LOGGER.error("Validation error : count is only allowed value for options parameter");
      throw new DxRuntimeException(failureCode(), INVALID_PARAM_VALUE, failureMessage(value));
    }
    return true;
  }


  @Override
  public int failureCode() {
    return HttpStatusCode.BAD_REQUEST.getValue();
  }


  @Override
  public String failureMessage() {
    return INVALID_PARAM_VALUE.getMessage();
  }
}
