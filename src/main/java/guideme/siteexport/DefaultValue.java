package guideme.siteexport;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@ApiStatus.Experimental
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultValue {
    String value();
}
