package com.bay1ts.bay.route;


import com.bay1ts.bay.core.Action;
import com.bay1ts.bay.core.HttpMethod;
import com.bay1ts.bay.utils.SparkUtils;

import java.util.List;

/**
 * Created by chenu on 2016/10/12.
 */
public class RouteEntry {
    HttpMethod httpMethod;
    String path;
    String acceptedType;
    Action action;

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getAcceptedType() {
        return acceptedType;
    }

    public void setAcceptedType(String acceptedType) {
        this.acceptedType = acceptedType;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public RouteEntry(HttpMethod httpMethod, String path, String acceptedType, Action action) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.acceptedType = acceptedType;
        this.action = action;
    }

    RouteEntry(RouteEntry entry) {
        this.httpMethod = entry.httpMethod;
        this.path = entry.path;
        this.acceptedType = entry.acceptedType;
        this.action = entry.action;
    }

    boolean matches(HttpMethod httpMethod, String path) {
        //不清楚 怎么用的这个
        if ((httpMethod == HttpMethod.before || httpMethod == HttpMethod.after)
                && (this.httpMethod == httpMethod)
                && this.path.equals(SparkUtils.ALL_PATHS)) {
            // Is filter and matches all
            return true;
        }
        boolean match = false;
        if (this.httpMethod == httpMethod) {
            match = matchPath(path);
        }
        return match;
    }

    private boolean matchPath(String path) { // NOSONAR
        if (!this.path.endsWith("*") && ((path.endsWith("/") && !this.path.endsWith("/")) // NOSONAR
                || (this.path.endsWith("/") && !path.endsWith("/")))) {
            // One and not both ends with slash
            return false;
        }
        if (this.path.equals(path)) {
            // Paths are the same
            return true;
        }

        // check params
        List<String> thisPathList = SparkUtils.convertRouteToList(this.path);
        List<String> pathList = SparkUtils.convertRouteToList(path);

        int thisPathSize = thisPathList.size();
        int pathSize = pathList.size();

        if (thisPathSize == pathSize) {
            for (int i = 0; i < thisPathSize; i++) {
                String thisPathPart = thisPathList.get(i);
                String pathPart = pathList.get(i);

                if ((i == thisPathSize - 1) && (thisPathPart.equals("*") && this.path.endsWith("*"))) {
                    // wildcard match
                    return true;
                }

                if ((!thisPathPart.startsWith(":"))
                        && !thisPathPart.equals(pathPart)
                        && !thisPathPart.equals("*")) {
                    return false;
                }
            }
            // All parts matched
            return true;
        } else {
            // Number of "path parts" not the same
            // check wild card:
            if (this.path.endsWith("*")) {
                if (pathSize == (thisPathSize - 1) && (path.endsWith("/"))) {
                    // Hack for making wildcards work with trailing slash
                    pathList.add("");
                    pathList.add("");
                    pathSize += 2;
                }

                if (thisPathSize < pathSize) {
                    for (int i = 0; i < thisPathSize; i++) {
                        String thisPathPart = thisPathList.get(i);
                        String pathPart = pathList.get(i);
                        if (thisPathPart.equals("*") && (i == thisPathSize - 1) && this.path.endsWith("*")) {
                            // wildcard match
                            return true;
                        }
                        if (!thisPathPart.startsWith(":")
                                && !thisPathPart.equals(pathPart)
                                && !thisPathPart.equals("*")) {
                            return false;
                        }
                    }
                    // All parts matched
                    return true;
                }
                // End check wild card
            }
            return false;
        }
    }

    public String toString() {
        return httpMethod.name() + ", " + path + ", " + action;
    }
}
