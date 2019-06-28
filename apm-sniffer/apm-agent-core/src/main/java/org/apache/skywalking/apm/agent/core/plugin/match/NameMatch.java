package org.apache.skywalking.apm.agent.core.plugin.match;

public class NameMatch implements ClassMatch {

    private String className;

    private NameMatch(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public static NameMatch byName(String className) {
        return new NameMatch(className);
    }

}
