package utilities;

import filter.AuthenticationFilter;
import org.glassfish.jersey.jackson.JacksonFeature;
import ressources.AuthenticationEndPoint;
import ressources.EtudiantRessources;
import ressources.OptionRessources;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("rest")
public class RestActivator extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        return new HashSet<>(Arrays.asList(OptionRessources.class, EtudiantRessources.class,
                AuthenticationEndPoint.class, AuthenticationFilter.class,
                JsonParseExceptionMapper.class, JsonMapperProvider.class, JacksonFeature.class));
    }
}
