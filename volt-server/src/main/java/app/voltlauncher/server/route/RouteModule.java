package app.voltlauncher.server.route;

/** A group of related endpoints, registered together. */
public interface RouteModule {

    void register(RouteRegistry routes);
}
