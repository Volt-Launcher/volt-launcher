package app.voltlauncher.voltlauncher.rest.routes;

import io.javalin.http.Context;

public interface IRoute {
    void execute(Context ctx);
}
