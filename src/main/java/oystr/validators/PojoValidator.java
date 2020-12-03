package oystr.validators;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.stream.Collectors;

public class PojoValidator {
    private static final PojoValidator instance = new PojoValidator();

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    private PojoValidator() { }

    public static PojoValidator getInstance() {
        return instance;
    }

    public <T> List<ObjectNode> validate(T obj) {
        return validatorFactory
            .getValidator()
            .validate(obj)
            .stream()
            .map(o -> JsonNodeFactory.instance.objectNode().put(o.getPropertyPath().toString(), o.getMessage()))
            .collect(Collectors.toList());
    }
}
