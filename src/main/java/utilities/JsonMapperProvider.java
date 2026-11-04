package utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JsonMapperProvider implements ContextResolver<ObjectMapper> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }
}
