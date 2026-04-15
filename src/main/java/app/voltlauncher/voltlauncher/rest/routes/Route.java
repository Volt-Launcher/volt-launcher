package app.voltlauncher.voltlauncher.rest.routes;

import app.voltlauncher.voltlauncher.rest.MethodType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Route {
    String path();
    MethodType method() default MethodType.GET;
}

