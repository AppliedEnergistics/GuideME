package guideme.siteexport;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultValue {
    String value();
}
