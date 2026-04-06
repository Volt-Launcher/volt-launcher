package de.eztxm.thelauncherproject.rest;

import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.HandlerType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RouteManager {
    Javalin javalin;
    private List<IRoute> routes;

    public RouteManager(List<IRoute> routes) {
        this.routes = routes;
    }

    public RouteManager() {
        this(new ArrayList<>());
    }

    public void register(IRoute route) {
        this.routes.add(route);
    }

    public Javalin createJavalin(Consumer<JavalinConfig> extConfig) {
        return this.javalin = Javalin.create( config -> {
            extConfig.accept(config);

            for (IRoute route : routes) {
                Class<?> clazz = route.getClass();
                if(clazz.isAnnotationPresent(Route.class)) {
                    Route annotation = clazz.getAnnotation(Route.class);

                    config.routes.addHttpHandler(
                            HandlerType.findOrCreate(annotation.method().name()),
                            annotation.path(),
                            route::execute
                    );

                    System.out.println("Registered: " + annotation.method() + " " + annotation.path());
                }
            }
        });
    }
}
